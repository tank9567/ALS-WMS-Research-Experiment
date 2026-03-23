package com.wms.repository;

import com.wms.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.product.productId = :productId AND ia.location.locationId = :locationId AND ia.createdAt >= :since")
    List<InventoryAdjustment> findRecentAdjustments(@Param("productId") UUID productId, @Param("locationId") UUID locationId, @Param("since") Instant since);

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.approvalStatus = 'PENDING'")
    List<InventoryAdjustment> findAllPendingAdjustments();
}
