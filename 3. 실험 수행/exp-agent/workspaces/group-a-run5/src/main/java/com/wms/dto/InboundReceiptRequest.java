package com.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "Purchase order ID is required")
    private UUID purchaseOrderId;

    @NotNull(message = "Received date is required")
    private LocalDate receivedDate;

    @NotEmpty(message = "Receipt lines cannot be empty")
    @Valid
    private List<InboundReceiptLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineRequest {

        @NotNull(message = "Purchase order line ID is required")
        private UUID purchaseOrderLineId;

        @NotNull(message = "Location ID is required")
        private UUID locationId;

        @NotNull(message = "Quantity is required")
        private Integer quantity;

        private String lotNumber;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
    }
}
