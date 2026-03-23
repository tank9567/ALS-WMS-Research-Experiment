package com.wms.adjustment.dto;

import com.wms.adjustment.entity.CycleCount;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountResponse {

    private UUID cycleCountId;
    private UUID locationId;
    private String locationCode;
    private CycleCount.CycleCountStatus status;
    private String startedBy;
    private Instant startedAt;
    private Instant completedAt;

    public static CycleCountResponse from(CycleCount cycleCount) {
        return CycleCountResponse.builder()
                .cycleCountId(cycleCount.getCycleCountId())
                .locationId(cycleCount.getLocation().getLocationId())
                .locationCode(cycleCount.getLocation().getCode())
                .status(cycleCount.getStatus())
                .startedBy(cycleCount.getStartedBy())
                .startedAt(cycleCount.getStartedAt())
                .completedAt(cycleCount.getCompletedAt())
                .build();
    }
}
