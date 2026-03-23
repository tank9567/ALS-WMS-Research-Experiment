package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @Column(name = "po_id")
    private UUID poId;

    @Column(name = "po_number", unique = true, nullable = false, length = 30)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "po_type", nullable = false, length = 20)
    private PoType poType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PoStatus status;

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (poId == null) {
            poId = UUID.randomUUID();
        }
        if (poType == null) {
            poType = PoType.NORMAL;
        }
        if (status == null) {
            status = PoStatus.pending;
        }
    }

    public enum PoType {
        NORMAL, URGENT, IMPORT
    }

    public enum PoStatus {
        pending, partial, completed, cancelled, hold
    }
}
