package com.wms.common.entity;

import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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
    @Column(name = "reorder_log_id")
    private UUID reorderLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private TriggerType triggerType;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (reorderLogId == null) {
            reorderLogId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }
}
