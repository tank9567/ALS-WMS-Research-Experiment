package com.wms.service;

import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.entity.CycleCount;
import com.wms.entity.Location;
import com.wms.repository.CycleCountRepository;
import com.wms.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final LocationRepository locationRepository;

    public CycleCountService(
            CycleCountRepository cycleCountRepository,
            LocationRepository locationRepository) {
        this.cycleCountRepository = cycleCountRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("Location not found"));

        // 이미 실사가 진행 중인지 체크
        cycleCountRepository.findByLocationLocationIdAndStatus(location.getLocationId(), "in_progress")
                .ifPresent(cc -> {
                    throw new IllegalStateException("Cycle count already in progress for this location");
                });

        // 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 실사 세션 생성
        CycleCount cycleCount = new CycleCount();
        cycleCount.setLocation(location);
        cycleCount.setStartedBy(request.getStartedBy());
        cycleCount.setStatus("in_progress");

        cycleCountRepository.save(cycleCount);

        return toResponse(cycleCount);
    }

    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new IllegalArgumentException("Cycle count not found"));

        if (!"in_progress".equals(cycleCount.getStatus())) {
            throw new IllegalStateException("Cycle count is not in progress");
        }

        // 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        // 실사 완료
        cycleCount.setStatus("completed");
        cycleCount.setCompletedAt(OffsetDateTime.now());
        cycleCountRepository.save(cycleCount);

        return toResponse(cycleCount);
    }

    private CycleCountResponse toResponse(CycleCount cycleCount) {
        CycleCountResponse response = new CycleCountResponse();
        response.setCycleCountId(cycleCount.getCycleCountId());
        response.setLocationId(cycleCount.getLocation().getLocationId());
        response.setLocationCode(cycleCount.getLocation().getCode());
        response.setStatus(cycleCount.getStatus());
        response.setStartedBy(cycleCount.getStartedBy());
        response.setStartedAt(cycleCount.getStartedAt());
        response.setCompletedAt(cycleCount.getCompletedAt());
        return response;
    }
}
