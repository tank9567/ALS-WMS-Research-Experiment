package com.wms.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "backorders")
public class Backorder {

    @Id
    @Column(name = "backorder_id")
    private UUID backorderId;

    @Column(name = "shipment_line_id", nullable = false)
    private UUID shipmentLineId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "shortage_qty", nullable = false)
    private Integer shortageQty;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "open";

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "fulfilled_at")
    private OffsetDateTime fulfilledAt;

    @PrePersist
    protected void onCreate() {
        if (backorderId == null) {
            backorderId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    // Getters and Setters
    public UUID getBackorderId() {
        return backorderId;
    }

    public void setBackorderId(UUID backorderId) {
        this.backorderId = backorderId;
    }

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

    public Integer getShortageQty() {
        return shortageQty;
    }

    public void setShortageQty(Integer shortageQty) {
        this.shortageQty = shortageQty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getFulfilledAt() {
        return fulfilledAt;
    }

    public void setFulfilledAt(OffsetDateTime fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
    }
}
