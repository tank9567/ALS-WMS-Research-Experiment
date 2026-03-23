package com.wms.adjustment.service;

import com.wms.adjustment.dto.*;
import com.wms.adjustment.entity.CycleCount;
import com.wms.adjustment.entity.InventoryAdjustment;
import com.wms.adjustment.repository.CycleCountRepository;
import com.wms.adjustment.repository.InventoryAdjustmentRepository;
import com.wms.inbound.entity.Inventory;
import com.wms.inbound.entity.Location;
import com.wms.inbound.entity.Product;
import com.wms.inbound.repository.InventoryRepository;
import com.wms.inbound.repository.LocationRepository;
import com.wms.inbound.repository.ProductRepository;
import com.wms.outbound.entity.AuditLog;
import com.wms.outbound.entity.AutoReorderLog;
import com.wms.outbound.entity.SafetyStockRule;
import com.wms.outbound.repository.AuditLogRepository;
import com.wms.outbound.repository.AutoReorderLogRepository;
import com.wms.outbound.repository.SafetyStockRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final CycleCountRepository cycleCountRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * 실사 시작
     * ALS-WMS-ADJ-002: 실사 시작 시 로케이션 동결 (is_frozen=true)
     */
    @Transactional
    public CycleCountResponse startCycleCount(CycleCountStartRequest request) {
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + request.getLocationId()));

        // 이미 실사 진행 중인지 체크
        cycleCountRepository.findByLocationLocationIdAndStatus(location.getLocationId(), CycleCount.CycleCountStatus.IN_PROGRESS)
                .ifPresent(cc -> {
                    throw new IllegalStateException("이미 실사가 진행 중인 로케이션입니다: " + location.getCode());
                });

        // 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 실사 세션 생성
        CycleCount cycleCount = CycleCount.builder()
                .cycleCountId(UUID.randomUUID())
                .location(location)
                .status(CycleCount.CycleCountStatus.IN_PROGRESS)
                .startedBy(request.getStartedBy())
                .build();

        cycleCount = cycleCountRepository.save(cycleCount);

        return CycleCountResponse.from(cycleCount);
    }

    /**
     * 실사 완료
     * ALS-WMS-ADJ-002: 실사 완료 시 로케이션 동결 해제 (is_frozen=false)
     */
    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new IllegalArgumentException("실사 세션을 찾을 수 없습니다: " + cycleCountId));

        if (cycleCount.getStatus() == CycleCount.CycleCountStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 실사입니다.");
        }

        // 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        // 실사 완료
        cycleCount.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(Instant.now());
        cycleCount = cycleCountRepository.save(cycleCount);

        return CycleCountResponse.from(cycleCount);
    }

    /**
     * 재고 조정 생성
     * ALS-WMS-ADJ-002: 모든 조정 규칙 적용
     */
    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentCreateRequest request) {
        // 사유 필수 체크
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("조정 사유는 필수입니다.");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + request.getProductId()));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + request.getLocationId()));

        // 시스템 재고 조회
        Integer systemQty = inventoryRepository.findByProductProductIdAndLocationLocationId(
                        product.getProductId(), location.getLocationId())
                .map(Inventory::getQuantity)
                .orElse(0);

        Integer actualQty = request.getActualQty();
        Integer difference = actualQty - systemQty;

        // 조정으로 재고가 음수가 되는 것 방지
        if (actualQty < 0) {
            throw new IllegalArgumentException("실제 수량은 음수가 될 수 없습니다.");
        }

        // 승인 필요 여부 및 자동승인 결정
        boolean requiresApproval = false;
        InventoryAdjustment.ApprovalStatus approvalStatus = InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
        String reason = request.getReason();

        // 1. system_qty = 0인 경우 무조건 승인 필요
        if (systemQty == 0 && difference != 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // 2. HIGH_VALUE 카테고리: 차이가 0이 아니면 무조건 승인 필요 (자동승인 없음)
        else if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // 3. 연속 조정 감시: 최근 7일 내 동일 상품+로케이션 조정이 2회 이상이면 승인 필요
        else {
            Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
            List<InventoryAdjustment> recentAdjustments = adjustmentRepository.findRecentAdjustmentsByProductAndLocation(
                    product.getProductId(), location.getLocationId(), sevenDaysAgo);

            if (recentAdjustments.size() >= 2) {
                requiresApproval = true;
                approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
                reason = "[연속조정감시] " + reason;
            }
            // 4. 카테고리별 자동승인 임계치 체크
            else if (systemQty > 0) {
                double diffPct = Math.abs(difference) * 100.0 / systemQty;
                double threshold = getCategoryThreshold(product.getCategory());

                if (diffPct > threshold) {
                    requiresApproval = true;
                    approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
                }
            }
        }

        // 조정 레코드 생성
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
                .build();

        adjustment = adjustmentRepository.save(adjustment);

        // 자동 승인인 경우 즉시 재고 반영
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            applyAdjustmentToInventory(adjustment);
            checkSafetyStockAfterAdjustment(product);
        }

        InventoryAdjustmentResponse response = InventoryAdjustmentResponse.from(adjustment);

        // HIGH_VALUE 경고 메시지
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            response.setWarning("고가품 조정입니다. 해당 로케이션 전체 재실사를 권고합니다.");
        }

        return response;
    }

    /**
     * 재고 조정 승인
     * ALS-WMS-ADJ-002: 승인 시 재고 반영, HIGH_VALUE는 audit_logs 기록
     */
    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, InventoryAdjustmentApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("조정 레코드를 찾을 수 없습니다: " + adjustmentId));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new IllegalStateException("승인 대기 상태가 아닙니다.");
        }

        // 승인 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());
        adjustment = adjustmentRepository.save(adjustment);

        // 재고 반영
        applyAdjustmentToInventory(adjustment);

        // HIGH_VALUE 카테고리면 감사 로그 기록
        if (adjustment.getProduct().getCategory() == Product.ProductCategory.HIGH_VALUE) {
            recordHighValueAuditLog(adjustment);
        }

        // 안전재고 체크
        checkSafetyStockAfterAdjustment(adjustment.getProduct());

        InventoryAdjustmentResponse response = InventoryAdjustmentResponse.from(adjustment);

        // HIGH_VALUE 경고 메시지
        if (adjustment.getProduct().getCategory() == Product.ProductCategory.HIGH_VALUE && adjustment.getDifference() != 0) {
            response.setWarning("고가품 조정이 승인되었습니다. 해당 로케이션 전체 재실사를 권고합니다.");
        }

        return response;
    }

    /**
     * 재고 조정 거부
     * ALS-WMS-ADJ-002: 거부 시 재고 변동 없음
     */
    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, InventoryAdjustmentApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("조정 레코드를 찾을 수 없습니다: " + adjustmentId));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new IllegalStateException("승인 대기 상태가 아닙니다.");
        }

        // 거부 처리 (재고 변동 없음)
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());
        adjustment = adjustmentRepository.save(adjustment);

        return InventoryAdjustmentResponse.from(adjustment);
    }

    /**
     * 조정 상세 조회
     */
    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new IllegalArgumentException("조정 레코드를 찾을 수 없습니다: " + adjustmentId));

        return InventoryAdjustmentResponse.from(adjustment);
    }

    /**
     * 조정 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getAdjustments(String status) {
        List<InventoryAdjustment> adjustments;

        if (status != null && !status.isEmpty()) {
            InventoryAdjustment.ApprovalStatus approvalStatus = InventoryAdjustment.ApprovalStatus.valueOf(status.toUpperCase());
            adjustments = adjustmentRepository.findByApprovalStatus(approvalStatus);
        } else {
            adjustments = adjustmentRepository.findAll();
        }

        return adjustments.stream()
                .map(InventoryAdjustmentResponse::from)
                .toList();
    }

    // === Private Helper Methods ===

    /**
     * 카테고리별 자동승인 임계치 반환
     * ALS-WMS-ADJ-002: GENERAL=5%, FRESH=3%, HAZMAT=1%, HIGH_VALUE=자동승인 없음
     */
    private double getCategoryThreshold(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 5.0;
            case FRESH -> 3.0;
            case HAZMAT -> 1.0;
            case HIGH_VALUE -> 0.0; // 실제로는 이 메서드 호출 전에 필터링됨
        };
    }

    /**
     * 재고에 조정 반영
     * ALS-WMS-ADJ-002: 승인된 조정만 재고에 반영
     */
    private void applyAdjustmentToInventory(InventoryAdjustment adjustment) {
        Product product = adjustment.getProduct();
        Location location = adjustment.getLocation();
        Integer newQty = adjustment.getActualQty();

        Inventory inventory = inventoryRepository.findByProductProductIdAndLocationLocationId(
                        product.getProductId(), location.getLocationId())
                .orElse(null);

        if (inventory == null) {
            // 신규 재고 생성
            inventory = Inventory.builder()
                    .inventoryId(UUID.randomUUID())
                    .product(product)
                    .location(location)
                    .quantity(newQty)
                    .receivedAt(Instant.now())
                    .isExpired(false)
                    .build();
        } else {
            // 기존 재고 수정
            int oldQty = inventory.getQuantity();
            inventory.setQuantity(newQty);

            // 로케이션 현재 적재량도 갱신
            int qtyChange = newQty - oldQty;
            location.setCurrentQty(location.getCurrentQty() + qtyChange);
            locationRepository.save(location);
        }

        inventoryRepository.save(inventory);
    }

    /**
     * HIGH_VALUE 조정 시 감사 로그 기록
     * ALS-WMS-ADJ-002: HIGH_VALUE 조정 반영 시 audit_logs 기록
     */
    private void recordHighValueAuditLog(InventoryAdjustment adjustment) {
        AuditLog auditLog = AuditLog.builder()
                .logId(UUID.randomUUID())
                .eventType(AuditLog.EventType.HIGH_VALUE_ADJUSTMENT)
                .product(adjustment.getProduct())
                .location(adjustment.getLocation())
                .referenceId(adjustment.getAdjustmentId())
                .referenceType("inventory_adjustment")
                .message(String.format("HIGH_VALUE 조정 승인: system_qty=%d, actual_qty=%d, difference=%d, approved_by=%s",
                        adjustment.getSystemQty(), adjustment.getActualQty(),
                        adjustment.getDifference(), adjustment.getApprovedBy()))
                .severity("HIGH")
                .build();

        auditLogRepository.save(auditLog);
    }

    /**
     * 안전재고 체크 (조정 후)
     * ALS-WMS-ADJ-002: 조정 반영 후 안전재고 미달 시 자동 재발주
     */
    private void checkSafetyStockAfterAdjustment(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductIdAndIsActive(
                        product.getProductId(), true)
                .orElse(null);

        if (rule == null) {
            return;
        }

        // 전체 가용 재고 합산 (is_expired=false)
        Integer totalStock = inventoryRepository.findByProductProductIdAndIsExpired(
                        product.getProductId(), false)
                .stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 미달 시 자동 재발주 로그 기록
        if (totalStock < rule.getMinQty()) {
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .reorderLogId(UUID.randomUUID())
                    .product(product)
                    .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalStock)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy("SYSTEM")
                    .build();

            autoReorderLogRepository.save(reorderLog);
        }
    }
}
