package com.wms.repository;

import com.wms.entity.Inventory;
import com.wms.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductProductIdAndLocationLocationIdAndLotNumber(
        UUID productId, UUID locationId, String lotNumber);

    List<Inventory> findByLocationLocationId(UUID locationId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i " +
            "WHERE i.product.productId = :productId " +
            "AND i.location.zone = :zone " +
            "AND i.isExpired = false")
    int sumQuantityByProductAndZone(@Param("productId") UUID productId, @Param("zone") Location.Zone zone);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i " +
            "WHERE i.product.productId = :productId " +
            "AND i.location.locationId = :locationId")
    int sumQuantityByProductAndLocation(@Param("productId") UUID productId, @Param("locationId") UUID locationId);

    @Query("SELECT i FROM Inventory i " +
            "WHERE i.product.productId = :productId " +
            "AND i.location.locationId = :locationId")
    List<Inventory> findByProductAndLocation(@Param("productId") UUID productId, @Param("locationId") UUID locationId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i " +
            "WHERE i.product.productId = :productId " +
            "AND i.isExpired = false")
    int sumAvailableQuantityByProduct(@Param("productId") UUID productId);
}
