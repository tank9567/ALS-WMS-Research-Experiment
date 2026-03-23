package com.wms.service;

import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    public InventoryAdjustmentService(
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            AuditLogRepository auditLogRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository) {
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.auditLogRepository = auditLogRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
    }

    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        // reason 필수 체크
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is required for inventory adjustment");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("Location not found"));

        // 시스템 재고 조회
        int systemQty = inventoryRepository.findByProductAndLocationAndLotNumber(
                        product.getProductId(), location.getLocationId(), null)
                .map(Inventory::getQuantity)
                .orElse(0);

        int actualQty = request.getActualQty();
        int difference = actualQty - systemQty;

        // 음수 재고 방지
        if (actualQty < 0) {
            throw new IllegalArgumentException("Actual quantity cannot be negative");
        }

        // 조정 엔티티 생성
        InventoryAdjustment adjustment = new InventoryAdjustment();
        adjustment.setProduct(product);
        adjustment.setLocation(location);
        adjustment.setSystemQty(systemQty);
        adjustment.setActualQty(actualQty);
        adjustment.setDifference(difference);
        adjustment.setAdjustedBy(request.getAdjustedBy());

        // 연속 조정 감시 (최근 7일)
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        List<InventoryAdjustment> recentAdjustments = inventoryAdjustmentRepository.findRecentAdjustments(
                location.getLocationId(), product.getProductId(), sevenDaysAgo);

        boolean consecutiveAdjustment = recentAdjustments.size() >= 2;
        String reason = request.getReason();
        if (consecutiveAdjustment) {
            reason = "[연속조정감시] " + reason;
        }
        adjustment.setReason(reason);

        // 모든 조정을 즉시 반영 (승인 절차 없음)
        adjustment.setRequiresApproval(false);
        adjustment.setApprovalStatus("auto_approved");

        // 재고 즉시 반영
        applyAdjustment(adjustment);

        inventoryAdjustmentRepository.save(adjustment);

        InventoryAdjustmentResponse response = toResponse(adjustment);

        // HIGH_VALUE 상품 차이 발생 시 재실사 권고
        if ("HIGH_VALUE".equals(product.getCategory()) && difference != 0) {
            response.setReauditRecommendation("Full location re-audit recommended for HIGH_VALUE discrepancy");
        }

        return response;
    }

    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("Adjustment not found"));

        if (!"pending".equals(adjustment.getApprovalStatus())) {
            throw new IllegalStateException("Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus("approved");
        adjustment.setApprovedAt(OffsetDateTime.now());

        // 재고 반영
        applyAdjustment(adjustment);

        inventoryAdjustmentRepository.save(adjustment);

        return toResponse(adjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("Adjustment not found"));

        if (!"pending".equals(adjustment.getApprovalStatus())) {
            throw new IllegalStateException("Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus("rejected");
        adjustment.setApprovedAt(OffsetDateTime.now());

        inventoryAdjustmentRepository.save(adjustment);

        return toResponse(adjustment);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("Adjustment not found"));

        return toResponse(adjustment);
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getAllAdjustments() {
        return inventoryAdjustmentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getOldAdjustments(int yearsOld) {
        OffsetDateTime threshold = OffsetDateTime.now().minusYears(yearsOld);
        return inventoryAdjustmentRepository.findOldAdjustments(threshold).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public int deleteOldAdjustments(int yearsOld) {
        OffsetDateTime threshold = OffsetDateTime.now().minusYears(yearsOld);
        List<InventoryAdjustment> toDelete = inventoryAdjustmentRepository.findOldAdjustments(threshold);
        int count = toDelete.size();
        inventoryAdjustmentRepository.deleteOldAdjustments(threshold);
        return count;
    }

    // Helper methods
    private void applyAdjustment(InventoryAdjustment adjustment) {
        Product product = adjustment.getProduct();
        Location location = adjustment.getLocation();
        int actualQty = adjustment.getActualQty();

        // 재고 업데이트
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                        product.getProductId(), location.getLocationId(), null)
                .orElse(null);

        if (inventory != null) {
            inventory.setQuantity(actualQty);
            inventoryRepository.save(inventory);
        } else if (actualQty > 0) {
            // 새 재고 생성
            inventory = new Inventory();
            inventory.setProduct(product);
            inventory.setLocation(location);
            inventory.setQuantity(actualQty);
            inventory.setReceivedAt(OffsetDateTime.now());
            inventoryRepository.save(inventory);
        }

        // 로케이션 수량 업데이트
        location.setCurrentQty(location.getCurrentQty() + adjustment.getDifference());
        locationRepository.save(location);

        // HIGH_VALUE 카테고리는 audit_logs 추가 기록
        if ("HIGH_VALUE".equals(product.getCategory())) {
            AuditLog auditLog = new AuditLog();
            auditLog.setEventType("HIGH_VALUE_ADJUSTMENT");
            auditLog.setEntityType("InventoryAdjustment");
            auditLog.setEntityId(adjustment.getAdjustmentId());
            auditLog.setPerformedBy(adjustment.getAdjustedBy());
            auditLog.setDetails(String.format("{\"product_id\":\"%s\",\"location_id\":\"%s\",\"system_qty\":%d,\"actual_qty\":%d,\"difference\":%d}",
                    product.getProductId(), location.getLocationId(),
                    adjustment.getSystemQty(), adjustment.getActualQty(), adjustment.getDifference()));
            auditLogRepository.save(auditLog);
        }

        // 안전재고 체크 및 자동 재발주
        checkSafetyStockAndReorder(product);
    }

    private void checkSafetyStockAndReorder(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductProductId(product.getProductId())
                .orElse(null);

        if (rule == null) {
            return;
        }

        // 전체 가용 재고 계산
        int totalAvailableQty = inventoryRepository.findByProductProductId(product.getProductId()).stream()
                .filter(inv -> !Boolean.TRUE.equals(inv.getIsExpired()))
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalAvailableQty <= rule.getMinQty()) {
            AutoReorderLog reorderLog = new AutoReorderLog();
            reorderLog.setProduct(product);
            reorderLog.setTriggerType("SAFETY_STOCK_TRIGGER");
            reorderLog.setCurrentStock(totalAvailableQty);
            reorderLog.setMinQty(rule.getMinQty());
            reorderLog.setReorderQty(rule.getReorderQty());
            reorderLog.setTriggeredBy("SYSTEM");
            autoReorderLogRepository.save(reorderLog);
        }
    }

    private InventoryAdjustmentResponse toResponse(InventoryAdjustment adjustment) {
        InventoryAdjustmentResponse response = new InventoryAdjustmentResponse();
        response.setAdjustmentId(adjustment.getAdjustmentId());
        response.setProductId(adjustment.getProduct().getProductId());
        response.setProductSku(adjustment.getProduct().getSku());
        response.setProductName(adjustment.getProduct().getName());
        response.setLocationId(adjustment.getLocation().getLocationId());
        response.setLocationCode(adjustment.getLocation().getCode());
        response.setSystemQty(adjustment.getSystemQty());
        response.setActualQty(adjustment.getActualQty());
        response.setDifference(adjustment.getDifference());
        response.setReason(adjustment.getReason());
        response.setRequiresApproval(adjustment.getRequiresApproval());
        response.setApprovalStatus(adjustment.getApprovalStatus());
        response.setApprovedBy(adjustment.getApprovedBy());
        response.setAdjustedBy(adjustment.getAdjustedBy());
        response.setCreatedAt(adjustment.getCreatedAt());
        response.setApprovedAt(adjustment.getApprovedAt());
        return response;
    }
}
