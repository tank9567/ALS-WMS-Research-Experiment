package com.wms.repository;

import com.wms.entity.AdjustmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AdjustmentHistoryRepository extends JpaRepository<AdjustmentHistory, UUID> {

    List<AdjustmentHistory> findByProductIdOrderByAdjustedAtDesc(UUID productId);

    List<AdjustmentHistory> findByLocationIdOrderByAdjustedAtDesc(UUID locationId);

    List<AdjustmentHistory> findByAdjustedAtBetweenOrderByAdjustedAtDesc(
            OffsetDateTime startDate,
            OffsetDateTime endDate
    );

    @Modifying
    @Query("DELETE FROM AdjustmentHistory ah WHERE ah.adjustedAt < :cutoffDate")
    int deleteOldHistories(@Param("cutoffDate") OffsetDateTime cutoffDate);

    @Query("SELECT COUNT(ah) FROM AdjustmentHistory ah WHERE ah.adjustedAt < :cutoffDate")
    long countOldHistories(@Param("cutoffDate") OffsetDateTime cutoffDate);
}
