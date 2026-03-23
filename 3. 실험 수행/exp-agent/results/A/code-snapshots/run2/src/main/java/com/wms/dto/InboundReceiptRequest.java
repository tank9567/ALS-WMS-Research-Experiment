package com.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptRequest {

    @NotBlank(message = "Receipt number is required")
    private String receiptNumber;

    @NotNull(message = "Purchase order ID is required")
    private UUID purchaseOrderId;

    @NotEmpty(message = "At least one receipt line is required")
    @Valid
    private List<InboundReceiptLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineRequest {

        @NotNull(message = "Purchase order line ID is required")
        private UUID purchaseOrderLineId;

        @NotNull(message = "Product ID is required")
        private UUID productId;

        @NotNull(message = "Location ID is required")
        private UUID locationId;

        @NotNull(message = "Received quantity is required")
        @Positive(message = "Received quantity must be positive")
        private Integer receivedQuantity;

        private String lotNumber;

        private LocalDate manufactureDate;

        private LocalDate expiryDate;
    }
}
