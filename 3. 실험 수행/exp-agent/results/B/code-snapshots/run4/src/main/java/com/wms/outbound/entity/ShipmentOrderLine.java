package com.wms.outbound.entity;

import com.wms.inbound.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "shipment_order_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderLine {

    @Id
    @Column(name = "shipment_line_id")
    private UUID shipmentLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private ShipmentOrder shipmentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "picked_qty", nullable = false)
    private Integer pickedQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShipmentLineStatus status;

    @PrePersist
    public void prePersist() {
        if (shipmentLineId == null) {
            shipmentLineId = UUID.randomUUID();
        }
        if (pickedQty == null) {
            pickedQty = 0;
        }
        if (status == null) {
            status = ShipmentLineStatus.PENDING;
        }
    }
}
