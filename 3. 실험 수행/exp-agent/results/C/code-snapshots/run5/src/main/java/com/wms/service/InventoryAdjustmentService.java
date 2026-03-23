package com.wms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dto.ApprovalRequest;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public InventoryAdjustmentService(
        InventoryAdjustmentRepository adjustmentRepository,
        InventoryRepository inventoryRepository,
        ProductRepository productRepository,
        LocationRepository locationRepository,
        SafetyStockRuleRepository safetyStockRuleRepository,
        AutoReorderLogRepository autoReorderLogRepository,
        AuditLogRepository auditLogRepository,
        ObjectMapper objectMapper
    ) {
        this.adjustmentRepository = adjustmentRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        // 1. 기본 유효성 검사
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException("조정 사유는 필수입니다");
        }

        Product product = productRepository.findById(request.getProductId())
            .orElseThrow(() -> new BusinessException("상품을 찾을 수 없습니다"));

        Location location = locationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new BusinessException("로케이션을 찾을 수 없습니다"));

        // 2. 재고 조회
        Inventory inventory = null;
        int systemQty = 0;

        if (request.getInventoryId() != null) {
            inventory = inventoryRepository.findById(request.getInventoryId())
                .orElseThrow(() -> new BusinessException("재고를 찾을 수 없습니다"));
            systemQty = inventory.getQuantity();
        }

        int actualQty = request.getActualQty();
        int difference = actualQty - systemQty;

        // 3. 음수 재고 방지
        if (actualQty < 0) {
            throw new BusinessException("조정 후 재고는 음수가 될 수 없습니다");
        }

        // 4. 차이 비율 계산
        double diffPct = 0.0;
        if (systemQty > 0) {
            diffPct = Math.abs(difference) * 100.0 / systemQty;
        }

        // 5. 카테고리별 자동승인 임계치
        double threshold = getAutoApprovalThreshold(product.getCategory());

        // 6. 연속 조정 감시
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<InventoryAdjustment> recentAdjustments = adjustmentRepository.findRecentAdjustments(
            product, location, sevenDaysAgo
        );

        boolean hasRecentAdjustments = recentAdjustments.size() >= 1;
        String reason = request.getReason();

        if (hasRecentAdjustments) {
            reason = "[연속조정감시] " + reason;
        }

        // 7. 승인 여부 결정
        boolean requiresApproval = false;
        InventoryAdjustment.ApprovalStatus approvalStatus = InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;

        // system_qty = 0인 경우 무조건 승인 필요
        if (systemQty == 0 && actualQty > 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // HIGH_VALUE는 차이가 0이 아니면 무조건 승인 필요
        else if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // 연속 조정 감시 발동 시 승인 필요
        else if (hasRecentAdjustments) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // 임계치 초과 시 승인 필요
        else if (diffPct > threshold) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }

        // 8. 조정 생성
        InventoryAdjustment adjustment = new InventoryAdjustment();
        adjustment.setProduct(product);
        adjustment.setLocation(location);
        adjustment.setInventory(inventory);
        adjustment.setSystemQty(systemQty);
        adjustment.setActualQty(actualQty);
        adjustment.setDifference(difference);
        adjustment.setReason(reason);
        adjustment.setRequiresApproval(requiresApproval);
        adjustment.setApprovalStatus(approvalStatus);

        // 9. 자동 승인 시 즉시 반영
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(inventory, location, actualQty, systemQty, product);
        }

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);

        // 10. 자동 승인 시 안전재고 체크
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            checkSafetyStock(product, "AUTO_ADJUSTMENT");
        }

        InventoryAdjustmentResponse response = mapToResponse(saved);

        // 11. HIGH_VALUE 경고 추가
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            response.setWarning("해당 로케이션 전체 재실사를 권고합니다");
        }

        return response;
    }

    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, ApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException("조정을 찾을 수 없습니다"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("승인 대기 중인 조정만 승인할 수 있습니다");
        }

        // 승인 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());

        // 재고 반영
        Inventory inventory = adjustment.getInventory();
        Location location = adjustment.getLocation();
        Product product = adjustment.getProduct();

        applyAdjustment(inventory, location, adjustment.getActualQty(), adjustment.getSystemQty(), product);

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);

        // HIGH_VALUE 감사 로그 기록
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE) {
            createAuditLog(adjustment, request.getApprovedBy());
        }

        // 안전재고 체크
        checkSafetyStock(product, "ADJUSTMENT_APPROVED");

        InventoryAdjustmentResponse response = mapToResponse(saved);

        // HIGH_VALUE 경고
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE) {
            response.setWarning("해당 로케이션 전체 재실사를 권고합니다");
        }

        return response;
    }

    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, ApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException("조정을 찾을 수 없습니다"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("승인 대기 중인 조정만 거부할 수 있습니다");
        }

        // 거부 처리 (재고 변동 없음)
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException("조정을 찾을 수 없습니다"));

        return mapToResponse(adjustment);
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getAdjustments() {
        List<InventoryAdjustment> adjustments = adjustmentRepository.findAll();
        return adjustments.stream()
            .map(this::mapToResponse)
            .toList();
    }

    private double getAutoApprovalThreshold(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 5.0;
            case FRESH -> 3.0;
            case HAZMAT -> 1.0;
            case HIGH_VALUE -> 0.0; // 자동 승인 없음
        };
    }

    private void applyAdjustment(Inventory inventory, Location location, int actualQty, int systemQty, Product product) {
        int difference = actualQty - systemQty;

        if (inventory != null) {
            inventory.setQuantity(actualQty);
            inventoryRepository.save(inventory);
        } else if (actualQty > 0) {
            // 새 재고 생성 (system_qty=0이었던 경우)
            Inventory newInventory = new Inventory();
            newInventory.setProduct(product);
            newInventory.setLocation(location);
            newInventory.setQuantity(actualQty);
            newInventory.setReceivedAt(Instant.now());
            inventoryRepository.save(newInventory);
        }

        // 로케이션 현재 수량 갱신
        int newCurrentQty = location.getCurrentQty() + difference;
        if (newCurrentQty < 0) {
            throw new BusinessException("로케이션 현재 수량이 음수가 될 수 없습니다");
        }
        if (newCurrentQty > location.getCapacity()) {
            throw new BusinessException("로케이션 용량을 초과할 수 없습니다");
        }
        location.setCurrentQty(newCurrentQty);
        locationRepository.save(location);
    }

    private void checkSafetyStock(Product product, String triggeredBy) {
        // 전체 가용 재고 계산 (is_expired=false)
        List<Inventory> inventories = inventoryRepository.findByProduct(product);
        int totalStock = inventories.stream()
            .filter(inv -> !inv.getIsExpired())
            .mapToInt(Inventory::getQuantity)
            .sum();

        // 안전재고 규칙 조회
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct(product).orElse(null);
        if (rule == null) {
            return;
        }

        // 안전재고 미달 시 자동 재발주
        if (totalStock < rule.getMinQty()) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProduct(product);
            log.setTriggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER);
            log.setCurrentStock(totalStock);
            log.setMinQty(rule.getMinQty());
            log.setReorderQty(rule.getReorderQty());
            log.setTriggeredBy(triggeredBy);
            autoReorderLogRepository.save(log);
        }
    }

    private void createAuditLog(InventoryAdjustment adjustment, String performedBy) {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("system_qty", adjustment.getSystemQty());
            details.put("actual_qty", adjustment.getActualQty());
            details.put("difference", adjustment.getDifference());
            details.put("approved_by", performedBy);

            AuditLog auditLog = new AuditLog();
            auditLog.setEventType("HIGH_VALUE_ADJUSTMENT");
            auditLog.setEntityType("inventory_adjustment");
            auditLog.setEntityId(adjustment.getAdjustmentId());
            auditLog.setDetails(objectMapper.writeValueAsString(details));
            auditLog.setPerformedBy(performedBy);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            throw new BusinessException("감사 로그 기록 실패: " + e.getMessage());
        }
    }

    private InventoryAdjustmentResponse mapToResponse(InventoryAdjustment adjustment) {
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
        response.setApprovalStatus(adjustment.getApprovalStatus().name());
        response.setRequiresApproval(adjustment.getRequiresApproval());
        response.setApprovedBy(adjustment.getApprovedBy());
        response.setApprovedAt(adjustment.getApprovedAt());
        response.setCreatedAt(adjustment.getCreatedAt());
        return response;
    }
}
