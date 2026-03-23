package com.wms.dto;

import com.wms.entity.InventoryAdjustment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentResponse {

    private UUID id;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID locationId;
    private String locationCode;
    private Integer systemQty;
    private Integer actualQty;
    private Integer difference;
    private String reason;
    private InventoryAdjustment.ApprovalStatus approvalStatus;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private UUID cycleCountId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static InventoryAdjustmentResponse from(InventoryAdjustment adjustment) {
        return InventoryAdjustmentResponse.builder()
                .id(adjustment.getId())
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
                .approvedBy(adjustment.getApprovedBy())
                .approvedAt(adjustment.getApprovedAt())
                .cycleCountId(adjustment.getCycleCount() != null ? adjustment.getCycleCount().getId() : null)
                .createdAt(adjustment.getCreatedAt())
                .updatedAt(adjustment.getUpdatedAt())
                .build();
    }
}
