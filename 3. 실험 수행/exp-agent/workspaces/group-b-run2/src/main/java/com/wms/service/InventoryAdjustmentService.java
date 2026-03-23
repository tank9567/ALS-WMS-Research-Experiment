package com.wms.service;

import com.wms.dto.ApprovalRequest;
import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final CycleCountRepository cycleCountRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final StockAdjustmentHistoryService historyService;

    // 카테고리별 자동승인 임계치 (%)
    private static final Map<Product.ProductCategory, Double> AUTO_APPROVAL_THRESHOLDS = new HashMap<>();

    static {
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.GENERAL, 5.0);
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.FRESH, 3.0);
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.HAZMAT, 1.0);
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.HIGH_VALUE, 2.0);
    }

    @Transactional
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        // 1. 로케이션 조회
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        // 2. 이미 진행 중인 실사가 있는지 확인
        cycleCountRepository.findInProgressByLocationId(location.getLocationId())
                .ifPresent(cc -> {
                    throw new BusinessException("CYCLE_COUNT_IN_PROGRESS",
                            "Cycle count is already in progress for this location");
                });

        // 3. 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 4. 실사 세션 생성
        CycleCount cycleCount = CycleCount.builder()
                .location(location)
                .status(CycleCount.CycleCountStatus.IN_PROGRESS)
                .startedBy(request.getStartedBy())
                .startedAt(Instant.now())
                .build();

        CycleCount savedCycleCount = cycleCountRepository.save(cycleCount);
        log.info("Cycle count started for location {} by {}", location.getCode(), request.getStartedBy());

        return convertToCycleCountResponse(savedCycleCount);
    }

    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId) {
        // 1. 실사 세션 조회
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new BusinessException("CYCLE_COUNT_NOT_FOUND", "Cycle count not found"));

        if (cycleCount.getStatus() != CycleCount.CycleCountStatus.IN_PROGRESS) {
            throw new BusinessException("INVALID_STATUS", "Cycle count is not in progress");
        }

        // 2. 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        // 3. 실사 완료
        cycleCount.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(Instant.now());
        CycleCount savedCycleCount = cycleCountRepository.save(cycleCount);

        log.info("Cycle count completed for location {}", location.getCode());
        return convertToCycleCountResponse(savedCycleCount);
    }

    @Transactional
    public InventoryAdjustmentResponse createInventoryAdjustment(InventoryAdjustmentRequest request) {
        // 1. 필수 필드 검증
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException("REASON_REQUIRED", "Adjustment reason is required");
        }

        // 2. 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        // 3. 시스템 재고 조회
        int systemQty = inventoryRepository.sumQuantityByProductAndLocation(
                product.getProductId(), location.getLocationId());

        int actualQty = request.getActualQty();
        int difference = actualQty - systemQty;

        // 4. 재고가 음수가 되는지 체크
        if (actualQty < 0) {
            throw new BusinessException("INVALID_QUANTITY", "Actual quantity cannot be negative");
        }

        // 5. 재고 조정 생성 (모든 조정은 즉시 반영)
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .product(product)
                .location(location)
                .systemQty(systemQty)
                .actualQty(actualQty)
                .difference(difference)
                .reason(request.getReason())
                .requiresApproval(false)
                .approvalStatus(InventoryAdjustment.ApprovalStatus.AUTO_APPROVED)
                .adjustedBy(request.getAdjustedBy())
                .build();

        InventoryAdjustment savedAdjustment = inventoryAdjustmentRepository.save(adjustment);

        // 6. 재고 즉시 반영
        applyAdjustment(savedAdjustment);

        // 7. 이력 기록
        historyService.recordHistory(savedAdjustment);

        log.info("Inventory adjustment created and applied immediately: {}", savedAdjustment.getAdjustmentId());
        return convertToResponse(savedAdjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse approveInventoryAdjustment(UUID adjustmentId, ApprovalRequest request) {
        // 1. 조정 조회
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Inventory adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS", "Adjustment is not in pending status");
        }

        // 2. 승인 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());
        InventoryAdjustment savedAdjustment = inventoryAdjustmentRepository.save(adjustment);

        // 3. 재고 반영
        applyAdjustment(savedAdjustment);

        // 4. 이력 기록
        historyService.recordHistory(savedAdjustment);

        log.info("Inventory adjustment {} approved by {}", adjustmentId, request.getApprovedBy());
        return convertToResponse(savedAdjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse rejectInventoryAdjustment(UUID adjustmentId, ApprovalRequest request) {
        // 1. 조정 조회
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Inventory adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS", "Adjustment is not in pending status");
        }

        // 2. 거부 처리 (재고 변동 없음)
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());
        InventoryAdjustment savedAdjustment = inventoryAdjustmentRepository.save(adjustment);

        log.info("Inventory adjustment {} rejected by {}", adjustmentId, request.getApprovedBy());
        return convertToResponse(savedAdjustment);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getInventoryAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Inventory adjustment not found"));
        return convertToResponse(adjustment);
    }

    @Transactional(readOnly = true)
    public Page<InventoryAdjustmentResponse> getAllInventoryAdjustments(Pageable pageable) {
        return inventoryAdjustmentRepository.findAll(pageable)
                .map(this::convertToResponse);
    }

    // ===== Helper Methods =====

    private void applyAdjustment(InventoryAdjustment adjustment) {
        Product product = adjustment.getProduct();
        Location location = adjustment.getLocation();
        int difference = adjustment.getDifference();

        if (difference == 0) {
            return; // 차이가 없으면 아무것도 하지 않음
        }

        // 1. 재고 조정 반영
        // 기존 재고를 조회하거나 새로 생성
        List<Inventory> inventories = inventoryRepository.findByProductAndLocation(
                product.getProductId(), location.getLocationId());

        if (inventories.isEmpty() && difference > 0) {
            // 시스템에 없는 재고가 발견된 경우 (신규 생성)
            Inventory newInventory = Inventory.builder()
                    .product(product)
                    .location(location)
                    .quantity(adjustment.getActualQty())
                    .lotNumber(null)
                    .expiryDate(null)
                    .manufactureDate(null)
                    .receivedAt(Instant.now())
                    .isExpired(false)
                    .build();
            inventoryRepository.save(newInventory);

            // 로케이션 current_qty 갱신
            location.setCurrentQty(location.getCurrentQty() + adjustment.getActualQty());
            locationRepository.save(location);
        } else if (!inventories.isEmpty()) {
            // 기존 재고가 있는 경우
            Inventory inventory = inventories.get(0); // 첫 번째 재고에 조정 반영
            int newQty = inventory.getQuantity() + difference;

            if (newQty < 0) {
                throw new BusinessException("INVALID_ADJUSTMENT", "Adjustment would result in negative inventory");
            }

            if (newQty == 0) {
                inventoryRepository.delete(inventory);
            } else {
                inventory.setQuantity(newQty);
                inventoryRepository.save(inventory);
            }

            // 로케이션 current_qty 갱신
            location.setCurrentQty(location.getCurrentQty() + difference);
            locationRepository.save(location);
        }

        // 2. HIGH_VALUE 감사 로그 기록
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            Map<String, Object> details = new HashMap<>();
            details.put("adjustmentId", adjustment.getAdjustmentId().toString());
            details.put("productId", product.getProductId().toString());
            details.put("productSku", product.getSku());
            details.put("locationId", location.getLocationId().toString());
            details.put("locationCode", location.getCode());
            details.put("systemQty", adjustment.getSystemQty());
            details.put("actualQty", adjustment.getActualQty());
            details.put("difference", difference);
            details.put("reason", adjustment.getReason());
            details.put("approvedBy", adjustment.getApprovedBy());

            AuditLog auditLog = AuditLog.builder()
                    .eventType("HIGH_VALUE_ADJUSTMENT")
                    .entityType("InventoryAdjustment")
                    .entityId(adjustment.getAdjustmentId())
                    .details(details)
                    .performedBy(adjustment.getApprovedBy() != null ?
                            adjustment.getApprovedBy() : adjustment.getAdjustedBy())
                    .build();
            auditLogRepository.save(auditLog);

            log.warn("HIGH_VALUE adjustment recorded for product {} at location {}. Full location re-count recommended.",
                    product.getSku(), location.getCode());
        }

        // 3. 안전재고 체크
        checkSafetyStockAfterAdjustment(product);

        log.info("Adjustment applied: {} units for product {} at location {}",
                difference, product.getSku(), location.getCode());
    }

    private void checkSafetyStockAfterAdjustment(Product product) {
        // 전체 가용 재고 확인 (is_expired=false인 것만)
        int totalStock = inventoryRepository.sumAvailableQuantityByProduct(product.getProductId());

        SafetyStockRule rule = safetyStockRuleRepository.findByProductProductId(product.getProductId())
                .orElse(null);

        if (rule != null && totalStock <= rule.getMinQty()) {
            // 자동 재발주 로그 생성
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .product(product)
                    .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalStock)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy("SYSTEM")
                    .build();
            autoReorderLogRepository.save(reorderLog);

            log.warn("Safety stock triggered for product {}: current={}, min={}",
                    product.getSku(), totalStock, rule.getMinQty());
        }
    }

    private CycleCountResponse convertToCycleCountResponse(CycleCount cycleCount) {
        return CycleCountResponse.builder()
                .cycleCountId(cycleCount.getCycleCountId())
                .locationId(cycleCount.getLocation().getLocationId())
                .locationCode(cycleCount.getLocation().getCode())
                .status(cycleCount.getStatus().name())
                .startedBy(cycleCount.getStartedBy())
                .startedAt(cycleCount.getStartedAt())
                .completedAt(cycleCount.getCompletedAt())
                .build();
    }

    private InventoryAdjustmentResponse convertToResponse(InventoryAdjustment adjustment) {
        return InventoryAdjustmentResponse.builder()
                .adjustmentId(adjustment.getAdjustmentId())
                .productId(adjustment.getProduct().getProductId())
                .productSku(adjustment.getProduct().getSku())
                .productName(adjustment.getProduct().getName())
                .locationId(adjustment.getLocation().getLocationId())
                .locationCode(adjustment.getLocation().getCode())
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
                .build();
    }
}
