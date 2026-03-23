package com.wms.service;

import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.entity.CycleCount;
import com.wms.entity.Location;
import com.wms.exception.BusinessException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.CycleCountRepository;
import com.wms.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final LocationRepository locationRepository;

    @Transactional
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        // 1. 로케이션 조회
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found"));

        // 2. 이미 진행 중인 실사가 있는지 체크
        cycleCountRepository.findByLocationIdAndStatus(location.getId(), CycleCount.CycleCountStatus.IN_PROGRESS)
                .ifPresent(cc -> {
                    throw new BusinessException("Cycle count already in progress for this location");
                });

        // 3. 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 4. 실사 세션 생성
        CycleCount cycleCount = CycleCount.builder()
                .location(location)
                .status(CycleCount.CycleCountStatus.IN_PROGRESS)
                .startedAt(OffsetDateTime.now())
                .build();

        cycleCount = cycleCountRepository.save(cycleCount);

        return CycleCountResponse.from(cycleCount);
    }

    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId) {
        // 1. 실사 세션 조회
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle count not found"));

        if (cycleCount.getStatus() != CycleCount.CycleCountStatus.IN_PROGRESS) {
            throw new BusinessException("Cycle count is not in progress");
        }

        // 2. 실사 완료 처리
        cycleCount.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(OffsetDateTime.now());

        // 3. 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        cycleCount = cycleCountRepository.save(cycleCount);

        return CycleCountResponse.from(cycleCount);
    }

    @Transactional(readOnly = true)
    public CycleCountResponse getCycleCount(UUID cycleCountId) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle count not found"));

        return CycleCountResponse.from(cycleCount);
    }
}
