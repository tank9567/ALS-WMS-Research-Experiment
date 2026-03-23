package com.wms.repository;

import com.wms.entity.Inventory;
import com.wms.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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
        @Param("expiryDate") LocalDate expiryDate
    );

    /**
     * 특정 로케이션의 모든 재고 조회
     */
    @Query("SELECT i FROM Inventory i WHERE i.location.id = :locationId")
    List<Inventory> findByLocationId(@Param("locationId") UUID locationId);

    /**
     * 특정 상품의 특정 zone 내 재고 조회
     */
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.location.zone = :zone")
    List<Inventory> findByProductIdAndZone(@Param("productId") UUID productId, @Param("zone") Location.Zone zone);

    /**
     * 특정 상품의 모든 재고 조회
     */
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId")
    List<Inventory> findByProductId(@Param("productId") UUID productId);
}
