package com.wms.service;

import com.wms.dto.*;
import com.wms.entity.*;
import com.wms.exception.AdjustmentException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdjustmentService {

    private final CycleCountRepository cycleCountRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    // 카테고리별 자동승인 임계치
    private static final double THRESHOLD_GENERAL = 5.0;
    private static final double THRESHOLD_FRESH = 3.0;
    private static final double THRESHOLD_HAZMAT = 1.0;
    // HIGH_VALUE는 자동승인 없음 (임계치 0)

    /**
     * 실사 시작
     */
    @Transactional
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        UUID locationId = request.getLocationId();

        // 로케이션 조회
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));

        // 이미 실사 중인지 확인
        cycleCountRepository.findByLocationIdAndStatus(locationId, CycleCount.CycleCountStatus.IN_PROGRESS)
            .ifPresent(cc -> {
                throw new AdjustmentException("실사가 이미 진행 중인 로케이션입니다: " + locationId);
            });

        // 실사 시작: 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 실사 세션 생성
        CycleCount cycleCount = CycleCount.builder()
            .locationId(locationId)
            .status(CycleCount.CycleCountStatus.IN_PROGRESS)
            .startedBy(request.getStartedBy())
            .startedAt(OffsetDateTime.now())
            .build();

        cycleCount = cycleCountRepository.save(cycleCount);

        return mapToCycleCountResponse(cycleCount);
    }

    /**
     * 실사 완료
     */
    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId, CompleteCycleCountRequest request) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
            .orElseThrow(() -> new ResourceNotFoundException("CycleCount not found: " + cycleCountId));

        if (cycleCount.getStatus() != CycleCount.CycleCountStatus.IN_PROGRESS) {
            throw new AdjustmentException("실사가 진행 중이 아닙니다");
        }

        // 실사 완료: 로케이션 동결 해제
        Location location = locationRepository.findById(cycleCount.getLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + cycleCount.getLocationId()));

        location.setIsFrozen(false);
        locationRepository.save(location);

        // 실사 세션 완료
        cycleCount.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        cycleCount.setCompletedBy(request.getCompletedBy());
        cycleCount.setCompletedAt(OffsetDateTime.now());
        cycleCount = cycleCountRepository.save(cycleCount);

        return mapToCycleCountResponse(cycleCount);
    }

    /**
     * 재고 조정 생성
     */
    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        // 필수 필드 검증
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new AdjustmentException("조정 사유(reason)는 필수입니다");
        }

        UUID productId = request.getProductId();
        UUID locationId = request.getLocationId();

        // 상품 조회
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        // 로케이션 조회
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + locationId));

        // 시스템 재고 조회 (해당 로케이션의 해당 상품 총 수량)
        List<Inventory> inventories = inventoryRepository.findByLocationId(locationId);
        int systemQty = inventories.stream()
            .filter(inv -> inv.getProductId().equals(productId))
            .mapToInt(Inventory::getQuantity)
            .sum();

        int actualQty = request.getActualQty();
        int difference = actualQty - systemQty;

        // 음수 재고 방지
        if (actualQty < 0) {
            throw new AdjustmentException("실제 수량은 음수가 될 수 없습니다");
        }

        // 차이 비율 계산
        Double differencePct = null;
        if (systemQty != 0) {
            differencePct = Math.abs(difference) * 100.0 / systemQty;
        }

        // 승인 여부 판단
        boolean requiresApproval = false;
        InventoryAdjustment.ApprovalStatus approvalStatus = InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
        String reason = request.getReason();
        List<String> warnings = new ArrayList<>();

        // 1. system_qty = 0인 경우 무조건 승인 필요
        if (systemQty == 0 && difference != 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // 2. HIGH_VALUE: 차이가 0이 아니면 무조건 승인 필요 (자동승인 없음)
        else if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // 3. 카테고리별 임계치 체크
        else if (systemQty != 0 && differencePct != null) {
            double threshold = getThresholdByCategory(product.getCategory());
            if (differencePct > threshold) {
                requiresApproval = true;
                approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
            }
        }

        // 4. 연속 조정 감시 (최근 7일 내 동일 location + product 조정이 2회 이상)
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        List<InventoryAdjustment> recentAdjustments = adjustmentRepository.findRecentAdjustments(
            locationId, productId, sevenDaysAgo
        );

        if (recentAdjustments.size() >= 2) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
            reason = "[연속조정감시] " + reason;
        }

        // 조정 엔티티 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
            .productId(productId)
            .locationId(locationId)
            .systemQty(systemQty)
            .actualQty(actualQty)
            .difference(difference)
            .differencePct(differencePct)
            .reason(reason)
            .requiresApproval(requiresApproval)
            .approvalStatus(approvalStatus)
            .createdBy(request.getCreatedBy())
            .build();

        adjustment = adjustmentRepository.save(adjustment);

        // 자동 승인일 경우 즉시 재고 반영
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(adjustment, product, location, warnings);
        }

        return mapToAdjustmentResponse(adjustment, warnings);
    }

    /**
     * 조정 승인
     */
    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, AdjustmentApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Adjustment not found: " + adjustmentId));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new AdjustmentException("승인 대기 상태가 아닙니다");
        }

        // 승인 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustment = adjustmentRepository.save(adjustment);

        // 상품, 로케이션 조회
        Product product = productRepository.findById(adjustment.getProductId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + adjustment.getProductId()));
        Location location = locationRepository.findById(adjustment.getLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Location not found: " + adjustment.getLocationId()));

        List<String> warnings = new ArrayList<>();

        // 재고 반영
        applyAdjustment(adjustment, product, location, warnings);

        return mapToAdjustmentResponse(adjustment, warnings);
    }

    /**
     * 조정 거부
     */
    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, AdjustmentApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Adjustment not found: " + adjustmentId));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new AdjustmentException("승인 대기 상태가 아닙니다");
        }

        // 거부 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustment = adjustmentRepository.save(adjustment);

        List<String> warnings = new ArrayList<>();
        warnings.add("조정이 거부되었습니다. 재실사가 필요합니다.");

        return mapToAdjustmentResponse(adjustment, warnings);
    }

    /**
     * 조정 상세 조회
     */
    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Adjustment not found: " + adjustmentId));

        return mapToAdjustmentResponse(adjustment, new ArrayList<>());
    }

    /**
     * 조정 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> listAdjustments() {
        List<InventoryAdjustment> adjustments = adjustmentRepository.findAll();
        return adjustments.stream()
            .map(adj -> mapToAdjustmentResponse(adj, new ArrayList<>()))
            .toList();
    }

    // ========== Private Helper Methods ==========

    /**
     * 재고 반영 (승인 또는 자동승인 시)
     */
    private void applyAdjustment(InventoryAdjustment adjustment, Product product, Location location, List<String> warnings) {
        int difference = adjustment.getDifference();

        // 재고 반영: location의 재고를 조정
        List<Inventory> inventories = inventoryRepository.findByLocationId(adjustment.getLocationId());
        Inventory targetInventory = inventories.stream()
            .filter(inv -> inv.getProductId().equals(adjustment.getProductId()))
            .findFirst()
            .orElse(null);

        if (targetInventory != null) {
            // 기존 재고가 있는 경우
            int newQty = targetInventory.getQuantity() + difference;
            if (newQty < 0) {
                throw new AdjustmentException("조정 후 재고가 음수가 될 수 없습니다");
            }
            targetInventory.setQuantity(newQty);
            inventoryRepository.save(targetInventory);
            adjustment.setInventoryId(targetInventory.getInventoryId());
        } else {
            // 기존 재고가 없는 경우 (system_qty=0이었던 경우)
            if (adjustment.getActualQty() > 0) {
                Inventory newInventory = Inventory.builder()
                    .productId(adjustment.getProductId())
                    .locationId(adjustment.getLocationId())
                    .quantity(adjustment.getActualQty())
                    .receivedAt(OffsetDateTime.now())
                    .isExpired(false)
                    .build();
                newInventory = inventoryRepository.save(newInventory);
                adjustment.setInventoryId(newInventory.getInventoryId());
            }
        }

        // locations.current_qty 갱신
        location.setCurrentQty(location.getCurrentQty() + difference);
        locationRepository.save(location);

        // HIGH_VALUE 전수 검증: audit_logs 기록
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            AuditLog auditLog = AuditLog.builder()
                .eventType("HIGH_VALUE_ADJUSTMENT")
                .entityType("inventory_adjustment")
                .entityId(adjustment.getAdjustmentId())
                .details(String.format(
                    "{\"system_qty\": %d, \"actual_qty\": %d, \"difference\": %d, \"approved_by\": \"%s\"}",
                    adjustment.getSystemQty(),
                    adjustment.getActualQty(),
                    difference,
                    adjustment.getApprovedBy() != null ? adjustment.getApprovedBy() : "auto"
                ))
                .build();
            auditLogRepository.save(auditLog);

            warnings.add("고가품 조정이 감사 로그에 기록되었습니다. 해당 로케이션 전체 재실사를 권고합니다.");
        }

        // 안전재고 체크 (조정 반영 후)
        checkSafetyStockAndReorder(adjustment.getProductId(), warnings);
    }

    /**
     * 안전재고 체크 및 자동 재발주 트리거
     */
    private void checkSafetyStockAndReorder(UUID productId, List<String> warnings) {
        // 전체 가용 재고 합산 (is_expired=false)
        List<Inventory> allInventories = inventoryRepository.findByProductId(productId);
        int totalAvailableQty = allInventories.stream()
            .filter(inv -> !inv.getIsExpired())
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 안전재고 규칙 조회
        safetyStockRuleRepository.findByProductId(productId).ifPresent(rule -> {
            if (totalAvailableQty <= rule.getMinQty()) {
                // 자동 재발주 기록
                AutoReorderLog log = AutoReorderLog.builder()
                    .productId(productId)
                    .triggerReason("SAFETY_STOCK_TRIGGER")
                    .currentQty(totalAvailableQty)
                    .reorderQty(rule.getReorderQty())
                    .build();
                autoReorderLogRepository.save(log);

                warnings.add("안전재고 미달로 자동 재발주가 기록되었습니다.");
            }
        });
    }

    /**
     * 카테고리별 자동승인 임계치 반환
     */
    private double getThresholdByCategory(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> THRESHOLD_GENERAL;
            case FRESH -> THRESHOLD_FRESH;
            case HAZMAT -> THRESHOLD_HAZMAT;
            case HIGH_VALUE -> 0.0; // 자동승인 없음
        };
    }

    private CycleCountResponse mapToCycleCountResponse(CycleCount cycleCount) {
        return CycleCountResponse.builder()
            .cycleCountId(cycleCount.getCycleCountId())
            .locationId(cycleCount.getLocationId())
            .status(cycleCount.getStatus())
            .startedBy(cycleCount.getStartedBy())
            .completedBy(cycleCount.getCompletedBy())
            .startedAt(cycleCount.getStartedAt())
            .completedAt(cycleCount.getCompletedAt())
            .createdAt(cycleCount.getCreatedAt())
            .build();
    }

    private InventoryAdjustmentResponse mapToAdjustmentResponse(InventoryAdjustment adjustment, List<String> warnings) {
        return InventoryAdjustmentResponse.builder()
            .adjustmentId(adjustment.getAdjustmentId())
            .productId(adjustment.getProductId())
            .locationId(adjustment.getLocationId())
            .inventoryId(adjustment.getInventoryId())
            .systemQty(adjustment.getSystemQty())
            .actualQty(adjustment.getActualQty())
            .difference(adjustment.getDifference())
            .differencePct(adjustment.getDifferencePct())
            .reason(adjustment.getReason())
            .requiresApproval(adjustment.getRequiresApproval())
            .approvalStatus(adjustment.getApprovalStatus())
            .approvedBy(adjustment.getApprovedBy())
            .approvedAt(adjustment.getApprovedAt())
            .createdBy(adjustment.getCreatedBy())
            .createdAt(adjustment.getCreatedAt())
            .warnings(warnings.isEmpty() ? null : warnings)
            .build();
    }
}
