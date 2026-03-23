package com.wms.dto;

import com.wms.entity.StockTransfer;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
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
    private String lotNumber;
    private LocalDate expiryDate;
    private Integer transferQty;
    private StockTransfer.TransferStatus transferStatus;
    private String reason;
    private String requestedBy;
    private String approvedBy;
    private OffsetDateTime transferredAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
