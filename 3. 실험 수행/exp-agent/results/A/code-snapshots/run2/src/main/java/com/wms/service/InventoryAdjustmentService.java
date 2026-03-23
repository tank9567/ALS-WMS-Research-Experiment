package com.wms.service;

import com.wms.dto.*;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final CycleCountRepository cycleCountRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    // 카테고리별 자동승인 임계치
    private static final Map<Product.ProductCategory, Double> AUTO_APPROVAL_THRESHOLDS = new HashMap<>();
    static {
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.GENERAL, 0.05);    // 5%
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.FRESH, 0.03);      // 3%
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.HAZMAT, 0.01);     // 1%
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.HIGH_VALUE, 0.05); // 5%
    }

    @Transactional
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        // 1. Location 조회
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        // 2. 진행 중인 실사가 있는지 확인
        cycleCountRepository.findByLocationAndStatus(location, CycleCount.CycleCountStatus.IN_PROGRESS)
                .ifPresent(cc -> {
                    throw new BusinessException("CYCLE_COUNT_IN_PROGRESS",
                            "A cycle count is already in progress for location: " + location.getCode());
                });

        // 3. 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 4. CycleCount 생성
        CycleCount cycleCount = CycleCount.builder()
                .location(location)
                .status(CycleCount.CycleCountStatus.IN_PROGRESS)
                .startedBy(request.getStartedBy())
                .startedAt(Instant.now())
                .build();

        CycleCount saved = cycleCountRepository.save(cycleCount);
        log.info("Cycle count started for location: {} by: {}", location.getCode(), request.getStartedBy());

        return CycleCountResponse.from(saved);
    }

    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId, CycleCountCompleteRequest request) {
        // 1. CycleCount 조회
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new BusinessException("CYCLE_COUNT_NOT_FOUND", "Cycle count not found"));

        if (cycleCount.getStatus() == CycleCount.CycleCountStatus.COMPLETED) {
            throw new BusinessException("CYCLE_COUNT_ALREADY_COMPLETED", "Cycle count is already completed");
        }

        // 2. 실사 완료 처리
        cycleCount.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        cycleCount.setCompletedBy(request.getCompletedBy());
        cycleCount.setCompletedAt(Instant.now());

        // 3. 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        CycleCount saved = cycleCountRepository.save(cycleCount);
        log.info("Cycle count completed for location: {} by: {}", location.getCode(), request.getCompletedBy());

        return CycleCountResponse.from(saved);
    }

    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        // 1. Validation - reason 필수
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException("REASON_REQUIRED", "Adjustment reason is required");
        }

        // 2. 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        CycleCount cycleCount = null;
        if (request.getCycleCountId() != null) {
            cycleCount = cycleCountRepository.findById(request.getCycleCountId())
                    .orElseThrow(() -> new BusinessException("CYCLE_COUNT_NOT_FOUND", "Cycle count not found"));
        }

        Inventory inventory = null;
        if (request.getInventoryId() != null) {
            inventory = inventoryRepository.findById(request.getInventoryId())
                    .orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", "Inventory not found"));
        }

        // 3. 차이 계산
        Integer systemQty = request.getSystemQty();
        Integer actualQty = request.getActualQty();
        Integer difference = actualQty - systemQty;

        // 4. 승인 상태 판단
        InventoryAdjustment.ApprovalStatus approvalStatus = determineApprovalStatus(
                product, location, systemQty, actualQty, difference, request.getReason());

        // 5. InventoryAdjustment 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .cycleCount(cycleCount)
                .product(product)
                .location(location)
                .inventory(inventory)
                .systemQty(systemQty)
                .actualQty(actualQty)
                .difference(difference)
                .reason(request.getReason())
                .approvalStatus(approvalStatus)
                .build();

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);
        log.info("Inventory adjustment created: product={}, location={}, diff={}, status={}",
                product.getSku(), location.getCode(), difference, approvalStatus);

        // 6. 자동 승인된 경우에만 즉시 재고 반영
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(saved);
        }

        return InventoryAdjustmentResponse.from(saved);
    }

    private InventoryAdjustment.ApprovalStatus determineApprovalStatus(
            Product product, Location location, Integer systemQty, Integer actualQty,
            Integer difference, String reason) {

        // 1. system_qty=0 시 승인 필요
        if (systemQty == 0) {
            log.info("System qty is 0, approval required");
            return InventoryAdjustment.ApprovalStatus.PENDING;
        }

        // 2. 연속 조정 감시 (최근 7일 내 2회 이상)
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<InventoryAdjustment> recentAdjustments = adjustmentRepository.findRecentApprovedAdjustments(
                product, location, sevenDaysAgo);

        if (recentAdjustments.size() >= 2) {
            log.info("Consecutive adjustments detected ({}), approval required", recentAdjustments.size());
            return InventoryAdjustment.ApprovalStatus.PENDING;
        }

        // 3. 카테고리별 자동승인 임계치 체크
        double threshold = AUTO_APPROVAL_THRESHOLDS.get(product.getCategory());
        double diffRatio = Math.abs((double) difference / systemQty);

        if (diffRatio <= threshold) {
            log.info("Difference ratio {} is within threshold {}, auto approved", diffRatio, threshold);
            return InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
        } else {
            log.info("Difference ratio {} exceeds threshold {}, approval required", diffRatio, threshold);
            return InventoryAdjustment.ApprovalStatus.PENDING;
        }
    }

    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, InventoryAdjustmentApprovalRequest request) {
        // 1. Adjustment 조회
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS",
                    "Only pending adjustments can be approved. Current status: " + adjustment.getApprovalStatus());
        }

        // 2. 승인 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);
        log.info("Inventory adjustment approved: id={}, by={}", adjustmentId, request.getApprovedBy());

        // 3. 재고 반영
        applyAdjustment(saved);

        return InventoryAdjustmentResponse.from(saved);
    }

    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, InventoryAdjustmentRejectionRequest request) {
        // 1. Adjustment 조회
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS",
                    "Only pending adjustments can be rejected. Current status: " + adjustment.getApprovalStatus());
        }

        // 2. 거부 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());
        adjustment.setRejectionReason(request.getRejectionReason());

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);
        log.info("Inventory adjustment rejected: id={}, by={}, reason={}",
                adjustmentId, request.getApprovedBy(), request.getRejectionReason());

        return InventoryAdjustmentResponse.from(saved);
    }

    private void applyAdjustment(InventoryAdjustment adjustment) {
        // 1. Inventory 업데이트
        Inventory inventory = adjustment.getInventory();
        if (inventory == null) {
            // 새로운 재고 생성 (실물 발견 케이스)
            inventory = Inventory.builder()
                    .product(adjustment.getProduct())
                    .location(adjustment.getLocation())
                    .quantity(adjustment.getActualQty())
                    .receivedAt(Instant.now())
                    .build();
            inventoryRepository.save(inventory);
            log.info("New inventory created: product={}, location={}, qty={}",
                    adjustment.getProduct().getSku(), adjustment.getLocation().getCode(), adjustment.getActualQty());
        } else {
            // 기존 재고 업데이트
            int oldQty = inventory.getQuantity();
            inventory.setQuantity(adjustment.getActualQty());
            inventoryRepository.save(inventory);
            log.info("Inventory updated: product={}, location={}, {} -> {}",
                    adjustment.getProduct().getSku(), adjustment.getLocation().getCode(), oldQty, adjustment.getActualQty());
        }

        // 2. Location 현재 수량 업데이트
        Location location = adjustment.getLocation();
        int locationDiff = adjustment.getDifference();
        int newLocationQty = location.getCurrentQuantity() + locationDiff;

        if (newLocationQty < 0) {
            throw new BusinessException("INVALID_LOCATION_QUANTITY",
                    "Location quantity cannot be negative: " + newLocationQty);
        }

        if (newLocationQty > location.getCapacity()) {
            throw new BusinessException("LOCATION_CAPACITY_EXCEEDED",
                    "Location capacity exceeded: " + newLocationQty + " > " + location.getCapacity());
        }

        location.setCurrentQuantity(newLocationQty);
        locationRepository.save(location);

        // 3. 안전재고 체크
        checkSafetyStock(adjustment.getProduct());
    }

    private void checkSafetyStock(Product product) {
        // 1. SafetyStockRule 조회
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct(product).orElse(null);
        if (rule == null) {
            return; // 안전재고 규칙이 없으면 체크하지 않음
        }

        // 2. 전체 가용 재고 계산 (모든 로케이션 합산, expired 제외)
        Integer totalAvailableQty = inventoryRepository.sumAvailableQuantityByProduct(product);
        if (totalAvailableQty == null) {
            totalAvailableQty = 0;
        }

        // 3. 안전재고 이하인지 체크
        if (totalAvailableQty <= rule.getMinQty()) {
            // 자동 재발주 로그 기록
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason("SAFETY_STOCK_TRIGGER")
                    .reorderQty(rule.getReorderQty())
                    .triggeredAt(Instant.now())
                    .build();
            autoReorderLogRepository.save(reorderLog);

            log.warn("Safety stock threshold reached for product: {}, available: {}, min: {}, reorder: {}",
                    product.getSku(), totalAvailableQty, rule.getMinQty(), rule.getReorderQty());
        }
    }


    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found"));
        return InventoryAdjustmentResponse.from(adjustment);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentListResponse getAdjustments() {
        List<InventoryAdjustment> adjustments = adjustmentRepository.findAll();
        List<InventoryAdjustmentResponse> responses = adjustments.stream()
                .map(InventoryAdjustmentResponse::from)
                .collect(Collectors.toList());

        return InventoryAdjustmentListResponse.builder()
                .adjustments(responses)
                .total(responses.size())
                .build();
    }

    @Transactional(readOnly = true)
    public AdjustmentHistoryListResponse getAdjustmentHistory() {
        List<InventoryAdjustment> adjustments = adjustmentRepository.findAll();
        List<AdjustmentHistoryResponse> responses = adjustments.stream()
                .map(AdjustmentHistoryResponse::fromEntity)
                .collect(Collectors.toList());

        return AdjustmentHistoryListResponse.builder()
                .adjustments(responses)
                .totalCount(responses.size())
                .build();
    }

    @Transactional
    public Map<String, Object> deleteOldAdjustments() {
        // 1년 이전 날짜 계산
        Instant oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);

        // 1년 이전 조정 이력 조회
        List<InventoryAdjustment> oldAdjustments = adjustmentRepository.findOlderThan(oneYearAgo);

        // 삭제
        int deletedCount = oldAdjustments.size();
        adjustmentRepository.deleteAll(oldAdjustments);

        log.info("Deleted {} inventory adjustments older than 1 year", deletedCount);

        Map<String, Object> result = new HashMap<>();
        result.put("deletedCount", deletedCount);
        result.put("cutoffDate", oneYearAgo);

        return result;
    }
}
