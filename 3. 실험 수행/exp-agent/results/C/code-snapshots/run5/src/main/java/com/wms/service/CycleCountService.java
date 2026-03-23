package com.wms.service;

import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.entity.CycleCount;
import com.wms.entity.Location;
import com.wms.exception.BusinessException;
import com.wms.repository.CycleCountRepository;
import com.wms.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final LocationRepository locationRepository;

    public CycleCountService(
        CycleCountRepository cycleCountRepository,
        LocationRepository locationRepository
    ) {
        this.cycleCountRepository = cycleCountRepository;
        this.locationRepository = locationRepository;
    }

    @Transactional
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        Location location = locationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new BusinessException("로케이션을 찾을 수 없습니다"));

        if (location.getIsFrozen()) {
            throw new BusinessException("이미 실사가 진행 중인 로케이션입니다");
        }

        // 실사 시작 - 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        CycleCount cycleCount = new CycleCount();
        cycleCount.setLocation(location);
        cycleCount.setStartedBy(request.getStartedBy());
        cycleCount.setStatus(CycleCount.CycleCountStatus.IN_PROGRESS);

        CycleCount saved = cycleCountRepository.save(cycleCount);

        return mapToResponse(saved);
    }

    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
            .orElseThrow(() -> new BusinessException("실사 세션을 찾을 수 없습니다"));

        if (cycleCount.getStatus() == CycleCount.CycleCountStatus.COMPLETED) {
            throw new BusinessException("이미 완료된 실사입니다");
        }

        // 실사 완료 - 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        cycleCount.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(Instant.now());

        CycleCount saved = cycleCountRepository.save(cycleCount);

        return mapToResponse(saved);
    }

    private CycleCountResponse mapToResponse(CycleCount cycleCount) {
        CycleCountResponse response = new CycleCountResponse();
        response.setCycleCountId(cycleCount.getCycleCountId());
        response.setLocationId(cycleCount.getLocation().getLocationId());
        response.setLocationCode(cycleCount.getLocation().getCode());
        response.setStatus(cycleCount.getStatus().name());
        response.setStartedBy(cycleCount.getStartedBy());
        response.setStartedAt(cycleCount.getStartedAt());
        response.setCompletedAt(cycleCount.getCompletedAt());
        return response;
    }
}
