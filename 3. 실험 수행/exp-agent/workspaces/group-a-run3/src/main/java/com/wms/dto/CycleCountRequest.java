package com.wms.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CycleCountRequest {
    private UUID locationId;
    private UUID productId;
    private String startedBy;
}
