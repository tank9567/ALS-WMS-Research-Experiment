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
public class InboundReceiptListResponse {
    private UUID id;
    private String receiptNumber;
    private String poNumber;
    private String status;
    private Instant receivedDate;
    private Instant confirmedAt;
    private Instant createdAt;
}
