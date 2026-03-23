package com.wms.service;

import com.wms.dto.AdjustmentApprovalRequest;
import com.wms.dto.AdjustmentCreateRequest;
import com.wms.dto.CycleCountStartRequest;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final CycleCountRepository cycleCountRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    /**
     * 실사 시작
     * ALS-WMS-ADJ-002: 실사 동결 규칙
     */
    @Transactional
    public Map<String, Object> startCycleCount(CycleCountStartRequest request) {
        Location location = locationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + request.getLocationId()));

        // 이미 진행 중인 실사가 있는지 확인
        cycleCountRepository.findByLocation_LocationIdAndStatus(location.getLocationId(), CycleCount.Status.IN_PROGRESS)
            .ifPresent(cc -> {
                throw new IllegalStateException("해당 로케이션에 이미 진행 중인 실사가 있습니다");
            });

        // 실사 동결: is_frozen = true 설정
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 실사 세션 생성
        CycleCount cycleCount = CycleCount.builder()
            .location(location)
            .status(CycleCount.Status.IN_PROGRESS)
            .startedBy(request.getStartedBy())
            .notes(request.getNotes())
            .build();
        cycleCountRepository.save(cycleCount);

        Map<String, Object> response = new HashMap<>();
        response.put("cycleCountId", cycleCount.getCycleCountId());
        response.put("locationId", location.getLocationId());
        response.put("locationCode", location.getCode());
        response.put("status", cycleCount.getStatus());
        response.put("isFrozen", location.getIsFrozen());
        response.put("message", "실사가 시작되었습니다. 해당 로케이션은 동결되었습니다.");

        return response;
    }

    /**
     * 실사 완료
     * ALS-WMS-ADJ-002: 실사 동결 해제
     */
    @Transactional
    public Map<String, Object> completeCycleCount(UUID cycleCountId) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
            .orElseThrow(() -> new IllegalArgumentException("실사 세션을 찾을 수 없습니다: " + cycleCountId));

        if (cycleCount.getStatus() == CycleCount.Status.COMPLETED) {
            throw new IllegalStateException("이미 완료된 실사입니다");
        }

        // 실사 완료 처리
        cycleCount.setStatus(CycleCount.Status.COMPLETED);
        cycleCount.setCompletedAt(OffsetDateTime.now());
        cycleCountRepository.save(cycleCount);

        // 실사 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        Map<String, Object> response = new HashMap<>();
        response.put("cycleCountId", cycleCount.getCycleCountId());
        response.put("locationId", location.getLocationId());
        response.put("status", cycleCount.getStatus());
        response.put("isFrozen", location.getIsFrozen());
        response.put("message", "실사가 완료되었습니다. 로케이션 동결이 해제되었습니다.");

        return response;
    }

    /**
     * 재고 조정 생성
     * ALS-WMS-ADJ-002: 카테고리별 자동승인 임계치, 연속 조정 감시, 고가품 전수 검증
     */
    @Transactional
    public Map<String, Object> createAdjustment(AdjustmentCreateRequest request) {
        // 필수 필드 검증
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("조정 사유(reason)는 필수입니다");
        }

        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + request.getProductId()));

        Location location = locationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + request.getLocationId()));

        // 시스템 재고 수량 계산
        List<Inventory> inventories = inventoryRepository.findByProduct(product);
        int systemQty = inventories.stream()
            .filter(inv -> inv.getLocation().getLocationId().equals(location.getLocationId()))
            .mapToInt(Inventory::getQuantity)
            .sum();

        int actualQty = request.getActualQty();
        int difference = actualQty - systemQty;

        // 음수 재고 방지
        if (actualQty < 0) {
            throw new IllegalArgumentException("실제 수량은 0 이상이어야 합니다");
        }

        // system_qty = 0인 경우 무조건 승인 필요
        boolean requiresApproval = false;
        InventoryAdjustment.ApprovalStatus approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        String reason = request.getReason();

        if (difference == 0) {
            // 차이 없음 - 자동 승인
            approvalStatus = InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
        } else if (systemQty == 0) {
            // system_qty = 0이면 무조건 승인 필요
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        } else {
            // 차이 비율 계산
            double diffPct = Math.abs(difference) * 100.0 / systemQty;

            // 연속 조정 감시 (최근 7일 내 2회 이상)
            OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
            List<InventoryAdjustment> recentAdjustments = adjustmentRepository.findRecentAdjustments(
                product.getProductId(), location.getLocationId(), sevenDaysAgo
            );

            boolean consecutiveAdjustment = recentAdjustments.size() >= 1; // 기존 1회 이상이면 이번이 2회째

            if (consecutiveAdjustment) {
                // 연속 조정 감시 발동 - 무조건 승인 필요
                requiresApproval = true;
                approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
                reason = "[연속조정감시] " + reason;
            } else {
                // 카테고리별 자동승인 임계치 적용
                double threshold = getCategoryThreshold(product.getCategory());

                if (product.getCategory() == Product.Category.HIGH_VALUE) {
                    // HIGH_VALUE는 차이가 있으면 무조건 승인 필요
                    requiresApproval = true;
                    approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
                } else if (diffPct <= threshold) {
                    // 임계치 이하 - 자동 승인
                    approvalStatus = InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
                } else {
                    // 임계치 초과 - 승인 필요
                    requiresApproval = true;
                    approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
                }
            }
        }

        // 조정 레코드 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
            .product(product)
            .location(location)
            .systemQty(systemQty)
            .actualQty(actualQty)
            .difference(difference)
            .reason(reason)
            .requiresApproval(requiresApproval)
            .approvalStatus(approvalStatus)
            .createdBy(request.getCreatedBy())
            .build();

        if (request.getCycleCountId() != null) {
            CycleCount cycleCount = cycleCountRepository.findById(request.getCycleCountId())
                .orElseThrow(() -> new IllegalArgumentException("실사 세션을 찾을 수 없습니다"));
            adjustment.setCycleCount(cycleCount);
        }

        adjustmentRepository.save(adjustment);

        // 자동 승인인 경우 즉시 재고 반영
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(adjustment);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("adjustmentId", adjustment.getAdjustmentId());
        response.put("productId", product.getProductId());
        response.put("locationId", location.getLocationId());
        response.put("systemQty", systemQty);
        response.put("actualQty", actualQty);
        response.put("difference", difference);
        response.put("requiresApproval", requiresApproval);
        response.put("approvalStatus", approvalStatus);
        response.put("reason", reason);

        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            response.put("message", "조정이 자동 승인되어 재고에 반영되었습니다");
        } else {
            response.put("message", "관리자 승인이 필요합니다");
        }

        return response;
    }

    /**
     * 재고 조정 승인
     * ALS-WMS-ADJ-002: 고가품 감사 로그, 안전재고 체크
     */
    @Transactional
    public Map<String, Object> approveAdjustment(UUID adjustmentId, AdjustmentApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new IllegalArgumentException("조정 레코드를 찾을 수 없습니다: " + adjustmentId));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new IllegalStateException("승인 대기 상태가 아닙니다: " + adjustment.getApprovalStatus());
        }

        // 승인 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustmentRepository.save(adjustment);

        // 재고 반영
        applyAdjustment(adjustment);

        Map<String, Object> response = new HashMap<>();
        response.put("adjustmentId", adjustment.getAdjustmentId());
        response.put("approvalStatus", adjustment.getApprovalStatus());
        response.put("approvedBy", adjustment.getApprovedBy());
        response.put("message", "조정이 승인되어 재고에 반영되었습니다");

        // HIGH_VALUE 카테고리인 경우 감사 로그 기록
        if (adjustment.getProduct().getCategory() == Product.Category.HIGH_VALUE) {
            AuditLog auditLog = AuditLog.builder()
                .entityType("inventory_adjustment")
                .entityId(adjustment.getAdjustmentId())
                .action("HIGH_VALUE_ADJUSTMENT")
                .description(String.format("고가품 조정 승인: system_qty=%d, actual_qty=%d, difference=%d, approved_by=%s",
                    adjustment.getSystemQty(), adjustment.getActualQty(), adjustment.getDifference(), adjustment.getApprovedBy()))
                .createdBy(adjustment.getApprovedBy())
                .build();
            auditLogRepository.save(auditLog);

            response.put("warning", "해당 로케이션 전체 재실사를 권고합니다");
        }

        return response;
    }

    /**
     * 재고 조정 거부
     */
    @Transactional
    public Map<String, Object> rejectAdjustment(UUID adjustmentId, AdjustmentApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new IllegalArgumentException("조정 레코드를 찾을 수 없습니다: " + adjustmentId));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new IllegalStateException("승인 대기 상태가 아닙니다: " + adjustment.getApprovalStatus());
        }

        // 거부 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustmentRepository.save(adjustment);

        Map<String, Object> response = new HashMap<>();
        response.put("adjustmentId", adjustment.getAdjustmentId());
        response.put("approvalStatus", adjustment.getApprovalStatus());
        response.put("message", "조정이 거부되었습니다. 재실사가 필요합니다.");

        return response;
    }

    /**
     * 조정 상세 조회
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new IllegalArgumentException("조정 레코드를 찾을 수 없습니다: " + adjustmentId));

        Map<String, Object> response = new HashMap<>();
        response.put("adjustmentId", adjustment.getAdjustmentId());
        response.put("productId", adjustment.getProduct().getProductId());
        response.put("productName", adjustment.getProduct().getName());
        response.put("locationId", adjustment.getLocation().getLocationId());
        response.put("locationCode", adjustment.getLocation().getCode());
        response.put("systemQty", adjustment.getSystemQty());
        response.put("actualQty", adjustment.getActualQty());
        response.put("difference", adjustment.getDifference());
        response.put("reason", adjustment.getReason());
        response.put("requiresApproval", adjustment.getRequiresApproval());
        response.put("approvalStatus", adjustment.getApprovalStatus());
        response.put("approvedBy", adjustment.getApprovedBy());
        response.put("approvedAt", adjustment.getApprovedAt());
        response.put("createdBy", adjustment.getCreatedBy());
        response.put("createdAt", adjustment.getCreatedAt());

        return response;
    }

    /**
     * 조정 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InventoryAdjustment> getAdjustments() {
        return adjustmentRepository.findAll();
    }

    /**
     * 재고 조정 반영 (내부 로직)
     */
    private void applyAdjustment(InventoryAdjustment adjustment) {
        Product product = adjustment.getProduct();
        Location location = adjustment.getLocation();
        int difference = adjustment.getDifference();

        // 재고 반영
        List<Inventory> inventories = inventoryRepository.findByProduct(product);
        Inventory targetInventory = inventories.stream()
            .filter(inv -> inv.getLocation().getLocationId().equals(location.getLocationId()))
            .findFirst()
            .orElse(null);

        if (difference > 0) {
            // 증가
            if (targetInventory == null) {
                // 새 재고 생성
                targetInventory = Inventory.builder()
                    .product(product)
                    .location(location)
                    .quantity(difference)
                    .receivedAt(OffsetDateTime.now())
                    .build();
            } else {
                targetInventory.setQuantity(targetInventory.getQuantity() + difference);
            }
            inventoryRepository.save(targetInventory);

            // 로케이션 current_qty 증가
            location.setCurrentQty(location.getCurrentQty() + difference);
            locationRepository.save(location);

        } else if (difference < 0) {
            // 감소
            if (targetInventory == null) {
                throw new IllegalStateException("재고가 존재하지 않습니다");
            }

            int newQty = targetInventory.getQuantity() + difference; // difference는 음수
            if (newQty < 0) {
                throw new IllegalStateException("재고가 음수가 될 수 없습니다");
            }

            targetInventory.setQuantity(newQty);
            inventoryRepository.save(targetInventory);

            // 로케이션 current_qty 감소
            location.setCurrentQty(location.getCurrentQty() + difference); // difference는 음수
            locationRepository.save(location);
        }

        // 안전재고 체크
        checkSafetyStock(product);
    }

    /**
     * 안전재고 체크 및 자동 재발주
     */
    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductId(product.getProductId())
            .orElse(null);

        if (rule == null) {
            return; // 안전재고 규칙이 없으면 스킵
        }

        // 전체 가용 재고 합산 (is_expired=false만)
        List<Inventory> inventories = inventoryRepository.findByProduct(product);
        int totalAvailable = inventories.stream()
            .filter(inv -> !inv.getIsExpired())
            .mapToInt(Inventory::getQuantity)
            .sum();

        if (totalAvailable < rule.getMinQty()) {
            // 안전재고 미달 - 자동 재발주 기록
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                .product(product)
                .triggerReason("SAFETY_STOCK_TRIGGER")
                .currentQty(totalAvailable)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .build();
            autoReorderLogRepository.save(reorderLog);
        }
    }

    /**
     * 카테고리별 자동승인 임계치 반환
     */
    private double getCategoryThreshold(Product.Category category) {
        return switch (category) {
            case GENERAL -> 5.0;
            case FRESH -> 3.0;
            case HAZMAT -> 1.0;
            case HIGH_VALUE -> 0.0; // 실제로는 별도 처리되므로 사용되지 않음
        };
    }
}
