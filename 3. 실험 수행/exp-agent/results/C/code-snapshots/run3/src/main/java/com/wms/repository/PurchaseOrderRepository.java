package com.wms.repository;

import com.wms.entity.PurchaseOrder;
import com.wms.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    List<PurchaseOrder> findBySupplierAndStatus(Supplier supplier, PurchaseOrder.Status status);
}
