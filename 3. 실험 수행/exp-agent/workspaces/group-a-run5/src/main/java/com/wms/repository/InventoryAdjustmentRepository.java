package com.wms.repository;

import com.wms.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @Query("SELECT COUNT(ia) FROM InventoryAdjustment ia " +
           "WHERE ia.product.id = :productId AND ia.location.id = :locationId " +
           "AND ia.createdAt >= :since")
    long countRecentAdjustments(@Param("productId") UUID productId,
                                @Param("locationId") UUID locationId,
                                @Param("since") OffsetDateTime since);

    List<InventoryAdjustment> findByApprovalStatus(InventoryAdjustment.ApprovalStatus approvalStatus);

    @Query("SELECT ia FROM InventoryAdjustment ia " +
           "WHERE (:productId IS NULL OR ia.product.id = :productId) " +
           "AND (:locationId IS NULL OR ia.location.id = :locationId) " +
           "AND (:approvalStatus IS NULL OR ia.approvalStatus = :approvalStatus)")
    List<InventoryAdjustment> findByFilters(@Param("productId") UUID productId,
                                           @Param("locationId") UUID locationId,
                                           @Param("approvalStatus") InventoryAdjustment.ApprovalStatus approvalStatus);

    void deleteByCreatedAtBefore(OffsetDateTime cutoffDate);

    long countByCreatedAtBefore(OffsetDateTime cutoffDate);
}
