package com.wms.repository;

import com.wms.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    List<StockTransfer> findByTransferStatus(StockTransfer.TransferStatus transferStatus);

    List<StockTransfer> findByProductProductId(UUID productId);

    List<StockTransfer> findByFromLocationLocationId(UUID locationId);

    List<StockTransfer> findByToLocationLocationId(UUID locationId);
}
