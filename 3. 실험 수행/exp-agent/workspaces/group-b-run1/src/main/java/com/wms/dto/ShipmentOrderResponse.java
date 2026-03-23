package com.wms.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ShipmentOrderResponse {

    private UUID shipmentId;
    private String shipmentNumber;
    private String customerName;
    private String status;
    private OffsetDateTime requestedAt;
    private OffsetDateTime shippedAt;
    private List<ShipmentOrderLineResponse> lines;
    private List<PickDetail> pickDetails;
    private List<BackorderInfo> backorders;

    // Getters and Setters
    public UUID getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(UUID shipmentId) {
        this.shipmentId = shipmentId;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(OffsetDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public OffsetDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(OffsetDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }

    public List<ShipmentOrderLineResponse> getLines() {
        return lines;
    }

    public void setLines(List<ShipmentOrderLineResponse> lines) {
        this.lines = lines;
    }

    public List<PickDetail> getPickDetails() {
        return pickDetails;
    }

    public void setPickDetails(List<PickDetail> pickDetails) {
        this.pickDetails = pickDetails;
    }

    public List<BackorderInfo> getBackorders() {
        return backorders;
    }

    public void setBackorders(List<BackorderInfo> backorders) {
        this.backorders = backorders;
    }

    public static class ShipmentOrderLineResponse {
        private UUID shipmentLineId;
        private UUID productId;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;

        // Getters and Setters
        public UUID getShipmentLineId() {
            return shipmentLineId;
        }

        public void setShipmentLineId(UUID shipmentLineId) {
            this.shipmentLineId = shipmentLineId;
        }

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

        public Integer getPickedQty() {
            return pickedQty;
        }

        public void setPickedQty(Integer pickedQty) {
            this.pickedQty = pickedQty;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class PickDetail {
        private UUID productId;
        private UUID locationId;
        private Integer pickedQty;

        // Getters and Setters
        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public UUID getLocationId() {
            return locationId;
        }

        public void setLocationId(UUID locationId) {
            this.locationId = locationId;
        }

        public Integer getPickedQty() {
            return pickedQty;
        }

        public void setPickedQty(Integer pickedQty) {
            this.pickedQty = pickedQty;
        }
    }

    public static class BackorderInfo {
        private UUID backorderId;
        private UUID productId;
        private Integer shortageQty;

        // Getters and Setters
        public UUID getBackorderId() {
            return backorderId;
        }

        public void setBackorderId(UUID backorderId) {
            this.backorderId = backorderId;
        }

        public UUID getProductId() {
            return productId;
        }

        public void setProductId(UUID productId) {
            this.productId = productId;
        }

        public Integer getShortageQty() {
            return shortageQty;
        }

        public void setShortageQty(Integer shortageQty) {
            this.shortageQty = shortageQty;
        }
    }
}
