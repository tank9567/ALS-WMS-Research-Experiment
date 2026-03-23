package com.wms.service;

import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
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

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        // 필수 필드 검증
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException("INVALID_REASON", "조정 사유는 필수입니다.");
        }

        // 상품 및 로케이션 조회
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다."));

        Location location = locationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "로케이션을 찾을 수 없습니다."));

        // 시스템 재고 확인
        List<Inventory> inventories = inventoryRepository.findByProductAndLocation(product, location);
        int systemQty = inventories.stream().mapToInt(Inventory::getQuantity).sum();

        int actualQty = request.getActualQty();
        int difference = actualQty - systemQty;

        // 조정 후 수량이 음수가 되는지 확인
        if (actualQty < 0) {
            throw new BusinessException("INVALID_QUANTITY", "조정 후 수량은 음수가 될 수 없습니다.");
        }

        // 모든 조정을 즉시 반영 (승인 절차 제거)
        String reason = request.getReason();

        // 조정 레코드 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
            .product(product)
            .location(location)
            .systemQty(systemQty)
            .actualQty(actualQty)
            .difference(difference)
            .reason(reason)
            .requiresApproval(false)
            .approvalStatus(InventoryAdjustment.ApprovalStatus.AUTO_APPROVED)
            .adjustedBy(request.getAdjustedBy())
            .build();

        adjustment = adjustmentRepository.save(adjustment);

        // 즉시 재고 반영
        applyAdjustment(adjustment, product, location, systemQty, actualQty);

        return buildResponse(adjustment, product, location, null);
    }

    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "조정 내역을 찾을 수 없습니다."));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS", "승인 대기 중인 조정만 승인할 수 있습니다.");
        }

        // 승인 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustment = adjustmentRepository.save(adjustment);

        // 재고 반영
        Product product = adjustment.getProduct();
        Location location = adjustment.getLocation();
        applyAdjustment(adjustment, product, location, adjustment.getSystemQty(), adjustment.getActualQty());

        // HIGH_VALUE 카테고리인 경우 감사 로그 기록
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && adjustment.getDifference() != 0) {
            AuditLog auditLog = AuditLog.builder()
                .eventType("HIGH_VALUE_ADJUSTMENT")
                .entityType("INVENTORY_ADJUSTMENT")
                .entityId(adjustment.getAdjustmentId())
                .details(String.format("{\"systemQty\": %d, \"actualQty\": %d, \"difference\": %d, \"approvedBy\": \"%s\"}",
                    adjustment.getSystemQty(), adjustment.getActualQty(), adjustment.getDifference(), approvedBy))
                .performedBy(approvedBy)
                .build();
            auditLogRepository.save(auditLog);
        }

        return buildResponse(adjustment, product, location, null);
    }

    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, String rejectedBy) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "조정 내역을 찾을 수 없습니다."));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS", "승인 대기 중인 조정만 거부할 수 있습니다.");
        }

        // 거부 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(rejectedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustment = adjustmentRepository.save(adjustment);

        return buildResponse(adjustment, adjustment.getProduct(), adjustment.getLocation(), null);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "조정 내역을 찾을 수 없습니다."));

        return buildResponse(adjustment, adjustment.getProduct(), adjustment.getLocation(), null);
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getAllAdjustments() {
        return adjustmentRepository.findAll().stream()
            .map(adj -> buildResponse(adj, adj.getProduct(), adj.getLocation(), null))
            .collect(Collectors.toList());
    }

    private void applyAdjustment(InventoryAdjustment adjustment, Product product, Location location, int systemQty, int actualQty) {
        int difference = actualQty - systemQty;

        // 재고 조정 반영
        List<Inventory> inventories = inventoryRepository.findByProductAndLocation(product, location);

        if (difference > 0) {
            // 재고 증가
            if (inventories.isEmpty()) {
                // 새 재고 생성
                Inventory newInventory = Inventory.builder()
                    .product(product)
                    .location(location)
                    .quantity(difference)
                    .receivedAt(OffsetDateTime.now())
                    .build();
                inventoryRepository.save(newInventory);
            } else {
                // 기존 재고에 추가
                Inventory inventory = inventories.get(0);
                inventory.setQuantity(inventory.getQuantity() + difference);
                inventoryRepository.save(inventory);
            }
            location.setCurrentQty(location.getCurrentQty() + difference);
        } else if (difference < 0) {
            // 재고 감소
            int remainingToDeduct = Math.abs(difference);
            for (Inventory inventory : inventories) {
                if (remainingToDeduct <= 0) break;

                int deductQty = Math.min(inventory.getQuantity(), remainingToDeduct);
                inventory.setQuantity(inventory.getQuantity() - deductQty);
                inventoryRepository.save(inventory);
                remainingToDeduct -= deductQty;
            }
            location.setCurrentQty(location.getCurrentQty() + difference); // difference는 음수
        }

        locationRepository.save(location);

        // 안전재고 체크
        checkSafetyStock(product);
    }

    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct(product).orElse(null);
        if (rule == null) return;

        // 전체 가용 재고 확인 (is_expired=false인 재고만)
        List<Inventory> allInventories = inventoryRepository.findAll().stream()
            .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()) && !inv.getIsExpired())
            .collect(Collectors.toList());

        int totalStock = allInventories.stream().mapToInt(Inventory::getQuantity).sum();

        if (totalStock <= rule.getMinQty()) {
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                .product(product)
                .triggerType("SAFETY_STOCK_TRIGGER")
                .currentStock(totalStock)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .triggeredBy("SYSTEM")
                .build();
            autoReorderLogRepository.save(reorderLog);
        }
    }

    private double getAutoApprovalThreshold(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 5.0;
            case FRESH -> 3.0;
            case HAZMAT -> 1.0;
            case HIGH_VALUE -> 2.0;
        };
    }

    private InventoryAdjustmentResponse buildResponse(InventoryAdjustment adjustment, Product product, Location location, String warning) {
        return InventoryAdjustmentResponse.builder()
            .adjustmentId(adjustment.getAdjustmentId())
            .productId(product.getProductId())
            .productSku(product.getSku())
            .productName(product.getName())
            .locationId(location.getLocationId())
            .locationCode(location.getCode())
            .systemQty(adjustment.getSystemQty())
            .actualQty(adjustment.getActualQty())
            .difference(adjustment.getDifference())
            .reason(adjustment.getReason())
            .requiresApproval(adjustment.getRequiresApproval())
            .approvalStatus(adjustment.getApprovalStatus().name())
            .approvedBy(adjustment.getApprovedBy())
            .adjustedBy(adjustment.getAdjustedBy())
            .createdAt(adjustment.getCreatedAt())
            .approvedAt(adjustment.getApprovedAt())
            .warning(warning)
            .build();
    }
}
