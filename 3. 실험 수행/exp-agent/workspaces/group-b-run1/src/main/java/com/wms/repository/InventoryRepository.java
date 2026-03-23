package com.wms.repository;

import com.wms.entity.Inventory;
import com.wms.entity.Location;
import com.wms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("SELECT i FROM Inventory i WHERE i.product.productId = :productId " +
           "AND i.location.locationId = :locationId " +
           "AND (i.lotNumber = :lotNumber OR (i.lotNumber IS NULL AND :lotNumber IS NULL))")
    Optional<Inventory> findByProductAndLocationAndLotNumber(
        @Param("productId") UUID productId,
        @Param("locationId") UUID locationId,
        @Param("lotNumber") String lotNumber
    );

    Optional<Inventory> findByProductAndLocationAndLotNumber(Product product, Location location, String lotNumber);

    List<Inventory> findByLocation(Location location);

    List<Inventory> findByProduct(Product product);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product JOIN FETCH i.location " +
           "WHERE i.product.productId = :productId AND i.quantity > 0 AND i.isExpired = false")
    List<Inventory> findAvailableInventoryByProductId(@Param("productId") UUID productId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i " +
           "WHERE i.product.productId = :productId AND i.isExpired = false")
    Integer getTotalAvailableQuantityByProductId(@Param("productId") UUID productId);
}
