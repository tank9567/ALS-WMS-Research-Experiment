package com.wms.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class CycleCountResponse {
    private UUID id;
    private UUID locationId;
    private UUID productId;
    private String status;
    private String startedBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
