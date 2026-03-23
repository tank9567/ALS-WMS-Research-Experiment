package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CycleCountResponse {
    private UUID cycleCountId;
    private UUID locationId;
    private String locationCode;
    private String status;
    private String startedBy;
    private Instant startedAt;
    private Instant completedAt;
}
