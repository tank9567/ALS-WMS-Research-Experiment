package com.wms.adjustment.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentCreateRequest {

    private UUID productId;
    private UUID locationId;
    private Integer actualQty;
    private String reason;
    private String adjustedBy;
}
