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

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.location.id = :locationId AND ia.product.id = :productId AND ia.createdAt >= :since")
    List<InventoryAdjustment> findRecentAdjustments(
            @Param("locationId") UUID locationId,
            @Param("productId") UUID productId,
            @Param("since") OffsetDateTime since);

    @Query("SELECT COUNT(ia) FROM InventoryAdjustment ia WHERE ia.location.id = :locationId AND ia.product.id = :productId AND ia.createdAt >= :since")
    long countRecentAdjustments(
            @Param("locationId") UUID locationId,
            @Param("productId") UUID productId,
            @Param("since") OffsetDateTime since);

    List<InventoryAdjustment> findByProductId(UUID productId);

    List<InventoryAdjustment> findByLocationId(UUID locationId);

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.createdAt < :cutoffDate")
    List<InventoryAdjustment> findOldAdjustments(@Param("cutoffDate") OffsetDateTime cutoffDate);
}
