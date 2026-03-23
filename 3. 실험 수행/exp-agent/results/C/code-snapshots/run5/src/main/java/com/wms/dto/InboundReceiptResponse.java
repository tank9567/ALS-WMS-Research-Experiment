package com.wms.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class InboundReceiptResponse {
    private UUID receiptId;
    private UUID poId;
    private String status;
    private String receivedBy;
    private Instant receivedAt;
    private Instant confirmedAt;
    private List<LineItemResponse> lines;

    public static class LineItemResponse {
        private UUID receiptLineId;
        private UUID productId;
        private UUID locationId;
        private Integer quantity;
        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;

        // Getters and Setters
        public UUID getReceiptLineId() { return receiptLineId; }
        public void setReceiptLineId(UUID receiptLineId) { this.receiptLineId = receiptLineId; }

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }

        public UUID getLocationId() { return locationId; }
        public void setLocationId(UUID locationId) { this.locationId = locationId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public String getLotNumber() { return lotNumber; }
        public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

        public LocalDate getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

        public LocalDate getManufactureDate() { return manufactureDate; }
        public void setManufactureDate(LocalDate manufactureDate) { this.manufactureDate = manufactureDate; }
    }

    // Getters and Setters
    public UUID getReceiptId() { return receiptId; }
    public void setReceiptId(UUID receiptId) { this.receiptId = receiptId; }

    public UUID getPoId() { return poId; }
    public void setPoId(UUID poId) { this.poId = poId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public List<LineItemResponse> getLines() { return lines; }
    public void setLines(List<LineItemResponse> lines) { this.lines = lines; }
}
