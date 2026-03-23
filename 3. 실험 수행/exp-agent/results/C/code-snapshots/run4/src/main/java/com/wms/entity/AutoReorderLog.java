package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auto_reorder_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoReorderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "auto_reorder_log_id")
    private UUID autoReorderLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 50)
    private TriggerReason triggerReason;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public enum TriggerReason {
        SAFETY_STOCK_TRIGGER, URGENT_REORDER
    }
}
