package com.wms.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentRequest {
    private UUID productId;
    private UUID locationId;
    private String lotNumber;
    private LocalDate expiryDate;
    private Integer actualQty;
    private String reason;
    private String createdBy;
}
