package com.wms.inbound.repository;

import com.wms.inbound.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @Modifying
    @Query("UPDATE PurchaseOrder p SET p.status = 'hold' WHERE p.supplier.supplierId = :supplierId AND p.status = 'pending'")
    void holdPendingOrdersBySupplier(@Param("supplierId") UUID supplierId);
}
