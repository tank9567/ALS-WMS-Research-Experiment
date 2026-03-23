package com.wms.repository;

import com.wms.entity.SeasonalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeasonalConfigRepository extends JpaRepository<SeasonalConfig, UUID> {
    @Query("SELECT s FROM SeasonalConfig s WHERE s.isActive = true " +
           "AND s.startDate <= :date AND s.endDate >= :date")
    Optional<SeasonalConfig> findActiveSeasonForDate(@Param("date") LocalDate date);
}
