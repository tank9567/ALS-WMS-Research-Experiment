package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferListResponse {
    private UUID id;
    private String productSku;
    private String productName;
    private String fromLocationCode;
    private String toLocationCode;
    private Integer quantity;
    private String transferStatus;
    private Instant transferredAt;
    private Instant createdAt;
}
