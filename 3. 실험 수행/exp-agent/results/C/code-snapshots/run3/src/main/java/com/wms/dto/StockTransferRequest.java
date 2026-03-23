package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferRequest {
    private UUID fromLocationId;
    private UUID toLocationId;
    private UUID productId;
    private String lotNumber;
    private Integer quantity;
    private String requestedBy;
}
