package com.wms.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ShipmentOrderResponse {

    private UUID shipmentId;
    private String shipmentNumber;
    private String customerName;
    private String status;
    private Instant requestedAt;
    private Instant shippedAt;
    private List<ShipmentLineResponse> lines;
    private Instant createdAt;
    private Instant updatedAt;

    public static class ShipmentLineResponse {
        private UUID shipmentLineId;
        private UUID productId;
        private String productName;
        private String productSku;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;

        // Getters and Setters
        public UUID getShipmentLineId() { return shipmentLineId; }
        public void setShipmentLineId(UUID shipmentLineId) { this.shipmentLineId = shipmentLineId; }

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public String getProductSku() { return productSku; }
        public void setProductSku(String productSku) { this.productSku = productSku; }

        public Integer getRequestedQty() { return requestedQty; }
        public void setRequestedQty(Integer requestedQty) { this.requestedQty = requestedQty; }

        public Integer getPickedQty() { return pickedQty; }
        public void setPickedQty(Integer pickedQty) { this.pickedQty = pickedQty; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    // Getters and Setters
    public UUID getShipmentId() { return shipmentId; }
    public void setShipmentId(UUID shipmentId) { this.shipmentId = shipmentId; }

    public String getShipmentNumber() { return shipmentNumber; }
    public void setShipmentNumber(String shipmentNumber) { this.shipmentNumber = shipmentNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getShippedAt() { return shippedAt; }
    public void setShippedAt(Instant shippedAt) { this.shippedAt = shippedAt; }

    public List<ShipmentLineResponse> getLines() { return lines; }
    public void setLines(List<ShipmentLineResponse> lines) { this.lines = lines; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
