package com.wms.dto;

import com.wms.entity.CycleCount;
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
public class CycleCountResponse {
    private UUID id;
    private UUID locationId;
    private String locationCode;
    private String status;
    private String startedBy;
    private String completedBy;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;

    public static CycleCountResponse from(CycleCount cycleCount) {
        return CycleCountResponse.builder()
                .id(cycleCount.getId())
                .locationId(cycleCount.getLocation().getId())
                .locationCode(cycleCount.getLocation().getCode())
                .status(cycleCount.getStatus().name())
                .startedBy(cycleCount.getStartedBy())
                .completedBy(cycleCount.getCompletedBy())
                .startedAt(cycleCount.getStartedAt())
                .completedAt(cycleCount.getCompletedAt())
                .createdAt(cycleCount.getCreatedAt())
                .build();
    }
}
