package com.wms.adjustment.dto;

import com.wms.adjustment.entity.InventoryAdjustment;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    private InventoryAdjustment.ApprovalStatus approvalStatus;
    private String approvedBy;
    private String adjustedBy;
    private Instant createdAt;
    private Instant approvedAt;
    private String warning;

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
                .approvalStatus(adjustment.getApprovalStatus())
                .approvedBy(adjustment.getApprovedBy())
                .adjustedBy(adjustment.getAdjustedBy())
                .createdAt(adjustment.getCreatedAt())
                .approvedAt(adjustment.getApprovedAt())
                .build();
    }
}
