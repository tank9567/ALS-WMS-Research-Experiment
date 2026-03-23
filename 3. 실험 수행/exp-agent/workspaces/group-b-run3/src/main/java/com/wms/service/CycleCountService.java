package com.wms.service;

import com.wms.entity.CycleCount;
import com.wms.entity.CycleCount.CycleCountStatus;
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

@Service
@RequiredArgsConstructor
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final LocationRepository locationRepository;

    /**
     * 실사 시작
     * - cycle_counts 레코드 생성
     * - 해당 로케이션의 is_frozen을 true로 설정 (입고/출고/이동 동결)
     */
    @Transactional
    public CycleCount startCycleCount(UUID locationId, String startedBy) {
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        // 이미 진행 중인 실사가 있는지 체크
        List<CycleCount> inProgressCounts = cycleCountRepository
            .findByLocationLocationIdAndStatus(locationId, CycleCountStatus.IN_PROGRESS);

        if (!inProgressCounts.isEmpty()) {
            throw new BusinessException("CYCLE_COUNT_IN_PROGRESS",
                "Cycle count already in progress for this location");
        }

        // 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 실사 세션 생성
        CycleCount cycleCount = CycleCount.builder()
            .location(location)
            .status(CycleCountStatus.IN_PROGRESS)
            .startedBy(startedBy)
            .build();

        return cycleCountRepository.save(cycleCount);
    }

    /**
     * 실사 완료
     * - 해당 로케이션의 is_frozen을 false로 해제
     * - cycle_count 상태를 completed로 변경
     */
    @Transactional
    public CycleCount completeCycleCount(UUID cycleCountId) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
            .orElseThrow(() -> new BusinessException("CYCLE_COUNT_NOT_FOUND", "Cycle count not found"));

        if (cycleCount.getStatus() == CycleCountStatus.COMPLETED) {
            throw new BusinessException("CYCLE_COUNT_ALREADY_COMPLETED",
                "Cycle count already completed");
        }

        // 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        // 실사 완료 처리
        cycleCount.setStatus(CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(OffsetDateTime.now());

        return cycleCountRepository.save(cycleCount);
    }

    @Transactional(readOnly = true)
    public CycleCount getCycleCount(UUID cycleCountId) {
        return cycleCountRepository.findById(cycleCountId)
            .orElseThrow(() -> new BusinessException("CYCLE_COUNT_NOT_FOUND", "Cycle count not found"));
    }

    @Transactional(readOnly = true)
    public List<CycleCount> getAllCycleCounts() {
        return cycleCountRepository.findAll();
    }
}
