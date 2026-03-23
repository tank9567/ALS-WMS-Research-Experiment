package com.wms.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "shipment_order_lines")
public class ShipmentOrderLine {

    @Id
    @Column(name = "shipment_line_id")
    private UUID shipmentLineId;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "picked_qty", nullable = false)
    private Integer pickedQty = 0;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    @PrePersist
    protected void onCreate() {
        if (shipmentLineId == null) {
            shipmentLineId = UUID.randomUUID();
        }
    }

    // Getters and Setters
    public UUID getShipmentLineId() {
        return shipmentLineId;
    }

    public void setShipmentLineId(UUID shipmentLineId) {
        this.shipmentLineId = shipmentLineId;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(UUID shipmentId) {
        this.shipmentId = shipmentId;
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
