package com.wms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentRequest {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    @NotNull(message = "Location ID is required")
    private UUID locationId;

    @NotNull(message = "Actual quantity is required")
    private Integer actualQty;

    @NotBlank(message = "Reason is required")
    private String reason;

    private UUID cycleCountId;
}
