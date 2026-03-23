package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_penalties")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPenalty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, name = "penalty_type")
    private PenaltyType penaltyType;

    @Column(nullable = false, name = "occurred_at")
    private OffsetDateTime occurredAt = OffsetDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum PenaltyType {
        OVER_DELIVERY, SHORT_SHELF_LIFE
    }
}
