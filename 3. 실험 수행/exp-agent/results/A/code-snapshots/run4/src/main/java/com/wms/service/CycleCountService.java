package com.wms.service;

import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.entity.CycleCount;
import com.wms.entity.Location;
import com.wms.enums.CycleCountStatus;
import com.wms.exception.WmsException;
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
        // Validate location
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new WmsException("Location not found"));

        // Check if there's already an in-progress cycle count
        cycleCountRepository.findByLocationIdAndStatus(location.getId(), CycleCountStatus.IN_PROGRESS)
                .ifPresent(cc -> {
                    throw new WmsException("Location already has an in-progress cycle count");
                });

        // Freeze location
        location.setIsFrozen(true);
        locationRepository.save(location);

        // Create cycle count
        CycleCount cycleCount = CycleCount.builder()
                .location(location)
                .status(CycleCountStatus.IN_PROGRESS)
                .startedAt(OffsetDateTime.now())
                .build();

        cycleCount = cycleCountRepository.save(cycleCount);

        return mapToResponse(cycleCount);
    }

    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new WmsException("Cycle count not found"));

        if (cycleCount.getStatus() != CycleCountStatus.IN_PROGRESS) {
            throw new WmsException("Cycle count is not in progress");
        }

        // Complete cycle count
        cycleCount.setStatus(CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(OffsetDateTime.now());

        // Unfreeze location
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        cycleCount = cycleCountRepository.save(cycleCount);

        return mapToResponse(cycleCount);
    }

    private CycleCountResponse mapToResponse(CycleCount cycleCount) {
        return CycleCountResponse.builder()
                .id(cycleCount.getId())
                .locationId(cycleCount.getLocation().getId())
                .locationCode(cycleCount.getLocation().getCode())
                .status(cycleCount.getStatus())
                .startedAt(cycleCount.getStartedAt())
                .completedAt(cycleCount.getCompletedAt())
                .createdAt(cycleCount.getCreatedAt())
                .build();
    }
}
