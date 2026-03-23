package com.wms.dto;

import lombok.Data;

import java.util.List;

@Data
public class ShipmentOrderRequest {
    private String orderNumber;
    private String customerName;
    private List<ShipmentOrderLineRequest> lines;
}
