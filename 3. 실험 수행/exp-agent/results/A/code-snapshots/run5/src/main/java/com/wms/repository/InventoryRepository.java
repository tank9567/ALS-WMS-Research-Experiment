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

    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.location.id = :locationId " +
           "AND i.lotNumber = :lotNumber AND i.expiryDate = :expiryDate")
    Optional<Inventory> findByProductAndLocationAndLotAndExpiry(
            @Param("productId") UUID productId,
            @Param("locationId") UUID locationId,
            @Param("lotNumber") String lotNumber,
            @Param("expiryDate") java.time.LocalDate expiryDate);

    List<Inventory> findByProductId(UUID productId);

    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.location.id = :locationId")
    List<Inventory> findByProductAndLocation(@Param("productId") UUID productId,
                                             @Param("locationId") UUID locationId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i WHERE i.product.id = :productId AND i.quantity > 0")
    Integer getTotalAvailableQuantity(@Param("productId") UUID productId);
}
