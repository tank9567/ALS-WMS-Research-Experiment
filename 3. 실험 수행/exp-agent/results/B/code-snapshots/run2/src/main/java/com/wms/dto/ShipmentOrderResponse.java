package com.wms.dto;

import com.wms.entity.ShipmentOrder;
import com.wms.entity.ShipmentOrderLine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentOrderResponse {

    private UUID shipmentId;
    private String shipmentNumber;
    private String customerName;
    private String status;
    private Instant requestedAt;
    private Instant shippedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ShipmentOrderLineResponse> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipmentOrderLineResponse {
        private UUID shipmentLineId;
        private UUID productId;
        private String productSku;
        private String productName;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
    }

    public static ShipmentOrderResponse from(ShipmentOrder shipmentOrder) {
        return ShipmentOrderResponse.builder()
                .shipmentId(shipmentOrder.getShipmentId())
                .shipmentNumber(shipmentOrder.getShipmentNumber())
                .customerName(shipmentOrder.getCustomerName())
                .status(shipmentOrder.getStatus().name())
                .requestedAt(shipmentOrder.getRequestedAt())
                .shippedAt(shipmentOrder.getShippedAt())
                .createdAt(shipmentOrder.getCreatedAt())
                .updatedAt(shipmentOrder.getUpdatedAt())
                .lines(shipmentOrder.getLines().stream()
                        .map(ShipmentOrderResponse::fromLine)
                        .collect(Collectors.toList()))
                .build();
    }

    private static ShipmentOrderLineResponse fromLine(ShipmentOrderLine line) {
        return ShipmentOrderLineResponse.builder()
                .shipmentLineId(line.getShipmentLineId())
                .productId(line.getProduct().getProductId())
                .productSku(line.getProduct().getSku())
                .productName(line.getProduct().getName())
                .requestedQty(line.getRequestedQty())
                .pickedQty(line.getPickedQty())
                .status(line.getStatus().name())
                .build();
    }
}
