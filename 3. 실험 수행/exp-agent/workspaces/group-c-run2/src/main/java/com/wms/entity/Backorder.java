package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "backorders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackorderStatus status = BackorderStatus.OPEN;

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

    public enum BackorderStatus {
        OPEN, FULFILLED, CANCELLED
    }
}
