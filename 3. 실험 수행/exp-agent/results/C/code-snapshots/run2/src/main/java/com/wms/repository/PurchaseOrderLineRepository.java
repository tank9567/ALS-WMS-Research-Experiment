package com.wms.repository;

import com.wms.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    List<PurchaseOrderLine> findByPoId(UUID poId);

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.poId = :poId AND pol.productId = :productId")
    Optional<PurchaseOrderLine> findByPoIdAndProductId(@Param("poId") UUID poId, @Param("productId") UUID productId);
}
