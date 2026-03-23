package com.wms.dto;

import com.wms.enums.AdjustmentApprovalStatus;
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
    private UUID inventoryId;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID locationId;
    private String locationCode;
    private Integer systemQty;
    private Integer actualQty;
    private Integer difference;
    private String reason;
    private AdjustmentApprovalStatus approvalStatus;
    private OffsetDateTime approvedAt;
    private String approvedBy;
    private OffsetDateTime createdAt;
}
