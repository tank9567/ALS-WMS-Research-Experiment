package com.wms.outbound.dto;

import com.wms.outbound.entity.ShipmentOrder;
import com.wms.outbound.entity.ShipmentOrderLine;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderResponse {
    private UUID shipmentOrderId;
    private String orderNumber;
    private String customerName;
    private String status;
    private Instant requestedAt;
    private Instant shippedAt;
    private List<ShipmentLineResponse> lines;
    private Instant createdAt;
    private Instant updatedAt;

    public static ShipmentOrderResponse from(ShipmentOrder order) {
        return ShipmentOrderResponse.builder()
            .shipmentOrderId(order.getShipmentOrderId())
            .orderNumber(order.getOrderNumber())
            .customerName(order.getCustomerName())
            .status(order.getStatus().name())
            .requestedAt(order.getRequestedAt())
            .shippedAt(order.getShippedAt())
            .lines(order.getLines().stream()
                .map(ShipmentLineResponse::from)
                .collect(Collectors.toList()))
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentLineResponse {
        private UUID lineId;
        private UUID productId;
        private String productSku;
        private String productName;
        private Integer lineNumber;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
        private Instant createdAt;
        private Instant updatedAt;

        public static ShipmentLineResponse from(ShipmentOrderLine line) {
            return ShipmentLineResponse.builder()
                .lineId(line.getLineId())
                .productId(line.getProduct().getProductId())
                .productSku(line.getProduct().getSku())
                .productName(line.getProduct().getName())
                .lineNumber(line.getLineNumber())
                .requestedQty(line.getRequestedQty())
                .pickedQty(line.getPickedQty())
                .status(line.getStatus().name())
                .createdAt(line.getCreatedAt())
                .updatedAt(line.getUpdatedAt())
                .build();
        }
    }
}
