package com.wms.dto;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferResponse {
    private UUID transferId;
    private UUID productId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
    private String lotNumber;
    private String transferStatus;
    private String requestedBy;
    private String approvedBy;
    private OffsetDateTime requestedAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime executedAt;
    private String reason;
    private OffsetDateTime createdAt;
}
