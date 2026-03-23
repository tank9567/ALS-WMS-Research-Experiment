package com.wms.dto;

import com.wms.entity.InventoryAdjustment.ApprovalStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentResponse {
    private UUID id;
    private UUID cycleCountId;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID locationId;
    private String locationCode;
    private String lotNumber;
    private LocalDate expiryDate;
    private Integer systemQty;
    private Integer actualQty;
    private Integer differenceQty;
    private String reason;
    private ApprovalStatus approvalStatus;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private String createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
