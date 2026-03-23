package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentOrder {

    @Id
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.pending;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "picked_at")
    private OffsetDateTime pickedAt;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    public enum Status {
        pending, picking, partial, shipped, cancelled
    }

    @PrePersist
    protected void onCreate() {
        if (shipmentId == null) {
            shipmentId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }
}
