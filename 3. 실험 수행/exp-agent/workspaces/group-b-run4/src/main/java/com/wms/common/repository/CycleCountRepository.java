package com.wms.common.repository;

import com.wms.common.entity.CycleCount;
import com.wms.common.entity.CycleCountStatus;
import com.wms.inbound.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {
    Optional<CycleCount> findByLocationAndStatus(Location location, CycleCountStatus status);
}
