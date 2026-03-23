package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer {
    @Id
    @Column(name = "transfer_id")
    private UUID transferId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "from_location_id", nullable = false)
    private UUID fromLocationId;

    @Column(name = "to_location_id", nullable = false)
    private UUID toLocationId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 30)
    private TransferStatus transferStatus = TransferStatus.immediate;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (transferId == null) {
            transferId = UUID.randomUUID();
        }
        if (requestedAt == null) {
            requestedAt = OffsetDateTime.now();
        }
        createdAt = OffsetDateTime.now();
    }

    public enum TransferStatus {
        immediate,          // 즉시 이동 (< 80%)
        pending_approval,   // 승인 대기 (≥ 80%)
        approved,          // 승인됨
        rejected           // 거부됨
    }
}
