package com.wms.outbound.dto;

import lombok.*;
import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShipmentOrderRequest {
    private String shipmentNumber;
    private String customerName;
    private ZonedDateTime requestedAt;
    private List<ShipmentLineRequest> lines;
}
