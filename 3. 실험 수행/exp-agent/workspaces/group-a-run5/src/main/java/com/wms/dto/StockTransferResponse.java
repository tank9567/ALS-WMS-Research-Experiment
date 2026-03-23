package com.wms.dto;

import com.wms.entity.StockTransfer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferResponse {

    private UUID id;
    private UUID inventoryId;
    private String productSku;
    private String productName;
    private UUID fromLocationId;
    private String fromLocationCode;
    private UUID toLocationId;
    private String toLocationCode;
    private Integer quantity;
    private StockTransfer.TransferStatus transferStatus;
    private String requestedBy;
    private String approvedBy;
    private String reason;
    private OffsetDateTime transferredAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
