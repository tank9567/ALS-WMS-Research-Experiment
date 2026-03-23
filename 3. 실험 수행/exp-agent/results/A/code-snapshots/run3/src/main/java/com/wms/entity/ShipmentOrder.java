package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50, name = "order_number")
    private String orderNumber;

    @Column(nullable = false, name = "customer_name")
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.pending;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @Column(nullable = false, name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public enum ShipmentStatus {
        pending, picking, partial, shipped, cancelled
    }
}
