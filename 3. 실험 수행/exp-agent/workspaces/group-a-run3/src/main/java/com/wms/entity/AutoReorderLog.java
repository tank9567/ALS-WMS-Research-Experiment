package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auto_reorder_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AutoReorderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, name = "trigger_reason")
    private TriggerReason triggerReason;

    @Column(nullable = false, name = "reorder_qty")
    private Integer reorderQty;

    @Column(nullable = false, name = "current_stock")
    private Integer currentStock;

    @Column(nullable = false, name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public enum TriggerReason {
        SAFETY_STOCK_TRIGGER, EMERGENCY_REORDER
    }
}
