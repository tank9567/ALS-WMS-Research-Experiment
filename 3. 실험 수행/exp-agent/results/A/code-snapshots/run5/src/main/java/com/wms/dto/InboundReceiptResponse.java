package com.wms.dto;

import com.wms.entity.InboundReceipt;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptResponse {
    private UUID id;
    private String receiptNumber;
    private UUID purchaseOrderId;
    private String purchaseOrderNumber;
    private String status;
    private LocalDate receivedDate;
    private String rejectionReason;
    private List<InboundReceiptLineResponse> lines;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineResponse {
        private UUID id;
        private UUID purchaseOrderLineId;
        private UUID productId;
        private String productSku;
        private String productName;
        private UUID locationId;
        private String locationCode;
        private Integer quantity;
        private String lotNumber;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
    }

    public static InboundReceiptResponse from(InboundReceipt receipt, List<InboundReceiptLineResponse> lines) {
        return InboundReceiptResponse.builder()
                .id(receipt.getId())
                .receiptNumber(receipt.getReceiptNumber())
                .purchaseOrderId(receipt.getPurchaseOrder().getId())
                .purchaseOrderNumber(receipt.getPurchaseOrder().getPoNumber())
                .status(receipt.getStatus().name())
                .receivedDate(receipt.getReceivedDate())
                .rejectionReason(receipt.getRejectionReason())
                .lines(lines)
                .createdAt(receipt.getCreatedAt())
                .updatedAt(receipt.getUpdatedAt())
                .build();
    }
}
