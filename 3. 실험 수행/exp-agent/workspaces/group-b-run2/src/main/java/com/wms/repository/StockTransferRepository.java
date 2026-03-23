package com.wms.repository;

import com.wms.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    @Query("SELECT st FROM StockTransfer st " +
            "LEFT JOIN FETCH st.product p " +
            "LEFT JOIN FETCH st.fromLocation fl " +
            "LEFT JOIN FETCH st.toLocation tl " +
            "WHERE st.transferId = :transferId")
    Optional<StockTransfer> findByIdWithDetails(@Param("transferId") UUID transferId);

    @Query("SELECT st FROM StockTransfer st " +
            "LEFT JOIN FETCH st.product p " +
            "LEFT JOIN FETCH st.fromLocation fl " +
            "LEFT JOIN FETCH st.toLocation tl " +
            "WHERE st.transferStatus = :status")
    List<StockTransfer> findByTransferStatusWithDetails(@Param("status") StockTransfer.TransferStatus status);
}
