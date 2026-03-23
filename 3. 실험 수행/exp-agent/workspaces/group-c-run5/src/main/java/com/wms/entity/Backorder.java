package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backorders")
public class Backorder {

    @Id
    @Column(name = "backorder_id")
    private UUID backorderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_line_id", nullable = false)
    private ShipmentOrderLine shipmentLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "shortage_qty", nullable = false)
    private Integer shortageQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackorderStatus status = BackorderStatus.OPEN;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    public enum BackorderStatus {
        OPEN, FULFILLED, CANCELLED
    }

    @PrePersist
    protected void onCreate() {
        if (backorderId == null) {
            backorderId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getBackorderId() { return backorderId; }
    public void setBackorderId(UUID backorderId) { this.backorderId = backorderId; }

    public ShipmentOrderLine getShipmentLine() { return shipmentLine; }
    public void setShipmentLine(ShipmentOrderLine shipmentLine) { this.shipmentLine = shipmentLine; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getShortageQty() { return shortageQty; }
    public void setShortageQty(Integer shortageQty) { this.shortageQty = shortageQty; }

    public BackorderStatus getStatus() { return status; }
    public void setStatus(BackorderStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getFulfilledAt() { return fulfilledAt; }
    public void setFulfilledAt(Instant fulfilledAt) { this.fulfilledAt = fulfilledAt; }
}
