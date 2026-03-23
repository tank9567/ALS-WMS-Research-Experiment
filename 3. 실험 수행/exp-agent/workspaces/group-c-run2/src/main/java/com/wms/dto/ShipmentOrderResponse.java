package com.wms.dto;

import com.wms.entity.ShipmentOrder;
import com.wms.entity.ShipmentOrderLine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderResponse {
    private UUID shipmentId;
    private String shipmentNumber;
    private String customerName;
    private String status;
    private OffsetDateTime requestedAt;
    private OffsetDateTime shippedAt;
    private List<ShipmentOrderLineResponse> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineResponse {
        private UUID shipmentLineId;
        private UUID productId;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
    }

    public static ShipmentOrderResponse from(ShipmentOrder shipment, List<ShipmentOrderLine> lines) {
        return ShipmentOrderResponse.builder()
                .shipmentId(shipment.getShipmentId())
                .shipmentNumber(shipment.getShipmentNumber())
                .customerName(shipment.getCustomerName())
                .status(shipment.getStatus().name())
                .requestedAt(shipment.getRequestedAt())
                .shippedAt(shipment.getShippedAt())
                .lines(lines.stream()
                        .map(line -> ShipmentOrderLineResponse.builder()
                                .shipmentLineId(line.getShipmentLineId())
                                .productId(line.getProductId())
                                .requestedQty(line.getRequestedQty())
                                .pickedQty(line.getPickedQty())
                                .status(line.getStatus().name())
                                .build())
                        .toList())
                .build();
    }
}
