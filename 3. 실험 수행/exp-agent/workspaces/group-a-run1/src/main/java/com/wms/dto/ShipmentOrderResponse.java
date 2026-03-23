package com.wms.dto;

import com.wms.entity.ShipmentOrder;
import com.wms.entity.ShipmentOrderLine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
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
    private ShipmentOrder.ShipmentStatus status;
    private OffsetDateTime orderDate;
    private OffsetDateTime shippedAt;
    private List<ShipmentOrderLineResponse> lines;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineResponse {
        private UUID id;
        private UUID productId;
        private String productSku;
        private String productName;
        private Integer requestedQty;
        private Integer pickedQty;
        private ShipmentOrderLine.LineStatus status;
    }
}
