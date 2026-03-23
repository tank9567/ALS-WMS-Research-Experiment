package com.wms.dto;

import com.wms.entity.InventoryAdjustment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustmentResponse {
    private UUID adjustmentId;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID locationId;
    private String locationCode;
    private Integer systemQty;
    private Integer actualQty;
    private Integer difference;
    private String reason;
    private Boolean requiresApproval;
    private String approvalStatus;
    private String approvedBy;
    private String adjustedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime approvedAt;

    public static InventoryAdjustmentResponse from(InventoryAdjustment adjustment) {
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
