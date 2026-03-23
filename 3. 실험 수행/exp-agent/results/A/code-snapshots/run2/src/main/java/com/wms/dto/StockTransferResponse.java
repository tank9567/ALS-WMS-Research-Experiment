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
public class StockTransferResponse {

    private UUID id;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID fromLocationId;
    private String fromLocationCode;
    private UUID toLocationId;
    private String toLocationCode;
    private UUID inventoryId;
    private Integer quantity;
    private String transferStatus;
    private String requestedBy;
    private String approvedBy;
    private Instant approvedAt;
    private String rejectionReason;
    private Instant transferredAt;
    private Instant createdAt;
    private Instant updatedAt;
}
