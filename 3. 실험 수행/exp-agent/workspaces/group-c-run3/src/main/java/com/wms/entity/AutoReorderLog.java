package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auto_reorder_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoReorderLog {

    @Id
    @Column(name = "log_id")
    private UUID logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 50)
    private Reason reason;

    @Column(name = "triggered_at")
    private OffsetDateTime triggeredAt;

    public enum Reason {
        SAFETY_STOCK_TRIGGER, URGENT_REORDER
    }

    @PrePersist
    protected void onCreate() {
        if (logId == null) {
            logId = UUID.randomUUID();
        }
        triggeredAt = OffsetDateTime.now();
    }
}
