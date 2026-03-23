package com.wms.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderRequest {
    private String orderNumber;
    private String customerName;
    private OffsetDateTime requestedShipDate;
    private List<ShipmentOrderLineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineRequest {
        private UUID productId;
        private Integer requestedQty;
    }
}
