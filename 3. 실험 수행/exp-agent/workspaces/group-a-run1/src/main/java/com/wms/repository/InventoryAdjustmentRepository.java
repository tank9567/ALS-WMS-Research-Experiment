package com.wms.repository;

import com.wms.entity.InventoryAdjustment;
import com.wms.entity.InventoryAdjustment.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @Query("SELECT ia FROM InventoryAdjustment ia " +
            "WHERE ia.product.id = :productId " +
            "AND ia.location.id = :locationId " +
            "AND ia.createdAt >= :startDate " +
            "AND (ia.approvalStatus = 'AUTO_APPROVED' OR ia.approvalStatus = 'APPROVED')")
    List<InventoryAdjustment> findRecentAdjustments(
            @Param("productId") UUID productId,
            @Param("locationId") UUID locationId,
            @Param("startDate") OffsetDateTime startDate
    );

    List<InventoryAdjustment> findByApprovalStatus(ApprovalStatus approvalStatus);
}
