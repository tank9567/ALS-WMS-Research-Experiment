package com.wms.dto;

import com.wms.entity.InventoryAdjustment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentResponse {
    private UUID id;
    private UUID cycleCountId;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID locationId;
    private String locationCode;
    private UUID inventoryId;
    private Integer systemQty;
    private Integer actualQty;
    private Integer difference;
    private String reason;
    private String approvalStatus;
    private String approvedBy;
    private Instant approvedAt;
    private String rejectionReason;
    private Instant createdAt;

    public static InventoryAdjustmentResponse from(InventoryAdjustment adjustment) {
        return InventoryAdjustmentResponse.builder()
                .id(adjustment.getId())
                .cycleCountId(adjustment.getCycleCount() != null ? adjustment.getCycleCount().getId() : null)
                .productId(adjustment.getProduct().getId())
                .productSku(adjustment.getProduct().getSku())
                .productName(adjustment.getProduct().getName())
                .locationId(adjustment.getLocation().getId())
                .locationCode(adjustment.getLocation().getCode())
                .inventoryId(adjustment.getInventory() != null ? adjustment.getInventory().getId() : null)
                .systemQty(adjustment.getSystemQty())
                .actualQty(adjustment.getActualQty())
                .difference(adjustment.getDifference())
                .reason(adjustment.getReason())
                .approvalStatus(adjustment.getApprovalStatus().name())
                .approvedBy(adjustment.getApprovedBy())
                .approvedAt(adjustment.getApprovedAt())
                .rejectionReason(adjustment.getRejectionReason())
                .createdAt(adjustment.getCreatedAt())
                .build();
    }
}
