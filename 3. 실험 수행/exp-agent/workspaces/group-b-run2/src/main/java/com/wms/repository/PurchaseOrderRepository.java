package com.wms.repository;

import com.wms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    @Query("SELECT po FROM PurchaseOrder po LEFT JOIN FETCH po.lines WHERE po.poId = :poId")
    Optional<PurchaseOrder> findByIdWithLines(@Param("poId") UUID poId);

    List<PurchaseOrder> findBySupplierSupplierIdAndStatus(UUID supplierId, PurchaseOrder.PoStatus status);
}
