package com.wms.repository;

import com.wms.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.location.locationId = :locationId AND ia.product.productId = :productId AND ia.createdAt >= :since")
    List<InventoryAdjustment> findRecentAdjustments(
        @Param("locationId") UUID locationId,
        @Param("productId") UUID productId,
        @Param("since") OffsetDateTime since
    );

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.createdAt < :before")
    List<InventoryAdjustment> findOldAdjustments(@Param("before") OffsetDateTime before);

    @Modifying
    @Query("DELETE FROM InventoryAdjustment ia WHERE ia.createdAt < :before")
    void deleteOldAdjustments(@Param("before") OffsetDateTime before);
}
