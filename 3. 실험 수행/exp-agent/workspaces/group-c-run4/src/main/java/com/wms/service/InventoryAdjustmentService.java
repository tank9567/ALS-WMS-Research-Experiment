package com.wms.service;

import com.wms.dto.ApprovalRequest;
import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final CycleCountRepository cycleCountRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * 실사 시작 (ALS-WMS-ADJ-002 - 실사 동결)
     */
    @Transactional
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        log.info("실사 시작: location={}, startedBy={}", request.getLocationId(), request.getStartedBy());

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + request.getLocationId()));

        // 이미 진행 중인 실사 체크
        cycleCountRepository.findByLocationLocationIdAndStatus(
                request.getLocationId(), CycleCount.CycleCountStatus.IN_PROGRESS)
                .ifPresent(cc -> {
                    throw new IllegalStateException("해당 로케이션에 이미 진행 중인 실사가 있습니다: " + location.getCode());
                });

        // 실사 시작: 로케이션 동결 (ALS-WMS-ADJ-002 Constraints)
        location.setIsFrozen(true);
        locationRepository.save(location);

        CycleCount cycleCount = CycleCount.builder()
                .location(location)
                .status(CycleCount.CycleCountStatus.IN_PROGRESS)
                .startedBy(request.getStartedBy())
                .build();

        cycleCountRepository.save(cycleCount);

        log.info("실사 시작 완료, 로케이션 동결: {}", location.getCode());

        return CycleCountResponse.from(cycleCount);
    }

    /**
     * 실사 완료 (ALS-WMS-ADJ-002 - 실사 동결 해제)
     */
    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId) {
        log.info("실사 완료: cycleCountId={}", cycleCountId);

        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new IllegalArgumentException("실사를 찾을 수 없습니다: " + cycleCountId));

        if (cycleCount.getStatus() == CycleCount.CycleCountStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 실사입니다");
        }

        // 실사 완료: 로케이션 동결 해제 (ALS-WMS-ADJ-002 Constraints)
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        cycleCount.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(OffsetDateTime.now());
        cycleCountRepository.save(cycleCount);

        log.info("실사 완료, 로케이션 동결 해제: {}", location.getCode());

        return CycleCountResponse.from(cycleCount);
    }

    /**
     * 재고 조정 생성 (ALS-WMS-ADJ-002 규칙 준수)
     */
    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        log.info("재고 조정 생성: inventory={}, actualQty={}, reason={}",
                request.getInventoryId(), request.getActualQty(), request.getReason());

        // 1. 사유 필수 체크 (ALS-WMS-ADJ-002 Constraints - 기본 규칙)
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("조정 사유는 필수입니다");
        }

        // 2. 재고 조회
        Inventory inventory = inventoryRepository.findById(request.getInventoryId())
                .orElseThrow(() -> new IllegalArgumentException("재고를 찾을 수 없습니다: " + request.getInventoryId()));

        Product product = inventory.getProduct();
        Location location = inventory.getLocation();
        Integer systemQty = inventory.getQuantity();
        Integer actualQty = request.getActualQty();
        Integer difference = actualQty - systemQty;

        // 3. 음수 재고 체크 (ALS-WMS-ADJ-002 Constraints - 기본 규칙)
        if (actualQty < 0) {
            throw new IllegalArgumentException("실제 수량은 음수가 될 수 없습니다");
        }

        // 4. 차이 비율 계산
        double diffPct = 0.0;
        if (systemQty > 0) {
            diffPct = Math.abs(difference) * 100.0 / systemQty;
        }

        // 5. 연속 조정 감시 (ALS-WMS-ADJ-002 Constraints - Level 2)
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        List<InventoryAdjustment> recentAdjustments = adjustmentRepository.findRecentAdjustments(
                product.getProductId(), location.getLocationId(), sevenDaysAgo);

        boolean hasRecentAdjustment = !recentAdjustments.isEmpty();
        String adjustedReason = request.getReason();

        if (hasRecentAdjustment) {
            adjustedReason = "[연속조정감시] " + adjustedReason;
            log.warn("연속 조정 감지: product={}, location={}, 최근 7일 내 조정 {}건",
                    product.getSku(), location.getCode(), recentAdjustments.size());
        }

        // 6. 승인 필요 여부 결정 (ALS-WMS-ADJ-002 Constraints)
        boolean requiresApproval;
        InventoryAdjustment.ApprovalStatus approvalStatus;
        String warning = null;

        // 6-1. system_qty = 0인 경우: 무조건 승인 필요
        if (systemQty == 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
            log.info("시스템 수량 0 → 승인 필요");
        }
        // 6-2. 연속 조정 감시 발동: 무조건 승인 필요
        else if (hasRecentAdjustment) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
            log.info("연속 조정 감시 발동 → 승인 필요");
        }
        // 6-3. HIGH_VALUE: 차이가 0이 아니면 무조건 승인 필요 (자동 승인 없음)
        else if (product.getCategory() == Product.ProductCategory.HIGH_VALUE) {
            if (difference != 0) {
                requiresApproval = true;
                approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
                warning = "해당 로케이션 전체 재실사를 권고합니다";
                log.info("HIGH_VALUE 상품 차이 발생 → 승인 필요");
            } else {
                // 차이 없으면 자동 승인
                requiresApproval = false;
                approvalStatus = InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
            }
        }
        // 6-4. 카테고리별 임계치 체크
        else {
            double threshold = getCategoryThreshold(product.getCategory());
            if (diffPct <= threshold) {
                requiresApproval = false;
                approvalStatus = InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
                log.info("자동 승인: diffPct={}%, threshold={}%", diffPct, threshold);
            } else {
                requiresApproval = true;
                approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
                log.info("승인 필요: diffPct={}%, threshold={}%", diffPct, threshold);
            }
        }

        // 7. 조정 레코드 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .product(product)
                .location(location)
                .inventory(inventory)
                .systemQty(systemQty)
                .actualQty(actualQty)
                .difference(difference)
                .reason(adjustedReason)
                .requiresApproval(requiresApproval)
                .approvalStatus(approvalStatus)
                .build();

        adjustmentRepository.save(adjustment);

        // 8. 자동 승인인 경우 즉시 재고 반영
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(adjustment);
        }

        InventoryAdjustmentResponse response = InventoryAdjustmentResponse.from(adjustment);
        response.setWarning(warning);

        log.info("재고 조정 생성 완료: adjustmentId={}, approvalStatus={}", adjustment.getAdjustmentId(), approvalStatus);

        return response;
    }

    /**
     * 조정 승인 (ALS-WMS-ADJ-002)
     */
    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, ApprovalRequest request) {
        log.info("재고 조정 승인: adjustmentId={}, approvedBy={}", adjustmentId, request.getApprovedBy());

        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("조정을 찾을 수 없습니다: " + adjustmentId));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new IllegalStateException("승인 대기 중인 조정이 아닙니다: " + adjustment.getApprovalStatus());
        }

        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustmentRepository.save(adjustment);

        // 재고 반영
        applyAdjustment(adjustment);

        // HIGH_VALUE 감사 로그 기록 (ALS-WMS-ADJ-002 Constraints - 고가품 전수 검증)
        if (adjustment.getProduct().getCategory() == Product.ProductCategory.HIGH_VALUE) {
            AuditLog auditLog = AuditLog.builder()
                    .eventType("HIGH_VALUE_ADJUSTMENT")
                    .entityType("inventory_adjustment")
                    .entityId(adjustment.getAdjustmentId())
                    .details(String.format("{\"system_qty\": %d, \"actual_qty\": %d, \"difference\": %d, \"approved_by\": \"%s\"}",
                            adjustment.getSystemQty(), adjustment.getActualQty(), adjustment.getDifference(), request.getApprovedBy()))
                    .build();
            auditLogRepository.save(auditLog);
            log.info("HIGH_VALUE 조정 감사 로그 기록 완료");
        }

        InventoryAdjustmentResponse response = InventoryAdjustmentResponse.from(adjustment);
        if (adjustment.getProduct().getCategory() == Product.ProductCategory.HIGH_VALUE && adjustment.getDifference() != 0) {
            response.setWarning("해당 로케이션 전체 재실사를 권고합니다");
        }

        log.info("재고 조정 승인 완료");

        return response;
    }

    /**
     * 조정 거부 (ALS-WMS-ADJ-002)
     */
    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, ApprovalRequest request) {
        log.info("재고 조정 거부: adjustmentId={}, approvedBy={}", adjustmentId, request.getApprovedBy());

        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("조정을 찾을 수 없습니다: " + adjustmentId));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new IllegalStateException("승인 대기 중인 조정이 아닙니다: " + adjustment.getApprovalStatus());
        }

        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustmentRepository.save(adjustment);

        log.info("재고 조정 거부 완료, 재고 변동 없음");

        return InventoryAdjustmentResponse.from(adjustment);
    }

    /**
     * 조정 상세 조회
     */
    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("조정을 찾을 수 없습니다: " + adjustmentId));

        return InventoryAdjustmentResponse.from(adjustment);
    }

    /**
     * 조정 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<InventoryAdjustmentResponse> getAdjustments(Pageable pageable) {
        return adjustmentRepository.findAll(pageable)
                .map(InventoryAdjustmentResponse::from);
    }

    /**
     * 조정 반영 (재고 갱신 + 안전재고 체크)
     * ALS-WMS-ADJ-002 Constraints - 안전재고 연쇄 체크
     */
    private void applyAdjustment(InventoryAdjustment adjustment) {
        Inventory inventory = adjustment.getInventory();
        Location location = inventory.getLocation();
        Product product = inventory.getProduct();

        // 1. 재고 수량 갱신
        int oldQty = inventory.getQuantity();
        int newQty = adjustment.getActualQty();
        int difference = adjustment.getDifference();

        inventory.setQuantity(newQty);
        inventoryRepository.save(inventory);

        // 2. 로케이션 적재량 갱신
        location.setCurrentQty(location.getCurrentQty() + difference);
        locationRepository.save(location);

        log.info("재고 반영 완료: inventory={}, {}→{}, location.currentQty={}",
                inventory.getInventoryId(), oldQty, newQty, location.getCurrentQty());

        // 3. 안전재고 체크 (ALS-WMS-ADJ-002 Constraints - 안전재고 연쇄 체크)
        checkSafetyStock(product);
    }

    /**
     * 안전재고 체크 및 자동 재발주
     * ALS-WMS-ADJ-002 Constraints - 안전재고 연쇄 체크
     */
    private void checkSafetyStock(Product product) {
        safetyStockRuleRepository.findByProductProductId(product.getProductId())
                .ifPresent(rule -> {
                    // 전체 가용 재고 합산 (is_expired=true 제외)
                    int totalAvailable = inventoryRepository.findByProduct(product).stream()
                            .filter(inv -> !Boolean.TRUE.equals(inv.getIsExpired()))
                            .mapToInt(Inventory::getQuantity)
                            .sum();

                    log.info("안전재고 체크: product={}, totalAvailable={}, minQty={}",
                            product.getSku(), totalAvailable, rule.getMinQty());

                    if (totalAvailable <= rule.getMinQty()) {
                        // 자동 재발주 기록
                        AutoReorderLog reorderLog = AutoReorderLog.builder()
                                .product(product)
                                .triggerReason("SAFETY_STOCK_TRIGGER")
                                .currentQty(totalAvailable)
                                .minQty(rule.getMinQty())
                                .reorderQty(rule.getReorderQty())
                                .build();
                        autoReorderLogRepository.save(reorderLog);

                        log.warn("안전재고 미달 → 자동 재발주 기록: product={}, reorderQty={}",
                                product.getSku(), rule.getReorderQty());
                    }
                });
    }

    /**
     * 카테고리별 자동승인 임계치 반환
     * ALS-WMS-ADJ-002 Constraints - 카테고리별 자동승인 임계치
     */
    private double getCategoryThreshold(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 5.0;
            case FRESH -> 3.0;
            case HAZMAT -> 1.0;
            case HIGH_VALUE -> 0.0; // HIGH_VALUE는 별도 로직에서 처리
        };
    }
}
