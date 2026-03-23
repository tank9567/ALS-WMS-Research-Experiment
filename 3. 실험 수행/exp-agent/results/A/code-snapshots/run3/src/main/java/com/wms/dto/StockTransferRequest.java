package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferRequest {
    private UUID inventoryId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
}
