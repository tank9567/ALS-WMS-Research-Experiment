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
    private UUID cycleCountId;
    private UUID locationId;
    private String locationCode;
    private String status;
    private String startedBy;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;

    public static CycleCountResponse from(CycleCount cycleCount) {
        return CycleCountResponse.builder()
            .cycleCountId(cycleCount.getCycleCountId())
            .locationId(cycleCount.getLocation().getLocationId())
            .locationCode(cycleCount.getLocation().getCode())
            .status(cycleCount.getStatus().name())
            .startedBy(cycleCount.getStartedBy())
            .completedAt(cycleCount.getCompletedAt())
            .createdAt(cycleCount.getCreatedAt())
            .build();
    }
}
