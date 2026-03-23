package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderResponse {
    private UUID id;
    private String shipmentNumber;
    private String customerName;
    private String status;
    private Instant requestedDate;
    private Instant shippedAt;
    private List<ShipmentOrderLineResponse> lines;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineResponse {
        private UUID id;
        private UUID productId;
        private String productSku;
        private String productName;
        private Integer requestedQuantity;
        private Integer pickedQuantity;
        private String status;
    }
}
