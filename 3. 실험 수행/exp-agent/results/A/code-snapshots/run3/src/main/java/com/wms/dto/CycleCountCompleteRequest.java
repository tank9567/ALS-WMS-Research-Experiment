package com.wms.dto;

import lombok.Data;

@Data
public class CycleCountCompleteRequest {
    private Integer actualQty;
    private String reason;
}
