package com.wms.repository;

import com.wms.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.location.id = :locationId AND i.lotNumber = :lotNumber")
    Optional<Inventory> findByProductAndLocationAndLot(
        @Param("productId") UUID productId,
        @Param("locationId") UUID locationId,
        @Param("lotNumber") String lotNumber
    );

    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.quantity > 0 AND i.expired = false AND i.location.isFrozen = false ORDER BY i.receivedAt ASC")
    List<Inventory> findAvailableInventoryForProduct(@Param("productId") UUID productId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.product.id = :productId AND i.expired = false")
    int getTotalAvailableQuantity(@Param("productId") UUID productId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.product = :product AND i.expired = false")
    Integer sumAvailableQuantityByProduct(@Param("product") com.wms.entity.Product product);
}
