package com.wms.adjustment.entity;

import com.wms.inbound.entity.Location;
import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_adjustments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustment {

    @Id
    @Column(name = "adjustment_id")
    private UUID adjustmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;

    @Column(name = "actual_qty", nullable = false)
    private Integer actualQty;

    @Column(name = "difference", nullable = false)
    private Integer difference;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "requires_approval", nullable = false)
    private Boolean requiresApproval;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "adjusted_by", nullable = false, length = 100)
    private String adjustedBy;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "approved_at")
    private ZonedDateTime approvedAt;

    @PrePersist
    public void prePersist() {
        if (adjustmentId == null) {
            adjustmentId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (approvalStatus == null) {
            approvalStatus = ApprovalStatus.AUTO_APPROVED;
        }
        if (requiresApproval == null) {
            requiresApproval = false;
        }
    }
}
