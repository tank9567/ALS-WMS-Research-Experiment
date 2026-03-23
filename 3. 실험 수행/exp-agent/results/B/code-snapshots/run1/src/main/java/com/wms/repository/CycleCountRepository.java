package com.wms.repository;

import com.wms.entity.CycleCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {
    Optional<CycleCount> findByLocationLocationIdAndStatus(UUID locationId, String status);
}
