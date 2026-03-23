package com.wms.repository;

import com.wms.entity.InboundReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID> {

    @Query("SELECT ir FROM InboundReceipt ir LEFT JOIN FETCH ir.lines WHERE ir.receiptId = :receiptId")
    Optional<InboundReceipt> findByIdWithLines(@Param("receiptId") UUID receiptId);
}
