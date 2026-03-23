package com.wms.repository;

import com.wms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @Modifying
    @Query("UPDATE PurchaseOrder po SET po.status = 'hold' WHERE po.supplier.id = :supplierId AND po.status = 'pending'")
    void holdPendingOrdersBySupplierId(@Param("supplierId") UUID supplierId);
}
