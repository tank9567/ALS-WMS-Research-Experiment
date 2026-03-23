package com.wms.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class InboundReceiptRequest {

    private UUID poId;
    private String receivedBy;
    private List<InboundReceiptLineRequest> lines;

    // Getters and Setters
    public UUID getPoId() {
        return poId;
    }

    public void setPoId(UUID poId) {
        this.poId = poId;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(String receivedBy) {
        this.receivedBy = receivedBy;
    }

    public List<InboundReceiptLineRequest> getLines() {
        return lines;
    }

    public void setLines(List<InboundReceiptLineRequest> lines) {
        this.lines = lines;
    }

    public static class InboundReceiptLineRequest {
        private UUID productId;
        private UUID locationId;
        private Integer quantity;
        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;

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

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public String getLotNumber() {
            return lotNumber;
        }

        public void setLotNumber(String lotNumber) {
            this.lotNumber = lotNumber;
        }

        public LocalDate getExpiryDate() {
            return expiryDate;
        }

        public void setExpiryDate(LocalDate expiryDate) {
            this.expiryDate = expiryDate;
        }

        public LocalDate getManufactureDate() {
            return manufactureDate;
        }

        public void setManufactureDate(LocalDate manufactureDate) {
            this.manufactureDate = manufactureDate;
        }
    }
}
