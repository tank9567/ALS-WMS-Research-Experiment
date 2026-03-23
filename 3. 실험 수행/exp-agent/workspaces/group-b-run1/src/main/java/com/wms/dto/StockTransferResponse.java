package com.wms.dto;

import com.wms.entity.StockTransfer;

import java.time.OffsetDateTime;
import java.util.UUID;

public class StockTransferResponse {

    private UUID transferId;
    private UUID productId;
    private String productName;
    private UUID fromLocationId;
    private String fromLocationCode;
    private UUID toLocationId;
    private String toLocationCode;
    private Integer quantity;
    private String lotNumber;
    private String reason;
    private String transferStatus;
    private String transferredBy;
    private String approvedBy;
    private OffsetDateTime transferredAt;

    public static StockTransferResponse from(StockTransfer transfer) {
        StockTransferResponse response = new StockTransferResponse();
        response.transferId = transfer.getTransferId();
        response.productId = transfer.getProduct().getProductId();
        response.productName = transfer.getProduct().getName();
        response.fromLocationId = transfer.getFromLocation().getLocationId();
        response.fromLocationCode = transfer.getFromLocation().getCode();
        response.toLocationId = transfer.getToLocation().getLocationId();
        response.toLocationCode = transfer.getToLocation().getCode();
        response.quantity = transfer.getQuantity();
        response.lotNumber = transfer.getLotNumber();
        response.reason = transfer.getReason();
        response.transferStatus = transfer.getTransferStatus();
        response.transferredBy = transfer.getTransferredBy();
        response.approvedBy = transfer.getApprovedBy();
        response.transferredAt = transfer.getTransferredAt();
        return response;
    }

    // Getters and Setters
    public UUID getTransferId() {
        return transferId;
    }

    public void setTransferId(UUID transferId) {
        this.transferId = transferId;
    }

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public UUID getFromLocationId() {
        return fromLocationId;
    }

    public void setFromLocationId(UUID fromLocationId) {
        this.fromLocationId = fromLocationId;
    }

    public String getFromLocationCode() {
        return fromLocationCode;
    }

    public void setFromLocationCode(String fromLocationCode) {
        this.fromLocationCode = fromLocationCode;
    }

    public UUID getToLocationId() {
        return toLocationId;
    }

    public void setToLocationId(UUID toLocationId) {
        this.toLocationId = toLocationId;
    }

    public String getToLocationCode() {
        return toLocationCode;
    }

    public void setToLocationCode(String toLocationCode) {
        this.toLocationCode = toLocationCode;
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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTransferStatus() {
        return transferStatus;
    }

    public void setTransferStatus(String transferStatus) {
        this.transferStatus = transferStatus;
    }

    public String getTransferredBy() {
        return transferredBy;
    }

    public void setTransferredBy(String transferredBy) {
        this.transferredBy = transferredBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public OffsetDateTime getTransferredAt() {
        return transferredAt;
    }

    public void setTransferredAt(OffsetDateTime transferredAt) {
        this.transferredAt = transferredAt;
    }
}
