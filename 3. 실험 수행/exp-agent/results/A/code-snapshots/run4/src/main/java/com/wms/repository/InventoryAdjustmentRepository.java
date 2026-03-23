package com.wms.repository;

import com.wms.entity.InventoryAdjustment;
import com.wms.enums.AdjustmentApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    List<InventoryAdjustment> findByProductIdAndLocationIdAndCreatedAtAfter(
        UUID productId, UUID locationId, OffsetDateTime createdAt);

    List<InventoryAdjustment> findByApprovalStatus(AdjustmentApprovalStatus status);

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.product.id = :productId AND ia.location.id = :locationId " +
           "AND ia.approvalStatus IN ('AUTO_APPROVED', 'APPROVED') AND ia.createdAt > :since")
    List<InventoryAdjustment> findApprovedAdjustmentsForLocationProduct(
        @Param("productId") UUID productId,
        @Param("locationId") UUID locationId,
        @Param("since") OffsetDateTime since);

    List<InventoryAdjustment> findByProductId(UUID productId);

    List<InventoryAdjustment> findByLocationId(UUID locationId);

    List<InventoryAdjustment> findByCreatedAtBetween(OffsetDateTime startDate, OffsetDateTime endDate);

    List<InventoryAdjustment> findByCreatedAtBefore(OffsetDateTime cutoffDate);
}
