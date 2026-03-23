package com.wms.service;

import com.wms.entity.*;
import com.wms.entity.InventoryAdjustment.ApprovalStatus;
import com.wms.entity.Product.ProductCategory;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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

    /**
     * 재고 조정 생성
     * - 시스템 재고와 실제 재고 비교
     * - 카테고리별 자동 승인 임계값: GENERAL ±5%, FRESH ±3%, HAZMAT ±1%, HIGH_VALUE ±5%
     * - 임계값 이하: 자동 승인 및 즉시 반영
     * - 임계값 초과: 관리자 승인 대기
     */
    @Transactional
    public InventoryAdjustment createAdjustment(
        UUID productId,
        UUID locationId,
        Integer actualQty,
        String reason,
        String adjustedBy
    ) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("REASON_REQUIRED", "Adjustment reason is required");
        }

        if (actualQty < 0) {
            throw new BusinessException("INVALID_ACTUAL_QTY", "Actual quantity cannot be negative");
        }

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        // 시스템 재고 조회 (해당 location + product의 총 재고)
        Integer systemQty = inventoryRepository.findByProductProductIdAndLocationLocationId(productId, locationId)
            .stream()
            .mapToInt(Inventory::getQuantity)
            .sum();

        Integer difference = actualQty - systemQty;

        // 자동 승인 여부 결정
        boolean requiresApproval = shouldRequireApproval(product, systemQty, actualQty, difference);
        ApprovalStatus approvalStatus = requiresApproval ? ApprovalStatus.PENDING : ApprovalStatus.AUTO_APPROVED;

        InventoryAdjustment adjustment = InventoryAdjustment.builder()
            .product(product)
            .location(location)
            .systemQty(systemQty)
            .actualQty(actualQty)
            .difference(difference)
            .reason(reason)
            .requiresApproval(requiresApproval)
            .approvalStatus(approvalStatus)
            .adjustedBy(adjustedBy)
            .build();

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);

        // 자동 승인된 경우 즉시 재고 반영
        if (!requiresApproval) {
            applyAdjustment(saved);
        }

        return saved;
    }

    /**
     * 승인 필요 여부 판단
     * - 카테고리별 자동 승인 임계값 적용
     * - system_qty가 0인 경우(새로 발견된 재고)는 무조건 승인 필요
     */
    private boolean shouldRequireApproval(Product product, Integer systemQty, Integer actualQty, Integer difference) {
        // 차이가 없으면 자동 승인
        if (difference == 0) {
            return false;
        }

        // 시스템에 없던 재고가 발견된 경우 무조건 승인 필요
        if (systemQty == 0) {
            return true;
        }

        // 차이 비율 계산 (%)
        double diffRatio = Math.abs(difference) * 100.0 / systemQty;

        // 카테고리별 자동 승인 임계값
        double threshold;
        switch (product.getCategory()) {
            case GENERAL:
                threshold = 5.0;
                break;
            case FRESH:
                threshold = 3.0;
                break;
            case HAZMAT:
                threshold = 1.0;
                break;
            case HIGH_VALUE:
                threshold = 5.0;  // 고가품 5% 이내 자동 승인
                break;
            default:
                threshold = 5.0;
        }

        return diffRatio > threshold;
    }

    /**
     * 조정 승인
     */
    @Transactional
    public InventoryAdjustment approveAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found"));

        if (adjustment.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("ADJUSTMENT_NOT_PENDING", "Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus(ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);

        // 재고 반영
        applyAdjustment(saved);

        return saved;
    }

    /**
     * 조정 거부
     */
    @Transactional
    public InventoryAdjustment rejectAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found"));

        if (adjustment.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("ADJUSTMENT_NOT_PENDING", "Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus(ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());

        return adjustmentRepository.save(adjustment);
    }

    /**
     * 재고 반영 (승인된 조정 또는 자동승인된 조정에 대해)
     * - inventory.quantity 갱신
     * - locations.current_qty 갱신
     * - HIGH_VALUE 감사 로그 기록
     * - 안전재고 체크 및 자동 재발주
     */
    private void applyAdjustment(InventoryAdjustment adjustment) {
        Product product = adjustment.getProduct();
        Location location = adjustment.getLocation();
        Integer difference = adjustment.getDifference();

        // inventory 레코드 갱신 (해당 location + product의 재고 레코드)
        List<Inventory> inventories = inventoryRepository
            .findByProductProductIdAndLocationLocationId(
                product.getProductId(),
                location.getLocationId()
            );

        if (inventories.isEmpty() && adjustment.getActualQty() > 0) {
            // 시스템에 없었는데 실물이 발견된 경우 → 새 레코드 생성
            Inventory newInventory = Inventory.builder()
                .product(product)
                .location(location)
                .quantity(adjustment.getActualQty())
                .receivedAt(OffsetDateTime.now())
                .isExpired(false)
                .build();
            inventoryRepository.save(newInventory);
        } else if (!inventories.isEmpty()) {
            // 기존 레코드가 있으면 첫 번째 레코드를 갱신 (단순화)
            Inventory inventory = inventories.get(0);
            int newQty = inventory.getQuantity() + difference;

            if (newQty < 0) {
                throw new BusinessException("NEGATIVE_INVENTORY",
                    "Adjustment would result in negative inventory");
            }

            inventory.setQuantity(newQty);
            inventoryRepository.save(inventory);
        }

        // locations.current_qty 갱신
        location.setCurrentQty(location.getCurrentQty() + difference);
        locationRepository.save(location);

        // HIGH_VALUE 감사 로그 기록
        if (product.getCategory() == ProductCategory.HIGH_VALUE && difference != 0) {
            AuditLog auditLog = AuditLog.builder()
                .eventType("HIGH_VALUE_ADJUSTMENT")
                .entityType("InventoryAdjustment")
                .entityId(adjustment.getAdjustmentId())
                .details(String.format(
                    "{\"product_id\":\"%s\",\"location_id\":\"%s\",\"system_qty\":%d,\"actual_qty\":%d,\"difference\":%d}",
                    product.getProductId(),
                    location.getLocationId(),
                    adjustment.getSystemQty(),
                    adjustment.getActualQty(),
                    difference
                ))
                .performedBy(adjustment.getAdjustedBy())
                .build();
            auditLogRepository.save(auditLog);
        }

        // 안전재고 체크 및 자동 재발주
        checkAndTriggerReorder(product);
    }

    /**
     * 안전재고 체크 및 자동 재발주 트리거
     */
    private void checkAndTriggerReorder(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository
            .findByProductProductId(product.getProductId())
            .orElse(null);

        if (rule == null) {
            return;
        }

        // 전체 가용 재고 확인 (is_expired=false인 것만)
        Integer totalAvailable = inventoryRepository.findByProductProductId(product.getProductId())
            .stream()
            .filter(inv -> !inv.getIsExpired())
            .mapToInt(Inventory::getQuantity)
            .sum();

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                .currentStock(totalAvailable)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .triggeredBy("SYSTEM")
                .build();
            autoReorderLogRepository.save(log);
        }
    }


    @Transactional(readOnly = true)
    public InventoryAdjustment getAdjustment(UUID adjustmentId) {
        return adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found"));
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustment> getAllAdjustments() {
        return adjustmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustment> getPendingAdjustments() {
        return adjustmentRepository.findByApprovalStatus(ApprovalStatus.PENDING);
    }
}
