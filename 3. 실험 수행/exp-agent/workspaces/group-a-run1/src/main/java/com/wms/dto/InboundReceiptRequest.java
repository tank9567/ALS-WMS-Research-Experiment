package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptRequest {

    private String receiptNumber;
    private UUID purchaseOrderId;
    private OffsetDateTime receivedDate;
    private List<InboundReceiptLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineRequest {
        private UUID purchaseOrderLineId;
        private UUID productId;
        private UUID locationId;
        private String lotNumber;
        private Integer receivedQty;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
    }
}
