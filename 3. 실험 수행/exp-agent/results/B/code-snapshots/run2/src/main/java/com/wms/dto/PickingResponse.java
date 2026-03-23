package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PickingResponse {

    private UUID shipmentId;
    private String shipmentNumber;
    private String status;
    private List<LinePickingDetail> lineDetails;
    private List<BackorderDetail> backorders;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinePickingDetail {
        private UUID shipmentLineId;
        private UUID productId;
        private String productSku;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
        private List<LocationPickDetail> pickDetails;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationPickDetail {
        private UUID locationId;
        private String locationCode;
        private Integer pickedQty;
        private String lotNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackorderDetail {
        private UUID backorderId;
        private UUID productId;
        private String productSku;
        private Integer shortageQty;
    }
}
