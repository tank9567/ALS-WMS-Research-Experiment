package com.wms.repository;

import com.wms.entity.CycleCount;
import com.wms.entity.CycleCount.CycleCountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {

    List<CycleCount> findByLocationLocationIdAndStatus(UUID locationId, CycleCountStatus status);
}
