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
public class ShipmentOrderCreateRequest {
    private String shipmentNumber;
    private String customerName;
    private OffsetDateTime requestedAt;
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
