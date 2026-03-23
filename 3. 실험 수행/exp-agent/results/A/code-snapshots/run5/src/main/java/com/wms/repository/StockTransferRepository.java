package com.wms.repository;

import com.wms.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    List<StockTransfer> findByInventoryProductId(UUID productId);

    List<StockTransfer> findByFromLocationId(UUID locationId);

    List<StockTransfer> findByToLocationId(UUID locationId);

    List<StockTransfer> findByTransferStatus(StockTransfer.TransferStatus status);
}
