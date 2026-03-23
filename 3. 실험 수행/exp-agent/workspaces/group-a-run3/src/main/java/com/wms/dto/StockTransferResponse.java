package com.wms.dto;

import com.wms.entity.StockTransfer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferResponse {
    private UUID id;
    private UUID inventoryId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
    private String transferStatus;
    private OffsetDateTime requestedAt;
    private OffsetDateTime approvedAt;
    private String approvedBy;
    private String rejectionReason;

    public static StockTransferResponse from(StockTransfer transfer) {
        return new StockTransferResponse(
            transfer.getId(),
            transfer.getInventory().getId(),
            transfer.getFromLocation().getId(),
            transfer.getToLocation().getId(),
            transfer.getQuantity(),
            transfer.getTransferStatus().name(),
            transfer.getRequestedAt(),
            transfer.getApprovedAt(),
            transfer.getApprovedBy(),
            transfer.getRejectionReason()
        );
    }
}
