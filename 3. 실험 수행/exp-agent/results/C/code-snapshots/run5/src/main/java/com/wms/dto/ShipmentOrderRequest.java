package com.wms.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ShipmentOrderRequest {

    private String shipmentNumber;
    private String customerName;
    private Instant requestedAt;
    private List<ShipmentLineRequest> lines;

    public static class ShipmentLineRequest {
        private UUID productId;
        private Integer requestedQty;

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }

        public Integer getRequestedQty() { return requestedQty; }
        public void setRequestedQty(Integer requestedQty) { this.requestedQty = requestedQty; }
    }

    // Getters and Setters
    public String getShipmentNumber() { return shipmentNumber; }
    public void setShipmentNumber(String shipmentNumber) { this.shipmentNumber = shipmentNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public List<ShipmentLineRequest> getLines() { return lines; }
    public void setLines(List<ShipmentLineRequest> lines) { this.lines = lines; }
}
