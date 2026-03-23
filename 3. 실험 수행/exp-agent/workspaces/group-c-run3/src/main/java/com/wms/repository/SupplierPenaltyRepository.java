package com.wms.repository;

import com.wms.entity.Supplier;
import com.wms.entity.SupplierPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {
    List<SupplierPenalty> findBySupplierAndCreatedAtAfter(Supplier supplier, OffsetDateTime since);
}
