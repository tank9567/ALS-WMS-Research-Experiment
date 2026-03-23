package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceipt {

    @Id
    @Column(name = "receipt_id")
    private UUID receiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InboundReceiptStatus status;

    @Column(name = "received_by", nullable = false, length = 100)
    private String receivedBy;

    @Column(name = "received_at")
    private ZonedDateTime receivedAt;

    @Column(name = "confirmed_at")
    private ZonedDateTime confirmedAt;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (receiptId == null) {
            receiptId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (receivedAt == null) {
            receivedAt = ZonedDateTime.now();
        }
        if (status == null) {
            status = InboundReceiptStatus.INSPECTING;
        }
    }
}
