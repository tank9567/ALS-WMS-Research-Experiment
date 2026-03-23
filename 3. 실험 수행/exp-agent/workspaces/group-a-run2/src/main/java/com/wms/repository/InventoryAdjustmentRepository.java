package com.wms.repository;

import com.wms.entity.InventoryAdjustment;
import com.wms.entity.Location;
import com.wms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @Query("SELECT ia FROM InventoryAdjustment ia " +
           "WHERE ia.product = :product " +
           "AND ia.location = :location " +
           "AND ia.createdAt >= :since " +
           "AND ia.approvalStatus IN ('AUTO_APPROVED', 'APPROVED')")
    List<InventoryAdjustment> findRecentApprovedAdjustments(
        @Param("product") Product product,
        @Param("location") Location location,
        @Param("since") Instant since
    );

    List<InventoryAdjustment> findByApprovalStatus(InventoryAdjustment.ApprovalStatus status);

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.createdAt < :cutoffDate")
    List<InventoryAdjustment> findOlderThan(@Param("cutoffDate") Instant cutoffDate);
}
