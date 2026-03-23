package com.wms.service;

import com.wms.dto.CycleCountCompleteRequest;
import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.*;
import com.wms.entity.CycleCount.CycleCountStatus;
import com.wms.entity.InventoryAdjustment.ApprovalStatus;
import com.wms.entity.Product.ProductCategory;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryAdjustmentService {

    private final CycleCountRepository cycleCountRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditLogRepository auditLogRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    /**
     * 실사 시작
     */
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        // 1. 기본 검증
        validateCycleCountRequest(request);

        // 2. Entity 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException("Location not found", "LOCATION_NOT_FOUND"));

        // 3. 재고 조회
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                request.getProductId(),
                request.getLocationId(),
                request.getLotNumber(),
                request.getExpiryDate()
        ).orElseThrow(() -> new BusinessException("Inventory not found at location", "INVENTORY_NOT_FOUND"));

        // 4. 이미 진행 중인 실사가 있는지 확인
        List<CycleCount> inProgressCounts = cycleCountRepository.findByLocationIdAndStatus(
                request.getLocationId(),
                CycleCountStatus.IN_PROGRESS
        );
        if (!inProgressCounts.isEmpty()) {
            throw new BusinessException("Cycle count already in progress for this location", "CYCLE_COUNT_IN_PROGRESS");
        }

        // 5. 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 6. CycleCount 엔티티 생성
        CycleCount cycleCount = CycleCount.builder()
                .location(location)
                .product(product)
                .lotNumber(request.getLotNumber())
                .expiryDate(request.getExpiryDate())
                .systemQty(inventory.getQuantity())
                .status(CycleCountStatus.IN_PROGRESS)
                .countedBy(request.getCountedBy())
                .startedAt(OffsetDateTime.now())
                .build();

        cycleCountRepository.save(cycleCount);

        return buildCycleCountResponse(cycleCount);
    }

    /**
     * 실사 완료
     */
    public CycleCountResponse completeCycleCount(UUID cycleCountId, CycleCountCompleteRequest request) {
        // 1. CycleCount 조회
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new BusinessException("Cycle count not found", "CYCLE_COUNT_NOT_FOUND"));

        if (cycleCount.getStatus() != CycleCountStatus.IN_PROGRESS) {
            throw new BusinessException("Cycle count is not in progress", "INVALID_STATUS");
        }

        // 2. 실사 수량 입력 검증
        if (request.getCountedQty() == null || request.getCountedQty() < 0) {
            throw new BusinessException("Counted quantity must be non-negative", "INVALID_COUNTED_QTY");
        }

        // 3. 실사 수량 업데이트
        cycleCount.setCountedQty(request.getCountedQty());
        cycleCount.setStatus(CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(OffsetDateTime.now());
        cycleCountRepository.save(cycleCount);

        // 4. 차이가 있으면 자동으로 재고 조정 생성
        int difference = request.getCountedQty() - cycleCount.getSystemQty();
        if (difference != 0) {
            InventoryAdjustmentRequest adjRequest = InventoryAdjustmentRequest.builder()
                    .productId(cycleCount.getProduct().getId())
                    .locationId(cycleCount.getLocation().getId())
                    .lotNumber(cycleCount.getLotNumber())
                    .expiryDate(cycleCount.getExpiryDate())
                    .actualQty(request.getCountedQty())
                    .reason("[실사] 실제 재고와 시스템 재고 불일치")
                    .createdBy(cycleCount.getCountedBy())
                    .build();

            createAdjustmentFromCycleCount(adjRequest, cycleCount);
        }

        // 5. 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        return buildCycleCountResponse(cycleCount);
    }

    /**
     * 재고 조정 생성
     */
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        // 1. 기본 검증
        validateAdjustmentRequest(request);

        // 2. Entity 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException("Location not found", "LOCATION_NOT_FOUND"));

        // 3. 재고 조회
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                request.getProductId(),
                request.getLocationId(),
                request.getLotNumber(),
                request.getExpiryDate()
        ).orElseThrow(() -> new BusinessException("Inventory not found at location", "INVENTORY_NOT_FOUND"));

        // 4. 차이 계산
        int systemQty = inventory.getQuantity();
        int actualQty = request.getActualQty();
        int differenceQty = actualQty - systemQty;

        // 5. 승인 상태 결정
        ApprovalStatus approvalStatus = determineApprovalStatus(
                product,
                location,
                systemQty,
                differenceQty
        );

        // 6. InventoryAdjustment 엔티티 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .product(product)
                .location(location)
                .lotNumber(request.getLotNumber())
                .expiryDate(request.getExpiryDate())
                .systemQty(systemQty)
                .actualQty(actualQty)
                .differenceQty(differenceQty)
                .reason(request.getReason())
                .approvalStatus(approvalStatus)
                .createdBy(request.getCreatedBy())
                .build();

        inventoryAdjustmentRepository.save(adjustment);

        // 7. 자동 승인인 경우 즉시 재고 반영
        if (approvalStatus == ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(adjustment);
        }

        return buildAdjustmentResponse(adjustment);
    }

    /**
     * 재고 조정 승인
     */
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("Adjustment not found", "ADJUSTMENT_NOT_FOUND"));

        if (adjustment.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Can only approve pending adjustments", "INVALID_STATUS");
        }

        // 승인 처리
        adjustment.setApprovalStatus(ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());
        inventoryAdjustmentRepository.save(adjustment);

        // 재고 반영
        applyAdjustment(adjustment);

        return buildAdjustmentResponse(adjustment);
    }

    /**
     * 재고 조정 거부
     */
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("Adjustment not found", "ADJUSTMENT_NOT_FOUND"));

        if (adjustment.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Can only reject pending adjustments", "INVALID_STATUS");
        }

        adjustment.setApprovalStatus(ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());
        inventoryAdjustmentRepository.save(adjustment);

        return buildAdjustmentResponse(adjustment);
    }

    /**
     * 재고 조정 상세 조회
     */
    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("Adjustment not found", "ADJUSTMENT_NOT_FOUND"));

        return buildAdjustmentResponse(adjustment);
    }

    /**
     * 재고 조정 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<InventoryAdjustmentResponse> getAdjustments(Pageable pageable) {
        Page<InventoryAdjustment> adjustments = inventoryAdjustmentRepository.findAll(pageable);
        return adjustments.map(this::buildAdjustmentResponse);
    }

    // ===== Private Helper Methods =====

    /**
     * 실사 요청 검증
     */
    private void validateCycleCountRequest(CycleCountRequest request) {
        if (request.getLocationId() == null) {
            throw new BusinessException("Location ID is required", "LOCATION_ID_REQUIRED");
        }
        if (request.getProductId() == null) {
            throw new BusinessException("Product ID is required", "PRODUCT_ID_REQUIRED");
        }
    }

    /**
     * 조정 요청 검증
     */
    private void validateAdjustmentRequest(InventoryAdjustmentRequest request) {
        if (request.getProductId() == null) {
            throw new BusinessException("Product ID is required", "PRODUCT_ID_REQUIRED");
        }
        if (request.getLocationId() == null) {
            throw new BusinessException("Location ID is required", "LOCATION_ID_REQUIRED");
        }
        if (request.getActualQty() == null || request.getActualQty() < 0) {
            throw new BusinessException("Actual quantity must be non-negative", "INVALID_ACTUAL_QTY");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException("Reason is required", "REASON_REQUIRED");
        }
    }

    /**
     * 승인 상태 결정
     */
    private ApprovalStatus determineApprovalStatus(
            Product product,
            Location location,
            int systemQty,
            int differenceQty
    ) {
        // 1. 차이가 없으면 자동 승인
        if (differenceQty == 0) {
            return ApprovalStatus.AUTO_APPROVED;
        }

        // 2. 시스템 재고가 0인데 실물이 발견된 경우 승인 필요
        if (systemQty == 0 && differenceQty > 0) {
            return ApprovalStatus.PENDING;
        }

        // 3. 카테고리별 자동승인 임계치 조회
        double thresholdPct = getAutoApprovalThreshold(product.getCategory());

        // 4. 차이율 계산
        double diffPct = Math.abs((double) differenceQty / systemQty * 100);

        // 5. 연속 조정 감시: 최근 7일 내 조정이 2회 이상이면 무조건 승인 필요
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minus(7, ChronoUnit.DAYS);
        List<InventoryAdjustment> recentAdjustments = inventoryAdjustmentRepository.findRecentAdjustments(
                product.getId(),
                location.getId(),
                sevenDaysAgo
        );
        if (recentAdjustments.size() >= 2) {
            return ApprovalStatus.PENDING;
        }

        // 6. 임계치 이내면 자동 승인, 초과면 승인 대기
        if (diffPct <= thresholdPct) {
            return ApprovalStatus.AUTO_APPROVED;
        } else {
            return ApprovalStatus.PENDING;
        }
    }

    /**
     * 카테고리별 자동승인 임계치
     */
    private double getAutoApprovalThreshold(ProductCategory category) {
        return switch (category) {
            case GENERAL -> 5.0;
            case FRESH -> 3.0;
            case HAZMAT -> 1.0;
            case HIGH_VALUE -> 2.0;
        };
    }

    /**
     * 재고 조정 반영
     */
    private void applyAdjustment(InventoryAdjustment adjustment) {
        // 1. 재고 조회
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                adjustment.getProduct().getId(),
                adjustment.getLocation().getId(),
                adjustment.getLotNumber(),
                adjustment.getExpiryDate()
        ).orElseThrow(() -> new BusinessException("Inventory not found", "INVENTORY_NOT_FOUND"));

        // 2. 재고 수량 업데이트
        int newQty = adjustment.getActualQty();
        int oldQty = inventory.getQuantity();
        inventory.setQuantity(newQty);
        inventoryRepository.save(inventory);

        // 3. 로케이션 적재량 업데이트
        Location location = adjustment.getLocation();
        int qtyDifference = newQty - oldQty;
        location.setCurrentQty(location.getCurrentQty() + qtyDifference);
        locationRepository.save(location);

        // 4. HIGH_VALUE 카테고리인 경우 감사 로그 기록
        if (adjustment.getProduct().getCategory() == ProductCategory.HIGH_VALUE) {
            AuditLog auditLog = AuditLog.builder()
                    .entityType("INVENTORY_ADJUSTMENT")
                    .entityId(adjustment.getId())
                    .action("APPROVED")
                    .description(String.format("HIGH_VALUE adjustment: %s (SKU: %s) from %d to %d at %s. Difference: %d",
                            adjustment.getProduct().getName(),
                            adjustment.getProduct().getSku(),
                            adjustment.getSystemQty(),
                            adjustment.getActualQty(),
                            adjustment.getLocation().getCode(),
                            adjustment.getDifferenceQty()))
                    .createdBy(adjustment.getApprovedBy() != null ? adjustment.getApprovedBy() : "SYSTEM")
                    .build();

            auditLogRepository.save(auditLog);
        }

        // 5. 안전재고 체크
        checkSafetyStockAfterAdjustment(adjustment.getProduct());
    }

    /**
     * 실사로부터 조정 생성 (내부 메서드)
     */
    private void createAdjustmentFromCycleCount(InventoryAdjustmentRequest request, CycleCount cycleCount) {
        // Entity 조회
        Product product = cycleCount.getProduct();
        Location location = cycleCount.getLocation();

        // 재고 조회
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                product.getId(),
                location.getId(),
                cycleCount.getLotNumber(),
                cycleCount.getExpiryDate()
        ).orElseThrow(() -> new BusinessException("Inventory not found", "INVENTORY_NOT_FOUND"));

        // 차이 계산
        int systemQty = cycleCount.getSystemQty();
        int actualQty = request.getActualQty();
        int differenceQty = actualQty - systemQty;

        // 승인 상태 결정
        ApprovalStatus approvalStatus = determineApprovalStatus(
                product,
                location,
                systemQty,
                differenceQty
        );

        // 연속 조정 감시 태그 추가
        String reason = request.getReason();
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minus(7, ChronoUnit.DAYS);
        List<InventoryAdjustment> recentAdjustments = inventoryAdjustmentRepository.findRecentAdjustments(
                product.getId(),
                location.getId(),
                sevenDaysAgo
        );
        if (recentAdjustments.size() >= 2) {
            reason = "[연속조정감시] " + reason;
        }

        // InventoryAdjustment 엔티티 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .cycleCount(cycleCount)
                .product(product)
                .location(location)
                .lotNumber(request.getLotNumber())
                .expiryDate(request.getExpiryDate())
                .systemQty(systemQty)
                .actualQty(actualQty)
                .differenceQty(differenceQty)
                .reason(reason)
                .approvalStatus(approvalStatus)
                .createdBy(request.getCreatedBy())
                .build();

        inventoryAdjustmentRepository.save(adjustment);

        // 자동 승인인 경우 즉시 재고 반영
        if (approvalStatus == ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(adjustment);
        }
    }

    /**
     * 조정 후 안전재고 체크
     */
    private void checkSafetyStockAfterAdjustment(Product product) {
        // 전체 가용 재고 확인
        List<Inventory> allInventories = inventoryRepository.findByProductId(product.getId());

        int totalAvailableQty = allInventories.stream()
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 규칙 조회
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId())
                .orElse(null);

        if (rule != null && totalAvailableQty <= rule.getMinQty()) {
            // 자동 재발주 요청 기록
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason(AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalAvailableQty)
                    .reorderQty(rule.getReorderQty())
                    .build();

            autoReorderLogRepository.save(log);
        }
    }

    /**
     * CycleCount Response 빌드
     */
    private CycleCountResponse buildCycleCountResponse(CycleCount cycleCount) {
        return CycleCountResponse.builder()
                .id(cycleCount.getId())
                .locationId(cycleCount.getLocation().getId())
                .locationCode(cycleCount.getLocation().getCode())
                .productId(cycleCount.getProduct().getId())
                .productSku(cycleCount.getProduct().getSku())
                .productName(cycleCount.getProduct().getName())
                .lotNumber(cycleCount.getLotNumber())
                .expiryDate(cycleCount.getExpiryDate())
                .systemQty(cycleCount.getSystemQty())
                .countedQty(cycleCount.getCountedQty())
                .status(cycleCount.getStatus())
                .countedBy(cycleCount.getCountedBy())
                .startedAt(cycleCount.getStartedAt())
                .completedAt(cycleCount.getCompletedAt())
                .createdAt(cycleCount.getCreatedAt())
                .updatedAt(cycleCount.getUpdatedAt())
                .build();
    }

    /**
     * InventoryAdjustment Response 빌드
     */
    private InventoryAdjustmentResponse buildAdjustmentResponse(InventoryAdjustment adjustment) {
        return InventoryAdjustmentResponse.builder()
                .id(adjustment.getId())
                .cycleCountId(adjustment.getCycleCount() != null ? adjustment.getCycleCount().getId() : null)
                .productId(adjustment.getProduct().getId())
                .productSku(adjustment.getProduct().getSku())
                .productName(adjustment.getProduct().getName())
                .locationId(adjustment.getLocation().getId())
                .locationCode(adjustment.getLocation().getCode())
                .lotNumber(adjustment.getLotNumber())
                .expiryDate(adjustment.getExpiryDate())
                .systemQty(adjustment.getSystemQty())
                .actualQty(adjustment.getActualQty())
                .differenceQty(adjustment.getDifferenceQty())
                .reason(adjustment.getReason())
                .approvalStatus(adjustment.getApprovalStatus())
                .approvedBy(adjustment.getApprovedBy())
                .approvedAt(adjustment.getApprovedAt())
                .createdBy(adjustment.getCreatedBy())
                .createdAt(adjustment.getCreatedAt())
                .updatedAt(adjustment.getUpdatedAt())
                .build();
    }
}
