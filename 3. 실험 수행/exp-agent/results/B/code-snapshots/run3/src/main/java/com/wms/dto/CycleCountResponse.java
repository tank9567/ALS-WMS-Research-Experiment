package com.wms.dto;

import com.wms.entity.CycleCount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
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
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;

    public static CycleCountResponse from(CycleCount cycleCount) {
        return CycleCountResponse.builder()
            .cycleCountId(cycleCount.getCycleCountId())
            .locationId(cycleCount.getLocation().getLocationId())
            .locationCode(cycleCount.getLocation().getCode())
            .status(cycleCount.getStatus().name())
            .startedBy(cycleCount.getStartedBy())
            .startedAt(cycleCount.getStartedAt())
            .completedAt(cycleCount.getCompletedAt())
            .build();
    }
}
