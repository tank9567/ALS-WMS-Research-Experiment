package com.wms.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class ShipmentOrderResponse {
    private UUID id;
    private String orderNumber;
    private String customerName;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime shippedAt;
    private List<ShipmentOrderLineResponse> lines;

    @Data
    public static class ShipmentOrderLineResponse {
        private UUID id;
        private UUID productId;
        private Integer requestedQuantity;
        private Integer pickedQuantity;
        private String status;
    }
}
