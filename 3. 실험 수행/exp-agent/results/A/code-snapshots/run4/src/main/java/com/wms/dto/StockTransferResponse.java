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
    private UUID id;
    private String productSku;
    private String productName;
    private String fromLocationCode;
    private String toLocationCode;
    private Integer quantity;
    private String transferStatus;
    private String reason;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
