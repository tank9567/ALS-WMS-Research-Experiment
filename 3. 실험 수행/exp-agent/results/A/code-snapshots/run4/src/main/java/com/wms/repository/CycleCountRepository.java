package com.wms.repository;

import com.wms.entity.CycleCount;
import com.wms.enums.CycleCountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {

    Optional<CycleCount> findByLocationIdAndStatus(UUID locationId, CycleCountStatus status);

    List<CycleCount> findByLocationId(UUID locationId);
}
