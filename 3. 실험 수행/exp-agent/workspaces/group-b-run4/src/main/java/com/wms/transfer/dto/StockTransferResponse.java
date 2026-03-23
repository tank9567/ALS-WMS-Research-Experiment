package com.wms.transfer.dto;

import com.wms.transfer.entity.TransferStatus;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferResponse {
    private UUID transferId;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID fromLocationId;
    private String fromLocationCode;
    private UUID toLocationId;
    private String toLocationCode;
    private Integer quantity;
    private String lotNumber;
    private String reason;
    private TransferStatus transferStatus;
    private String transferredBy;
    private String approvedBy;
    private ZonedDateTime transferredAt;
}
