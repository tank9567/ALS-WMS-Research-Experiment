package com.wms.repository;

import com.wms.entity.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    /**
     * 특정 상품의 재고 이동 이력 조회
     */
    Page<StockTransfer> findByProductId(UUID productId, Pageable pageable);

    /**
     * 특정 출발지 로케이션의 재고 이동 이력 조회
     */
    Page<StockTransfer> findByFromLocationId(UUID fromLocationId, Pageable pageable);

    /**
     * 특정 도착지 로케이션의 재고 이동 이력 조회
     */
    Page<StockTransfer> findByToLocationId(UUID toLocationId, Pageable pageable);

    /**
     * 특정 상태의 재고 이동 조회
     */
    Page<StockTransfer> findByTransferStatus(StockTransfer.TransferStatus transferStatus, Pageable pageable);

    /**
     * 특정 상품 + 출발지 로케이션 + 로트 + 유통기한에 해당하는 이동 이력 조회
     */
    @Query("SELECT st FROM StockTransfer st WHERE st.product.id = :productId " +
            "AND st.fromLocation.id = :fromLocationId " +
            "AND (:lotNumber IS NULL OR st.lotNumber = :lotNumber) " +
            "AND (:expiryDate IS NULL OR st.expiryDate = :expiryDate)")
    Page<StockTransfer> findByProductAndFromLocationAndLotAndExpiry(
            @Param("productId") UUID productId,
            @Param("fromLocationId") UUID fromLocationId,
            @Param("lotNumber") String lotNumber,
            @Param("expiryDate") java.time.LocalDate expiryDate,
            Pageable pageable
    );
}
