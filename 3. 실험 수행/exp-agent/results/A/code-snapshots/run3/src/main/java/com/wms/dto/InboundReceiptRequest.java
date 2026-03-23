package com.wms.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptRequest {

    @NotNull
    private String receiptNumber;

    @NotNull
    private UUID purchaseOrderId;

    @NotNull
    private OffsetDateTime receivedAt;

    @NotNull
    private List<InboundReceiptLineRequest> lines;
}
