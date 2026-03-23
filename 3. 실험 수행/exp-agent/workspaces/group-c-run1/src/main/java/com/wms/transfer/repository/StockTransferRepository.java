package com.wms.transfer.repository;

import com.wms.transfer.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    @Query("SELECT st FROM StockTransfer st WHERE st.product.productId = :productId ORDER BY st.createdAt DESC")
    List<StockTransfer> findByProductId(@Param("productId") UUID productId);

    @Query("SELECT st FROM StockTransfer st WHERE st.fromLocation.locationId = :locationId OR st.toLocation.locationId = :locationId ORDER BY st.createdAt DESC")
    List<StockTransfer> findByLocationId(@Param("locationId") UUID locationId);

    @Query("SELECT st FROM StockTransfer st WHERE st.transferStatus = :status ORDER BY st.createdAt DESC")
    List<StockTransfer> findByStatus(@Param("status") StockTransfer.TransferStatus status);
}
