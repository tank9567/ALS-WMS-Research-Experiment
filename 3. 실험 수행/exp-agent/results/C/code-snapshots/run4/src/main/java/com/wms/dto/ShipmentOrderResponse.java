package com.wms.dto;

import com.wms.entity.ShipmentOrder;
import com.wms.entity.ShipmentOrderLine;
import lombok.*;

import java.time.OffsetDateTime;
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
    private OffsetDateTime requestedShipDate;
    private OffsetDateTime shippedAt;
    private List<ShipmentOrderLineResponse> lines;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineResponse {
        private UUID shipmentOrderLineId;
        private UUID productId;
        private String productSku;
        private String productName;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static ShipmentOrderLineResponse fromEntity(ShipmentOrderLine line) {
            return ShipmentOrderLineResponse.builder()
                    .shipmentOrderLineId(line.getShipmentOrderLineId())
                    .productId(line.getProduct().getProductId())
                    .productSku(line.getProduct().getSku())
                    .productName(line.getProduct().getName())
                    .requestedQty(line.getRequestedQty())
                    .pickedQty(line.getPickedQty())
                    .status(line.getStatus().name())
                    .createdAt(line.getCreatedAt())
                    .updatedAt(line.getUpdatedAt())
                    .build();
        }
    }

    public static ShipmentOrderResponse fromEntity(ShipmentOrder order) {
        return ShipmentOrderResponse.builder()
                .shipmentOrderId(order.getShipmentOrderId())
                .orderNumber(order.getOrderNumber())
                .customerName(order.getCustomerName())
                .status(order.getStatus().name())
                .requestedShipDate(order.getRequestedShipDate())
                .shippedAt(order.getShippedAt())
                .lines(order.getLines().stream()
                        .map(ShipmentOrderLineResponse::fromEntity)
                        .collect(Collectors.toList()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
