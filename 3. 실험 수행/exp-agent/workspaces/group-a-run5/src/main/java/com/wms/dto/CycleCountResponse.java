package com.wms.dto;

import com.wms.entity.CycleCount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountResponse {

    private UUID id;
    private UUID locationId;
    private String locationCode;
    private CycleCount.CycleCountStatus status;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static CycleCountResponse from(CycleCount cycleCount) {
        return CycleCountResponse.builder()
                .id(cycleCount.getId())
                .locationId(cycleCount.getLocation().getId())
                .locationCode(cycleCount.getLocation().getCode())
                .status(cycleCount.getStatus())
                .startedAt(cycleCount.getStartedAt())
                .completedAt(cycleCount.getCompletedAt())
                .createdAt(cycleCount.getCreatedAt())
                .updatedAt(cycleCount.getUpdatedAt())
                .build();
    }
}
