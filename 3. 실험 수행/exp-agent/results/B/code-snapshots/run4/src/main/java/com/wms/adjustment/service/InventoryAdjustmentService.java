package com.wms.adjustment.service;

import com.wms.adjustment.dto.ApprovalRequest;
import com.wms.adjustment.dto.CreateAdjustmentRequest;
import com.wms.adjustment.entity.ApprovalStatus;
import com.wms.adjustment.entity.InventoryAdjustment;
import com.wms.adjustment.repository.InventoryAdjustmentRepository;
import com.wms.common.entity.AuditLog;
import com.wms.common.entity.AutoReorderLog;
import com.wms.common.entity.SafetyStockRule;
import com.wms.common.entity.TriggerType;
import com.wms.common.repository.AuditLogRepository;
import com.wms.common.repository.AutoReorderLogRepository;
import com.wms.common.repository.SafetyStockRuleRepository;
import com.wms.inbound.entity.Inventory;
import com.wms.inbound.entity.Location;
import com.wms.inbound.entity.Product;
import com.wms.inbound.entity.ProductCategory;
import com.wms.inbound.repository.InventoryRepository;
import com.wms.inbound.repository.LocationRepository;
import com.wms.inbound.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public InventoryAdjustment createAdjustment(CreateAdjustmentRequest request) {
        // 1. 기본 검증
        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Location location = locationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new IllegalArgumentException("Location not found"));

        // 2. reason 필수 체크
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is required");
        }

        // 3. 시스템 재고 조회
        Integer systemQty = inventoryRepository.findByProductAndLocationAndLotNumber(
            product.getProductId(),
            location.getLocationId(),
            null
        ).map(Inventory::getQuantity).orElse(0);

        Integer actualQty = request.getActualQty();
        Integer difference = actualQty - systemQty;

        // 모든 조정은 즉시 반영 (승인 절차 제거)
        boolean requiresApproval = false;
        ApprovalStatus approvalStatus = ApprovalStatus.AUTO_APPROVED;
        String reason = request.getReason();

        // 8. 조정 레코드 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
            .adjustmentId(UUID.randomUUID())
            .product(product)
            .location(location)
            .systemQty(systemQty)
            .actualQty(actualQty)
            .difference(difference)
            .reason(reason)
            .requiresApproval(requiresApproval)
            .approvalStatus(approvalStatus)
            .adjustedBy(request.getAdjustedBy())
            .createdAt(ZonedDateTime.now())
            .build();

        adjustment = adjustmentRepository.save(adjustment);

        // 9. 자동 승인인 경우 즉시 재고 반영
        if (approvalStatus == ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(adjustment);
        }

        return adjustment;
    }

    @Transactional
    public InventoryAdjustment approveAdjustment(UUID adjustmentId, ApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new IllegalArgumentException("Adjustment not found"));

        if (adjustment.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new IllegalArgumentException("Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus(ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(ZonedDateTime.now());

        adjustment = adjustmentRepository.save(adjustment);

        // 재고 반영
        applyAdjustment(adjustment);

        return adjustment;
    }

    @Transactional
    public InventoryAdjustment rejectAdjustment(UUID adjustmentId, ApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new IllegalArgumentException("Adjustment not found"));

        if (adjustment.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new IllegalArgumentException("Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus(ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(ZonedDateTime.now());

        return adjustmentRepository.save(adjustment);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustment getAdjustment(UUID adjustmentId) {
        return adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new IllegalArgumentException("Adjustment not found"));
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustment> getAllAdjustments() {
        return adjustmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustment> getOldAdjustments(int yearsOld) {
        ZonedDateTime cutoffDate = ZonedDateTime.now().minusYears(yearsOld);
        return adjustmentRepository.findOldAdjustments(cutoffDate);
    }

    @Transactional
    public Map<String, Object> deleteOldAdjustments(int yearsOld) {
        ZonedDateTime cutoffDate = ZonedDateTime.now().minusYears(yearsOld);
        List<InventoryAdjustment> oldAdjustments = adjustmentRepository.findOldAdjustments(cutoffDate);

        int deletedCount = oldAdjustments.size();
        adjustmentRepository.deleteAll(oldAdjustments);

        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("cutoffDate", cutoffDate);

        return result;
    }

    private void applyAdjustment(InventoryAdjustment adjustment) {
        Product product = adjustment.getProduct();
        Location location = adjustment.getLocation();
        Integer newQty = adjustment.getActualQty();

        // 1. 재고 반영
        Optional<Inventory> inventoryOpt = inventoryRepository.findByProductAndLocationAndLotNumber(
            product.getProductId(),
            location.getLocationId(),
            null
        );

        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            int oldQty = inventory.getQuantity();
            inventory.setQuantity(newQty);
            inventoryRepository.save(inventory);

            // location current_qty 갱신
            location.setCurrentQty(location.getCurrentQty() - oldQty + newQty);
        } else if (newQty > 0) {
            // 신규 재고 생성
            Inventory inventory = Inventory.builder()
                .inventoryId(UUID.randomUUID())
                .product(product)
                .location(location)
                .quantity(newQty)
                .receivedAt(ZonedDateTime.now())
                .build();
            inventoryRepository.save(inventory);

            location.setCurrentQty(location.getCurrentQty() + newQty);
        }
        locationRepository.save(location);

        // 2. HIGH_VALUE 감사 로그 기록
        if (product.getCategory() == ProductCategory.HIGH_VALUE && adjustment.getDifference() != 0) {
            Map<String, Object> details = new HashMap<>();
            details.put("adjustmentId", adjustment.getAdjustmentId().toString());
            details.put("productId", product.getProductId().toString());
            details.put("locationId", location.getLocationId().toString());
            details.put("systemQty", adjustment.getSystemQty());
            details.put("actualQty", adjustment.getActualQty());
            details.put("difference", adjustment.getDifference());
            details.put("approvedBy", adjustment.getApprovedBy());

            AuditLog auditLog = AuditLog.builder()
                .logId(UUID.randomUUID())
                .eventType("HIGH_VALUE_ADJUSTMENT")
                .entityType("INVENTORY_ADJUSTMENT")
                .entityId(adjustment.getAdjustmentId())
                .details(details)
                .performedBy(adjustment.getApprovedBy() != null ? adjustment.getApprovedBy() : adjustment.getAdjustedBy())
                .createdAt(ZonedDateTime.now())
                .build();

            auditLogRepository.save(auditLog);
        }

        // 3. 안전재고 체크
        checkSafetyStock(product);
    }

    private void checkSafetyStock(Product product) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductProductId(product.getProductId());
        if (ruleOpt.isEmpty()) {
            return;
        }

        SafetyStockRule rule = ruleOpt.get();

        // 전체 가용 재고 계산 (is_expired=false만)
        Integer totalStock = inventoryRepository.findByProductProductId(product.getProductId())
            .stream()
            .filter(inv -> !inv.getIsExpired())
            .mapToInt(Inventory::getQuantity)
            .sum();

        if (totalStock <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                .reorderLogId(UUID.randomUUID())
                .product(product)
                .triggerType(TriggerType.SAFETY_STOCK_TRIGGER)
                .currentStock(totalStock)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .triggeredBy("SYSTEM")
                .createdAt(ZonedDateTime.now())
                .build();

            autoReorderLogRepository.save(log);
        }
    }

    private double getCategoryThreshold(ProductCategory category) {
        return switch (category) {
            case GENERAL -> 5.0;
            case FRESH -> 3.0;
            case HAZMAT -> 1.0;
            case HIGH_VALUE -> 2.0;
        };
    }
}
