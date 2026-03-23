package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentOrderRequest {

    private String shipmentNumber;
    private String customerName;
    private Instant requestedAt;
    private List<ShipmentOrderLineRequest> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipmentOrderLineRequest {
        private UUID productId;
        private Integer requestedQty;
    }
}
