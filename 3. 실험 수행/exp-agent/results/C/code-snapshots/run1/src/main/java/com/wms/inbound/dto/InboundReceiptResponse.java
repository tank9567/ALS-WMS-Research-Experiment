package com.wms.inbound.dto;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptResponse {

    private UUID receiptId;
    private UUID poId;
    private String poNumber;
    private String status;
    private String receivedBy;
    private Instant receivedAt;
    private Instant confirmedAt;
    private List<InboundReceiptLineResponse> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineResponse {
        private UUID receiptLineId;
        private UUID productId;
        private String productSku;
        private String productName;
        private UUID locationId;
        private String locationCode;
        private Integer quantity;
        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;
    }
}
