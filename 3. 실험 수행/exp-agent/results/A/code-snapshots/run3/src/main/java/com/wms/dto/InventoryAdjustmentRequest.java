package com.wms.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class InventoryAdjustmentRequest {
    private UUID inventoryId;
    private UUID locationId;
    private UUID productId;
    private Integer systemQty;
    private Integer actualQty;
    private String reason;
}
