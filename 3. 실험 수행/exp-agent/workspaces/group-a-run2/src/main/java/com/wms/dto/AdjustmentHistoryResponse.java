package com.wms.dto;

import com.wms.entity.InventoryAdjustment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjustmentHistoryResponse {

    private UUID id;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID locationId;
    private String locationCode;
    private Integer systemQty;
    private Integer actualQty;
    private Integer difference;
    private String reason;
    private String approvalStatus;
    private String approvedBy;
    private Instant approvedAt;
    private String rejectionReason;
    private Instant createdAt;

    public static AdjustmentHistoryResponse fromEntity(InventoryAdjustment entity) {
        return AdjustmentHistoryResponse.builder()
                .id(entity.getId())
                .productId(entity.getProduct().getId())
                .productSku(entity.getProduct().getSku())
                .productName(entity.getProduct().getName())
                .locationId(entity.getLocation().getId())
                .locationCode(entity.getLocation().getLocationCode())
                .systemQty(entity.getSystemQty())
                .actualQty(entity.getActualQty())
                .difference(entity.getDifference())
                .reason(entity.getReason())
                .approvalStatus(entity.getApprovalStatus().name())
                .approvedBy(entity.getApprovedBy())
                .approvedAt(entity.getApprovedAt())
                .rejectionReason(entity.getRejectionReason())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
