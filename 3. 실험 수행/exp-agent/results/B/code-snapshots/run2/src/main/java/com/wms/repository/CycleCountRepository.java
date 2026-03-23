package com.wms.repository;

import com.wms.entity.CycleCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {

    @Query("SELECT cc FROM CycleCount cc WHERE cc.location.locationId = :locationId AND cc.status = 'IN_PROGRESS'")
    Optional<CycleCount> findInProgressByLocationId(@Param("locationId") UUID locationId);
}
