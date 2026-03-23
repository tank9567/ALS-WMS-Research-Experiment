package com.wms.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderRequest {
    private String orderNumber;
    private String customerName;
    private List<ShipmentLineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentLineRequest {
        private String productSku;
        private Integer requestedQty;
    }
}
