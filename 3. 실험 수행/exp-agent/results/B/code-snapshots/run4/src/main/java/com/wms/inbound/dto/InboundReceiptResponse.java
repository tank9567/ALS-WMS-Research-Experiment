package com.wms.inbound.dto;

import com.wms.inbound.entity.InboundReceiptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptResponse {
    private UUID receiptId;
    private UUID poId;
    private String poNumber;
    private InboundReceiptStatus status;
    private String receivedBy;
    private ZonedDateTime receivedAt;
    private ZonedDateTime confirmedAt;
}
