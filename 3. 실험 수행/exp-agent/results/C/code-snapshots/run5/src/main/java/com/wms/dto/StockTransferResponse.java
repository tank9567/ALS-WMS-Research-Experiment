package com.wms.dto;

import com.wms.entity.StockTransfer;

import java.time.Instant;
import java.util.UUID;

public class StockTransferResponse {

    private UUID transferId;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID fromLocationId;
    private String fromLocationCode;
    private UUID toLocationId;
    private String toLocationCode;
    private Integer quantity;
    private String lotNumber;
    private String transferStatus;
    private String requestedBy;
    private String approvedBy;
    private Instant requestedAt;
    private Instant approvedAt;
    private Instant completedAt;
    private String reason;

    public static StockTransferResponse from(StockTransfer transfer) {
        StockTransferResponse response = new StockTransferResponse();
        response.setTransferId(transfer.getTransferId());
        response.setProductId(transfer.getProduct().getProductId());
        response.setProductSku(transfer.getProduct().getSku());
        response.setProductName(transfer.getProduct().getName());
        response.setFromLocationId(transfer.getFromLocation().getLocationId());
        response.setFromLocationCode(transfer.getFromLocation().getCode());
        response.setToLocationId(transfer.getToLocation().getLocationId());
        response.setToLocationCode(transfer.getToLocation().getCode());
        response.setQuantity(transfer.getQuantity());
        response.setLotNumber(transfer.getLotNumber());
        response.setTransferStatus(transfer.getTransferStatus().name());
        response.setRequestedBy(transfer.getRequestedBy());
        response.setApprovedBy(transfer.getApprovedBy());
        response.setRequestedAt(transfer.getRequestedAt());
        response.setApprovedAt(transfer.getApprovedAt());
        response.setCompletedAt(transfer.getCompletedAt());
        response.setReason(transfer.getReason());
        return response;
    }

    // Getters and Setters
    public UUID getTransferId() { return transferId; }
    public void setTransferId(UUID transferId) { this.transferId = transferId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getProductSku() { return productSku; }
    public void setProductSku(String productSku) { this.productSku = productSku; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public UUID getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(UUID fromLocationId) { this.fromLocationId = fromLocationId; }

    public String getFromLocationCode() { return fromLocationCode; }
    public void setFromLocationCode(String fromLocationCode) { this.fromLocationCode = fromLocationCode; }

    public UUID getToLocationId() { return toLocationId; }
    public void setToLocationId(UUID toLocationId) { this.toLocationId = toLocationId; }

    public String getToLocationCode() { return toLocationCode; }
    public void setToLocationCode(String toLocationCode) { this.toLocationCode = toLocationCode; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public String getTransferStatus() { return transferStatus; }
    public void setTransferStatus(String transferStatus) { this.transferStatus = transferStatus; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
