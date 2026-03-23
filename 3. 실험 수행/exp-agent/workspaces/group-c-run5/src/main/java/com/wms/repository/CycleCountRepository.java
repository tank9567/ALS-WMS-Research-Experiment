package com.wms.repository;

import com.wms.entity.CycleCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {
    Optional<CycleCount> findByLocation_LocationIdAndStatus(UUID locationId, CycleCount.CycleCountStatus status);
    List<CycleCount> findByLocation_LocationId(UUID locationId);
}
