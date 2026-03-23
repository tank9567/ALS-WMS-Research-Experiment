package com.wms.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ShipmentLineRequest {
    private UUID productId;
    private Integer requestedQty;
}
