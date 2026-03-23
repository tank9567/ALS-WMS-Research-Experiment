package com.wms.service;

import com.wms.dto.AdjustmentApprovalRequest;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.*;
import com.wms.enums.AdjustmentApprovalStatus;
import com.wms.enums.ProductCategory;
import com.wms.exception.WmsException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public InventoryAdjustmentResponse createAdjustment(InventoryAdjustmentRequest request) {
        // Validate reason
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new WmsException("Adjustment reason is required");
        }

        // Validate inventory
        Inventory inventory = inventoryRepository.findById(request.getInventoryId())
                .orElseThrow(() -> new WmsException("Inventory not found"));

        Product product = inventory.getProduct();
        Location location = inventory.getLocation();

        int systemQty = inventory.getQuantity();
        int actualQty = request.getActualQty();
        int difference = actualQty - systemQty;

        String reason = request.getReason();

        // Determine approval status based on category and adjustment percentage
        boolean isHighValue = product.getCategory() == ProductCategory.HIGH_VALUE;
        double adjustmentPercentage = systemQty > 0 ? Math.abs((double) difference / systemQty) * 100 : 0;

        AdjustmentApprovalStatus approvalStatus;
        OffsetDateTime approvedAt = null;
        String approvedBy = null;

        if (isHighValue && adjustmentPercentage > 5.0) {
            // High-value items with >5% adjustment require manual approval
            approvalStatus = AdjustmentApprovalStatus.PENDING;
        } else {
            // Auto-approve for: non-high-value items OR high-value items with ≤5% adjustment
            approvalStatus = AdjustmentApprovalStatus.AUTO_APPROVED;
            approvedAt = OffsetDateTime.now();
            approvedBy = "SYSTEM";
        }

        // Create adjustment record
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .inventory(inventory)
                .location(location)
                .product(product)
                .systemQty(systemQty)
                .actualQty(actualQty)
                .difference(difference)
                .reason(reason)
                .approvalStatus(approvalStatus)
                .approvedAt(approvedAt)
                .approvedBy(approvedBy)
                .build();

        // Apply adjustment immediately if auto-approved
        if (approvalStatus == AdjustmentApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(adjustment);
        }

        adjustment = adjustmentRepository.save(adjustment);

        // HIGH_VALUE audit logging
        if (product.getCategory() == ProductCategory.HIGH_VALUE && difference != 0) {
            createHighValueAuditLog(adjustment);
        }

        return mapToResponse(adjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse approveAdjustment(UUID adjustmentId, AdjustmentApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new WmsException("Adjustment not found"));

        if (adjustment.getApprovalStatus() != AdjustmentApprovalStatus.PENDING) {
            throw new WmsException("Adjustment is not in pending status");
        }

        adjustment.setApprovalStatus(AdjustmentApprovalStatus.APPROVED);
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustment.setApprovedBy(request.getApprovedBy());

        applyAdjustment(adjustment);

        adjustment = adjustmentRepository.save(adjustment);

        return mapToResponse(adjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse rejectAdjustment(UUID adjustmentId, AdjustmentApprovalRequest request) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new WmsException("Adjustment not found"));

        if (adjustment.getApprovalStatus() != AdjustmentApprovalStatus.PENDING) {
            throw new WmsException("Adjustment is not in pending status");
        }

        adjustment.setApprovalStatus(AdjustmentApprovalStatus.REJECTED);
        adjustment.setApprovedAt(OffsetDateTime.now());
        adjustment.setApprovedBy(request.getApprovedBy());

        adjustment = adjustmentRepository.save(adjustment);

        return mapToResponse(adjustment);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new WmsException("Adjustment not found"));

        return mapToResponse(adjustment);
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> listAdjustments() {
        return adjustmentRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getAdjustmentsByProductId(UUID productId) {
        return adjustmentRepository.findByProductId(productId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getAdjustmentsByLocationId(UUID locationId) {
        return adjustmentRepository.findByLocationId(locationId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> getAdjustmentsByDateRange(OffsetDateTime startDate, OffsetDateTime endDate) {
        return adjustmentRepository.findByCreatedAtBetween(startDate, endDate).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public int deleteOldAdjustments(OffsetDateTime cutoffDate) {
        List<InventoryAdjustment> oldAdjustments = adjustmentRepository.findByCreatedAtBefore(cutoffDate);

        if (oldAdjustments.isEmpty()) {
            return 0;
        }

        adjustmentRepository.deleteAll(oldAdjustments);
        return oldAdjustments.size();
    }


    private void applyAdjustment(InventoryAdjustment adjustment) {
        Inventory inventory = adjustment.getInventory();
        Location location = adjustment.getLocation();

        int oldQty = inventory.getQuantity();
        int newQty = adjustment.getActualQty();
        int difference = newQty - oldQty;

        // Update inventory
        inventory.setQuantity(newQty);
        inventoryRepository.save(inventory);

        // Update location current quantity
        location.setCurrentQty(location.getCurrentQty() + difference);
        locationRepository.save(location);

        // Check safety stock
        checkSafetyStock(adjustment.getProduct());
    }

    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductId(product.getId())
                .orElse(null);

        if (rule == null) {
            return;
        }

        // Calculate total available inventory
        List<Inventory> inventories = inventoryRepository.findByProductIdAndExpiredFalse(product.getId());
        int totalQty = inventories.stream().mapToInt(Inventory::getQuantity).sum();

        if (totalQty <= rule.getMinQty()) {
            // Create auto reorder log
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason("SAFETY_STOCK_TRIGGER")
                    .requestedQty(rule.getReorderQty())
                    .build();

            autoReorderLogRepository.save(log);
        }
    }

    private void createHighValueAuditLog(InventoryAdjustment adjustment) {
        Map<String, Object> details = new HashMap<>();
        details.put("adjustmentId", adjustment.getId().toString());
        details.put("productId", adjustment.getProduct().getId().toString());
        details.put("locationId", adjustment.getLocation().getId().toString());
        details.put("systemQty", adjustment.getSystemQty());
        details.put("actualQty", adjustment.getActualQty());
        details.put("difference", adjustment.getDifference());
        details.put("reason", adjustment.getReason());

        AuditLog auditLog = AuditLog.builder()
                .entityType("InventoryAdjustment")
                .entityId(adjustment.getId())
                .action("HIGH_VALUE_ADJUSTMENT")
                .performedBy(adjustment.getApprovedBy() != null ? adjustment.getApprovedBy() : "SYSTEM")
                .details(details)
                .build();

        auditLogRepository.save(auditLog);
    }

    private InventoryAdjustmentResponse mapToResponse(InventoryAdjustment adjustment) {
        return InventoryAdjustmentResponse.builder()
                .id(adjustment.getId())
                .inventoryId(adjustment.getInventory().getId())
                .productId(adjustment.getProduct().getId())
                .productSku(adjustment.getProduct().getSku())
                .productName(adjustment.getProduct().getName())
                .locationId(adjustment.getLocation().getId())
                .locationCode(adjustment.getLocation().getCode())
                .systemQty(adjustment.getSystemQty())
                .actualQty(adjustment.getActualQty())
                .difference(adjustment.getDifference())
                .reason(adjustment.getReason())
                .approvalStatus(adjustment.getApprovalStatus())
                .approvedAt(adjustment.getApprovedAt())
                .approvedBy(adjustment.getApprovedBy())
                .createdAt(adjustment.getCreatedAt())
                .build();
    }
}
