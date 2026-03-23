package com.wms.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "shipment_order_lines")
public class ShipmentOrderLine {

    @Id
    @Column(name = "shipment_line_id")
    private UUID shipmentLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private ShipmentOrder shipmentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "picked_qty", nullable = false)
    private Integer pickedQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LineStatus status = LineStatus.PENDING;

    public enum LineStatus {
        PENDING, PICKED, PARTIAL, BACKORDERED
    }

    @PrePersist
    protected void onCreate() {
        if (shipmentLineId == null) {
            shipmentLineId = UUID.randomUUID();
        }
    }

    // Getters and Setters
    public UUID getShipmentLineId() { return shipmentLineId; }
    public void setShipmentLineId(UUID shipmentLineId) { this.shipmentLineId = shipmentLineId; }

    public ShipmentOrder getShipmentOrder() { return shipmentOrder; }
    public void setShipmentOrder(ShipmentOrder shipmentOrder) { this.shipmentOrder = shipmentOrder; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getRequestedQty() { return requestedQty; }
    public void setRequestedQty(Integer requestedQty) { this.requestedQty = requestedQty; }

    public Integer getPickedQty() { return pickedQty; }
    public void setPickedQty(Integer pickedQty) { this.pickedQty = pickedQty; }

    public LineStatus getStatus() { return status; }
    public void setStatus(LineStatus status) { this.status = status; }
}
