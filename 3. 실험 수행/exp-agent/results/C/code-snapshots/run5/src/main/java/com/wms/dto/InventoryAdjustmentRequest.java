package com.wms.dto;

import java.util.UUID;

public class InventoryAdjustmentRequest {
    private UUID productId;
    private UUID locationId;
    private UUID inventoryId;
    private Integer actualQty;
    private String reason;

    // Getters and Setters
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }

    public UUID getInventoryId() { return inventoryId; }
    public void setInventoryId(UUID inventoryId) { this.inventoryId = inventoryId; }

    public Integer getActualQty() { return actualQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
