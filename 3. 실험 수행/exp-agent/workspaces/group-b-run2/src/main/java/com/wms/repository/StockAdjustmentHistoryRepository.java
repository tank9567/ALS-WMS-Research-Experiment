package com.wms.repository;

import com.wms.entity.StockAdjustmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockAdjustmentHistoryRepository extends JpaRepository<StockAdjustmentHistory, UUID> {

    List<StockAdjustmentHistory> findByAdjustmentId(UUID adjustmentId);

    @Modifying
    @Query("DELETE FROM StockAdjustmentHistory h WHERE h.createdAt < :cutoffDate")
    int deleteByCreatedAtBefore(@Param("cutoffDate") Instant cutoffDate);

    @Query("SELECT h FROM StockAdjustmentHistory h WHERE h.createdAt >= :fromDate ORDER BY h.createdAt DESC")
    List<StockAdjustmentHistory> findRecentHistory(@Param("fromDate") Instant fromDate);

    @Query("SELECT h FROM StockAdjustmentHistory h WHERE h.product.productId = :productId AND h.location.locationId = :locationId ORDER BY h.createdAt DESC")
    List<StockAdjustmentHistory> findByProductAndLocation(@Param("productId") UUID productId, @Param("locationId") UUID locationId);
}
