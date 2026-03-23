package com.wms.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferRequest {
    private UUID inventoryId;
    private String fromLocationCode;
    private String toLocationCode;
    private Integer quantity;
    private String reason;
}
