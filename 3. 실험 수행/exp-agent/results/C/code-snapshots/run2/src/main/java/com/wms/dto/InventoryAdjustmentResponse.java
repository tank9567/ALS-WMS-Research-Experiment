package com.wms.dto;

import com.wms.entity.InventoryAdjustment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentResponse {
    private UUID adjustmentId;
    private UUID productId;
    private UUID locationId;
    private UUID inventoryId;
    private Integer systemQty;
    private Integer actualQty;
    private Integer difference;
    private Double differencePct;
    private String reason;
    private Boolean requiresApproval;
    private InventoryAdjustment.ApprovalStatus approvalStatus;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private String createdBy;
    private OffsetDateTime createdAt;
    private List<String> warnings;
}
