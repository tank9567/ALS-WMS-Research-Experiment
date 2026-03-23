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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderRequest {
    @NotBlank(message = "Shipment number is required")
    private String shipmentNumber;

    @NotBlank(message = "Customer name is required")
    private String customerName;

    @NotNull(message = "Requested at is required")
    private OffsetDateTime requestedAt;

    @NotEmpty(message = "Shipment lines are required")
    @Valid
    private List<ShipmentOrderLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineRequest {
        @NotNull(message = "Product ID is required")
        private UUID productId;

        @NotNull(message = "Requested quantity is required")
        @Positive(message = "Requested quantity must be positive")
        private Integer requestedQty;
    }
}
