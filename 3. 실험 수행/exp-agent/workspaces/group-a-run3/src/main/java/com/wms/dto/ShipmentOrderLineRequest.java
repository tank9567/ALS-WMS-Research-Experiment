package com.wms.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ShipmentOrderLineRequest {
    private UUID productId;
    private Integer requestedQuantity;
}
