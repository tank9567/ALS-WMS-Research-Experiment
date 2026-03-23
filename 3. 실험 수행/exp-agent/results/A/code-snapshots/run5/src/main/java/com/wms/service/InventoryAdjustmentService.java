package com.wms.service;

import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final CycleCountRepository cycleCountRepository;
    private final AuditLogRepository auditLogRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        // 1. 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found"));

        CycleCount cycleCount = null;
        if (request.getCycleCountId() != null) {
            cycleCount = cycleCountRepository.findById(request.getCycleCountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cycle count not found"));
        }

        // 2. 시스템 재고 수량 조회
        int systemQty = inventoryRepository.findByProductAndLocation(product.getId(), location.getId())
                .stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 3. 차이 계산
        int difference = request.getActualQty() - systemQty;

        // 4. 사유 체크
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException("Reason is required");
        }

        String reason = request.getReason();

        // 5. 연속 조정 감시 (최근 7일 내 2회 이상)
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        long recentAdjustments = inventoryAdjustmentRepository.countRecentAdjustments(
                product.getId(), location.getId(), sevenDaysAgo);

        if (recentAdjustments >= 2) {
            reason = "[연속조정감시] " + reason;
        }

        // 6. 승인 상태 결정
        InventoryAdjustment.ApprovalStatus approvalStatus;

        // 차이율 계산
        double diffPercent = systemQty > 0 ? (Math.abs(difference) * 100.0 / systemQty) : 100.0;
        double threshold = getAutoApprovalThreshold(product.getCategory());

        if (diffPercent <= threshold) {
            approvalStatus = InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
        } else {
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }

        // 7. 조정 레코드 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .product(product)
                .location(location)
                .systemQty(systemQty)
                .actualQty(request.getActualQty())
                .difference(difference)
                .reason(reason)
                .approvalStatus(approvalStatus)
                .cycleCount(cycleCount)
                .build();

        // 자동 승인 시 즉시 처리
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            adjustment.setApprovedBy("SYSTEM");
            adjustment.setApprovedAt(OffsetDateTime.now());
            applyAdjustment(adjustment);
        }

        adjustment = inventoryAdjustmentRepository.save(adjustment);

        // 8. HIGH_VALUE 감사 로그 기록
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            AuditLog auditLog = AuditLog.builder()
                    .eventType("HIGH_VALUE_ADJUSTMENT")
                    .entityType("InventoryAdjustment")
                    .entityId(adjustment.getId())
                    .description("HIGH_VALUE product adjustment: " + product.getSku() +
                            ", Location: " + location.getCode() +
                            ", Difference: " + difference +
                            ", Reason: " + reason)
                    .performedBy(approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED ? "SYSTEM" : "PENDING")
                    .build();
            auditLogRepository.save(auditLog);
        }

        return InventoryAdjustmentResponse.from(adjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());

        // 재고 반영
        applyAdjustment(adjustment);

        adjustment = inventoryAdjustmentRepository.save(adjustment);

        // HIGH_VALUE 감사 로그 업데이트
        if (adjustment.getProduct().getCategory() == Product.ProductCategory.HIGH_VALUE) {
            AuditLog auditLog = AuditLog.builder()
                    .eventType("HIGH_VALUE_ADJUSTMENT_APPROVED")
                    .entityType("InventoryAdjustment")
                    .entityId(adjustment.getId())
                    .description("HIGH_VALUE adjustment approved by " + approvedBy)
                    .performedBy(approvedBy)
                    .build();
            auditLogRepository.save(auditLog);
        }

        return InventoryAdjustmentResponse.from(adjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, String rejectedBy) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(rejectedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());

        adjustment = inventoryAdjustmentRepository.save(adjustment);

        return InventoryAdjustmentResponse.from(adjustment);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory adjustment not found"));

        return InventoryAdjustmentResponse.from(adjustment);
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getAdjustments(UUID productId, UUID locationId,
                                                           InventoryAdjustment.ApprovalStatus approvalStatus) {
        List<InventoryAdjustment> adjustments = inventoryAdjustmentRepository.findByFilters(
                productId, locationId, approvalStatus);

        return adjustments.stream()
                .map(InventoryAdjustmentResponse::from)
                .collect(Collectors.toList());
    }

    private double getAutoApprovalThreshold(Product.ProductCategory category) {
        switch (category) {
            case GENERAL:
                return 5.0;
            case FRESH:
                return 3.0;
            case HAZMAT:
                return 1.0;
            case HIGH_VALUE:
                return 5.0;
            default:
                return 5.0;
        }
    }

    private void applyAdjustment(InventoryAdjustment adjustment) {
        Product product = adjustment.getProduct();
        Location location = adjustment.getLocation();
        int difference = adjustment.getDifference();

        if (difference != 0) {
            // 재고 조정 반영
            List<Inventory> inventories = inventoryRepository.findByProductAndLocation(
                    product.getId(), location.getId());

            if (inventories.isEmpty() && difference > 0) {
                // 새 재고 생성
                Inventory newInventory = Inventory.builder()
                        .product(product)
                        .location(location)
                        .quantity(difference)
                        .receivedAt(OffsetDateTime.now())
                        .build();
                inventoryRepository.save(newInventory);

                // 로케이션 수량 업데이트
                location.setCurrentQuantity(location.getCurrentQuantity() + difference);

            } else if (!inventories.isEmpty()) {
                // 기존 재고 조정
                Inventory inventory = inventories.get(0);
                int newQty = inventory.getQuantity() + difference;

                if (newQty < 0) {
                    throw new BusinessException("Resulting inventory quantity cannot be negative");
                }

                inventory.setQuantity(newQty);
                inventoryRepository.save(inventory);

                // 로케이션 수량 업데이트
                location.setCurrentQuantity(location.getCurrentQuantity() + difference);
            }

            // 안전재고 체크
            checkSafetyStock(product);
        }
    }

    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId())
                .orElse(null);

        if (rule != null) {
            // 전체 가용 재고 계산
            int totalAvailableQty = inventoryRepository.findByProductId(product.getId())
                    .stream()
                    .filter(inv -> inv.getExpiryDate() == null || !inv.getExpiryDate().isBefore(java.time.LocalDate.now()))
                    .mapToInt(Inventory::getQuantity)
                    .sum();

            if (totalAvailableQty <= rule.getMinQty()) {
                // 자동 재발주 요청 생성
                AutoReorderLog reorderLog = AutoReorderLog.builder()
                        .product(product)
                        .triggerReason("SAFETY_STOCK_TRIGGER")
                        .currentQty(totalAvailableQty)
                        .minQty(rule.getMinQty())
                        .reorderQty(rule.getReorderQty())
                        .build();
                autoReorderLogRepository.save(reorderLog);
            }
        }
    }

    @Transactional
    public int deleteOldAdjustmentHistory(int years) {
        if (years <= 0) {
            throw new BusinessException("Years must be positive");
        }

        OffsetDateTime cutoffDate = OffsetDateTime.now().minusYears(years);
        long count = inventoryAdjustmentRepository.countByCreatedAtBefore(cutoffDate);

        if (count > 0) {
            inventoryAdjustmentRepository.deleteByCreatedAtBefore(cutoffDate);
        }

        return (int) count;
    }
}
