package com.wms.repository;

import com.wms.entity.InventoryAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @Query("SELECT ia FROM InventoryAdjustment ia " +
           "WHERE ia.product.productId = :productId " +
           "AND ia.location.locationId = :locationId " +
           "AND ia.createdAt >= :sinceDate")
    List<InventoryAdjustment> findRecentAdjustments(
        @Param("productId") UUID productId,
        @Param("locationId") UUID locationId,
        @Param("sinceDate") OffsetDateTime sinceDate
    );

    Page<InventoryAdjustment> findAll(Pageable pageable);
}
