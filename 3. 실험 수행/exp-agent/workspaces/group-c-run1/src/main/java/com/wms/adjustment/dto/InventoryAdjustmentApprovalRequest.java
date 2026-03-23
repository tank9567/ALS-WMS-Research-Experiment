package com.wms.adjustment.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustmentApprovalRequest {

    private String approvedBy;
}
