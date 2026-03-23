package com.wms.inbound.repository;

import com.wms.inbound.entity.PurchaseOrder;
import com.wms.inbound.entity.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    @Modifying
    @Query("UPDATE PurchaseOrder po SET po.status = :newStatus WHERE po.supplier.supplierId = :supplierId " +
           "AND po.status = :currentStatus")
    int updateStatusBySupplierIdAndCurrentStatus(
        @Param("supplierId") UUID supplierId,
        @Param("currentStatus") PurchaseOrderStatus currentStatus,
        @Param("newStatus") PurchaseOrderStatus newStatus
    );
}
