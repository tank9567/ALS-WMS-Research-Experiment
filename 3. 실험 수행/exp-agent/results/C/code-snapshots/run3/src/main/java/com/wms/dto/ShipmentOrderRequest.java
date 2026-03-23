package com.wms.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class ShipmentOrderRequest {
    private String customerName;
    private String createdBy;
    private List<ShipmentLineRequest> lines;
}
