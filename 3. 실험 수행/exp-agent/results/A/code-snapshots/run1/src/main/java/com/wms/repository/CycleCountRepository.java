package com.wms.repository;

import com.wms.entity.CycleCount;
import com.wms.entity.CycleCount.CycleCountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {

    List<CycleCount> findByLocationIdAndStatus(UUID locationId, CycleCountStatus status);

    @Query("SELECT cc FROM CycleCount cc WHERE cc.location.id = :locationId AND cc.status = :status")
    List<CycleCount> findInProgressByLocation(@Param("locationId") UUID locationId, @Param("status") CycleCountStatus status);
}
