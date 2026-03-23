package com.wms.dto;

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
    private UUID productId;
    private UUID locationId;
    private Integer actualQty;
    private String reason;
    private String adjustedBy;
}
