package com.wms.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickResponse {
    private UUID shipmentId;
    private String shipmentNumber;
    private String status;
    private List<LinePickResult> lineResults;
    private List<BackorderInfo> backorders;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LinePickResult {
        private UUID shipmentLineId;
        private UUID productId;
        private String productName;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
        private List<PickDetail> pickDetails;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PickDetail {
        private UUID locationId;
        private String locationCode;
        private Integer pickedQty;
        private String lotNumber;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BackorderInfo {
        private UUID backorderId;
        private UUID productId;
        private String productName;
        private Integer shortageQty;
    }
}
