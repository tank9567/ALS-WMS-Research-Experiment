package com.wms.outbound.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderCreateRequest {
    private String orderNumber;
    private String customerName;
    private List<ShipmentLineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentLineRequest {
        private UUID productId;
        private Integer lineNumber;
        private Integer requestedQty;
    }
}
