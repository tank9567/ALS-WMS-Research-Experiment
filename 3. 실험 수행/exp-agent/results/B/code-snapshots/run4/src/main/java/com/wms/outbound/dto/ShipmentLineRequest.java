package com.wms.outbound.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentLineRequest {
    private UUID productId;
    private Integer requestedQty;
}
