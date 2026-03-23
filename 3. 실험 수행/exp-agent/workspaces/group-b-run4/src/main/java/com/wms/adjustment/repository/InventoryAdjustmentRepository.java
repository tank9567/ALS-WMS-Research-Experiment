package com.wms.adjustment.repository;

import com.wms.adjustment.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @Query("SELECT COUNT(ia) FROM InventoryAdjustment ia " +
           "WHERE ia.location.locationId = :locationId " +
           "AND ia.product.productId = :productId " +
           "AND ia.createdAt >= :since")
    long countRecentAdjustments(@Param("locationId") UUID locationId,
                                @Param("productId") UUID productId,
                                @Param("since") ZonedDateTime since);

    List<InventoryAdjustment> findByApprovalStatus(com.wms.adjustment.entity.ApprovalStatus approvalStatus);

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.createdAt < :cutoffDate")
    List<InventoryAdjustment> findOldAdjustments(@Param("cutoffDate") ZonedDateTime cutoffDate);
}
