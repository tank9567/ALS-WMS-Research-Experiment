package com.wms.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ShipmentOrderRequest {

    private String shipmentNumber;
    private String customerName;
    private OffsetDateTime requestedAt;
    private List<ShipmentOrderLineRequest> lines;

    // Getters and Setters
    public String getShipmentNumber() {
        return shipmentNumber;
    }

    public void setShipmentNumber(String shipmentNumber) {
        this.shipmentNumber = shipmentNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public List<ShipmentOrderLineRequest> getLines() {
        return lines;
    }

    public void setLines(List<ShipmentOrderLineRequest> lines) {
        this.lines = lines;
    }

    public static class ShipmentOrderLineRequest {
        private UUID productId;
        private Integer requestedQty;

        // Getters and Setters
        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public Integer getRequestedQty() {
            return requestedQty;
        }

        public void setRequestedQty(Integer requestedQty) {
            this.requestedQty = requestedQty;
        }
    }
}
