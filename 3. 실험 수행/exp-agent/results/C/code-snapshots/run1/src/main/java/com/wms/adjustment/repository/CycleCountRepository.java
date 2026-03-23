package com.wms.adjustment.repository;

import com.wms.adjustment.entity.CycleCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {

    Optional<CycleCount> findByLocationLocationIdAndStatus(UUID locationId, CycleCount.CycleCountStatus status);

    List<CycleCount> findByStatus(CycleCount.CycleCountStatus status);
}
