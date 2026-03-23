package com.wms.service;

import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.entity.CycleCount;
import com.wms.entity.Location;
import com.wms.exception.BusinessException;
import com.wms.repository.CycleCountRepository;
import com.wms.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        // 로케이션 조회
        Location location = locationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "로케이션을 찾을 수 없습니다."));

        // 이미 진행 중인 실사가 있는지 확인
        cycleCountRepository.findInProgressByLocationId(location.getLocationId())
            .ifPresent(cc -> {
                throw new BusinessException("CYCLE_COUNT_IN_PROGRESS", "해당 로케이션에 이미 진행 중인 실사가 있습니다.");
            });

        // 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 실사 세션 생성
        CycleCount cycleCount = CycleCount.builder()
            .location(location)
            .status(CycleCount.Status.IN_PROGRESS)
            .startedBy(request.getStartedBy())
            .build();

        cycleCount = cycleCountRepository.save(cycleCount);

        return buildResponse(cycleCount, location);
    }

    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId) {
        // 실사 세션 조회
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
            .orElseThrow(() -> new BusinessException("CYCLE_COUNT_NOT_FOUND", "실사 세션을 찾을 수 없습니다."));

        if (cycleCount.getStatus() != CycleCount.Status.IN_PROGRESS) {
            throw new BusinessException("INVALID_STATUS", "진행 중인 실사만 완료할 수 있습니다.");
        }

        // 실사 완료 처리
        cycleCount.setStatus(CycleCount.Status.COMPLETED);
        cycleCount.setCompletedAt(OffsetDateTime.now());
        cycleCount = cycleCountRepository.save(cycleCount);

        // 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        return buildResponse(cycleCount, location);
    }

    @Transactional(readOnly = true)
    public List<CycleCountResponse> getAllCycleCounts() {
        return cycleCountRepository.findAll().stream()
            .map(cc -> buildResponse(cc, cc.getLocation()))
            .collect(Collectors.toList());
    }

    private CycleCountResponse buildResponse(CycleCount cycleCount, Location location) {
        return CycleCountResponse.builder()
            .cycleCountId(cycleCount.getCycleCountId())
            .locationId(location.getLocationId())
            .locationCode(location.getCode())
            .status(cycleCount.getStatus().name())
            .startedBy(cycleCount.getStartedBy())
            .startedAt(cycleCount.getStartedAt())
            .completedAt(cycleCount.getCompletedAt())
            .build();
    }
}
