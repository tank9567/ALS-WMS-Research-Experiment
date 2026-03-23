package com.wms.service;

import com.wms.entity.*;
import com.wms.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final CycleCountRepository cycleCountRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    public InventoryAdjustmentService(
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            CycleCountRepository cycleCountRepository,
            InventoryRepository inventoryRepository,
            LocationRepository locationRepository,
            ProductRepository productRepository,
            AuditLogRepository auditLogRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository) {
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
        this.cycleCountRepository = cycleCountRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.productRepository = productRepository;
        this.auditLogRepository = auditLogRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
    }

    @Transactional
    public CycleCount startCycleCount(UUID locationId, UUID productId, String startedBy) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Location not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        // 1. 동일 로케이션+상품에 대해 이미 진행 중인 실사가 있는지 확인
        var existingCount = cycleCountRepository.findInProgressByLocationAndProduct(locationId, productId);
        if (existingCount.isPresent()) {
            throw new IllegalArgumentException("Cycle count already in progress for this location and product");
        }

        // 2. 로케이션 동결
        location.setIsFrozen(true);
        location.setUpdatedAt(OffsetDateTime.now());
        locationRepository.save(location);

        // 3. 실사 시작
        CycleCount cycleCount = new CycleCount();
        cycleCount.setLocation(location);
        cycleCount.setProduct(product);
        cycleCount.setStatus(CycleCount.CycleCountStatus.in_progress);
        cycleCount.setStartedBy(startedBy);
        cycleCount.setStartedAt(OffsetDateTime.now());

        return cycleCountRepository.save(cycleCount);
    }

    @Transactional
    public CycleCount completeCycleCount(UUID cycleCountId, Integer actualQty, String reason) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new IllegalArgumentException("Cycle count not found"));

        if (cycleCount.getStatus() != CycleCount.CycleCountStatus.in_progress) {
            throw new IllegalArgumentException("Cycle count is not in progress");
        }

        // 1. 재고 조회 (해당 로케이션+상품)
        Inventory inventory = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getLocation().getId().equals(cycleCount.getLocation().getId()) &&
                        inv.getProduct().getId().equals(cycleCount.getProduct().getId()))
                .findFirst()
                .orElse(null);

        int systemQty = inventory != null ? inventory.getQuantity() : 0;
        int differenceQty = actualQty - systemQty;

        // 2. 차이가 있으면 재고 조정 생성
        if (differenceQty != 0) {
            createInventoryAdjustment(inventory, cycleCount.getLocation(), cycleCount.getProduct(),
                    systemQty, actualQty, differenceQty, reason);
        }

        // 3. 실사 완료
        cycleCount.setStatus(CycleCount.CycleCountStatus.completed);
        cycleCount.setCompletedAt(OffsetDateTime.now());
        cycleCount.setUpdatedAt(OffsetDateTime.now());
        cycleCount = cycleCountRepository.save(cycleCount);

        // 4. 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        location.setUpdatedAt(OffsetDateTime.now());
        locationRepository.save(location);

        return cycleCount;
    }

    @Transactional
    public InventoryAdjustment createInventoryAdjustment(Inventory inventory, Location location, Product product,
                                                          int systemQty, int actualQty, int differenceQty, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is required");
        }

        // 승인 로직: 5% 이내 차이는 자동 승인
        boolean autoApprove = false;
        if (systemQty == 0) {
            // 시스템 수량이 0이면 자동 승인
            autoApprove = true;
        } else {
            double discrepancyPercent = Math.abs((double) differenceQty / systemQty) * 100;
            autoApprove = (discrepancyPercent <= 5.0);
        }

        InventoryAdjustment adjustment = new InventoryAdjustment();
        adjustment.setInventory(inventory);
        adjustment.setLocation(location);
        adjustment.setProduct(product);
        adjustment.setSystemQty(systemQty);
        adjustment.setActualQty(actualQty);
        adjustment.setDifferenceQty(differenceQty);
        adjustment.setReason(reason);
        adjustment.setApprovalStatus(autoApprove ?
            InventoryAdjustment.ApprovalStatus.auto_approved :
            InventoryAdjustment.ApprovalStatus.pending);

        adjustment = inventoryAdjustmentRepository.save(adjustment);

        // 자동 승인된 경우에만 즉시 반영
        if (autoApprove) {
            applyAdjustment(adjustment);
        }

        return adjustment;
    }

    @Transactional
    public InventoryAdjustment approveAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("Adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.pending) {
            throw new IllegalArgumentException("Adjustment is not pending approval");
        }

        // 1. 승인 상태로 변경
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.approved);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustment.setUpdatedAt(OffsetDateTime.now());
        adjustment = inventoryAdjustmentRepository.save(adjustment);

        // 2. 재고 반영
        applyAdjustment(adjustment);

        // 3. HIGH_VALUE인 경우 감사 로그 기록
        if (adjustment.getProduct().getCategory() == Product.ProductCategory.HIGH_VALUE) {
            AuditLog auditLog = new AuditLog();
            auditLog.setEntityType("InventoryAdjustment");
            auditLog.setEntityId(adjustment.getId());
            auditLog.setAction("APPROVED");
            auditLog.setDescription("HIGH_VALUE adjustment approved. System: " +
                    adjustment.getSystemQty() + " -> Actual: " + adjustment.getActualQty());
            auditLog.setUserId(approvedBy);
            auditLogRepository.save(auditLog);
        }

        return adjustment;
    }

    @Transactional
    public InventoryAdjustment rejectAdjustment(UUID adjustmentId, String rejectionReason) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("Adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.pending) {
            throw new IllegalArgumentException("Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.rejected);
        adjustment.setRejectionReason(rejectionReason);
        adjustment.setUpdatedAt(OffsetDateTime.now());

        return inventoryAdjustmentRepository.save(adjustment);
    }

    public InventoryAdjustment getAdjustment(UUID adjustmentId) {
        return inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("Adjustment not found"));
    }

    public java.util.List<InventoryAdjustment> getAllAdjustments() {
        return inventoryAdjustmentRepository.findAll();
    }

    public java.util.List<InventoryAdjustment> getAdjustmentsByProduct(UUID productId) {
        return inventoryAdjustmentRepository.findByProductId(productId);
    }

    public java.util.List<InventoryAdjustment> getAdjustmentsByLocation(UUID locationId) {
        return inventoryAdjustmentRepository.findByLocationId(locationId);
    }

    @Transactional
    public int deleteOldAdjustments() {
        // 1년 이상 된 조정 이력 삭제
        OffsetDateTime oneYearAgo = OffsetDateTime.now().minusYears(1);
        var oldAdjustments = inventoryAdjustmentRepository.findOldAdjustments(oneYearAgo);
        int deletedCount = oldAdjustments.size();
        inventoryAdjustmentRepository.deleteAll(oldAdjustments);
        return deletedCount;
    }

    // === Helper Methods ===

    private void applyAdjustment(InventoryAdjustment adjustment) {
        Inventory inventory = adjustment.getInventory();
        Location location = adjustment.getLocation();
        Product product = adjustment.getProduct();

        // 1. 재고가 없었는데 새로 생긴 경우
        if (inventory == null && adjustment.getActualQty() > 0) {
            Inventory newInventory = new Inventory();
            newInventory.setProduct(product);
            newInventory.setLocation(location);
            newInventory.setQuantity(adjustment.getActualQty());
            newInventory.setReceivedAt(OffsetDateTime.now());
            inventoryRepository.save(newInventory);

            location.setCurrentQuantity(location.getCurrentQuantity() + adjustment.getActualQty());
        } else if (inventory != null) {
            // 2. 재고 조정 반영
            int oldQty = inventory.getQuantity();
            int newQty = adjustment.getActualQty();

            inventory.setQuantity(newQty);
            inventory.setUpdatedAt(OffsetDateTime.now());
            inventoryRepository.save(inventory);

            // 로케이션 수량 조정
            location.setCurrentQuantity(location.getCurrentQuantity() - oldQty + newQty);
        }

        location.setUpdatedAt(OffsetDateTime.now());
        locationRepository.save(location);

        // 3. 안전재고 체크
        checkSafetyStock(product);
    }

    private void checkSafetyStock(Product product) {
        var safetyStockRuleOpt = safetyStockRuleRepository.findByProductId(product.getId());
        if (safetyStockRuleOpt.isEmpty()) {
            return;
        }

        SafetyStockRule rule = safetyStockRuleOpt.get();

        // 전체 가용 재고 계산 (expired=false인 것만)
        int totalAvailableStock = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()) && !inv.getExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 미달 시 자동 재발주
        if (totalAvailableStock <= rule.getMinQty()) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProduct(product);
            log.setTriggerReason(AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER);
            log.setReorderQty(rule.getReorderQty());
            log.setCurrentStock(totalAvailableStock);
            autoReorderLogRepository.save(log);
        }
    }
}
