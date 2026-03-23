package com.wms.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class InventoryAdjustmentResponse {
    private UUID id;
    private UUID inventoryId;
    private UUID locationId;
    private UUID productId;
    private Integer systemQty;
    private Integer actualQty;
    private Integer differenceQty;
    private String reason;
    private String approvalStatus;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private String rejectionReason;
    private OffsetDateTime createdAt;
}
