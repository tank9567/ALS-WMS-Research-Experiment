package com.wms.adjustment.service;

import com.wms.common.entity.CycleCount;
import com.wms.common.entity.CycleCountStatus;
import com.wms.common.repository.CycleCountRepository;
import com.wms.inbound.entity.Location;
import com.wms.inbound.repository.LocationRepository;
import com.wms.adjustment.dto.CreateCycleCountRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public CycleCount startCycleCount(CreateCycleCountRequest request) {
        Location location = locationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new IllegalArgumentException("Location not found"));

        if (location.getIsFrozen()) {
            throw new IllegalArgumentException("Cycle count already in progress for this location");
        }

        // 실사 시작 - 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        CycleCount cycleCount = CycleCount.builder()
            .cycleCountId(UUID.randomUUID())
            .location(location)
            .status(CycleCountStatus.IN_PROGRESS)
            .startedBy(request.getStartedBy())
            .startedAt(ZonedDateTime.now())
            .build();

        return cycleCountRepository.save(cycleCount);
    }

    @Transactional
    public CycleCount completeCycleCount(UUID cycleCountId) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
            .orElseThrow(() -> new IllegalArgumentException("Cycle count not found"));

        if (cycleCount.getStatus() == CycleCountStatus.COMPLETED) {
            throw new IllegalArgumentException("Cycle count already completed");
        }

        // 실사 완료 - 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        cycleCount.setStatus(CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(ZonedDateTime.now());

        return cycleCountRepository.save(cycleCount);
    }

    @Transactional(readOnly = true)
    public CycleCount getCycleCount(UUID cycleCountId) {
        return cycleCountRepository.findById(cycleCountId)
            .orElseThrow(() -> new IllegalArgumentException("Cycle count not found"));
    }
}
