package com.wms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptLineRequest {

    @NotNull
    private UUID purchaseOrderLineId;

    @NotNull
    private UUID productId;

    @NotNull
    private UUID locationId;

    @NotNull
    @Positive
    private Integer quantity;

    private String lotNumber;

    private LocalDate manufactureDate;

    private LocalDate expiryDate;
}
