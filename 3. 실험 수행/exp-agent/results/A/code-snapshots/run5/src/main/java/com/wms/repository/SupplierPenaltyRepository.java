package com.wms.repository;

import com.wms.entity.SupplierPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {

    @Query("SELECT COUNT(p) FROM SupplierPenalty p WHERE p.supplier.id = :supplierId AND p.occurredAt >= :since")
    long countBySupplierId(@Param("supplierId") UUID supplierId, @Param("since") OffsetDateTime since);
}
