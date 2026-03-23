package com.wms.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickingResult {
    private UUID shipmentOrderId;
    private String orderNumber;
    private String status;
    private List<PickedLineResult> pickedLines;
    private List<String> warnings;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PickedLineResult {
        private UUID lineId;
        private String productSku;
        private Integer requestedQty;
        private Integer pickedQty;
        private Integer backorderedQty;
        private String status;
    }
}
