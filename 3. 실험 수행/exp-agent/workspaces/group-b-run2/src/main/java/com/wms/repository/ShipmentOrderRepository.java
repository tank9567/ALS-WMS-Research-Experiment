package com.wms.repository;

import com.wms.entity.ShipmentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentOrderRepository extends JpaRepository<ShipmentOrder, UUID> {

    @Query("SELECT s FROM ShipmentOrder s LEFT JOIN FETCH s.lines WHERE s.shipmentId = :shipmentId")
    Optional<ShipmentOrder> findByIdWithLines(@Param("shipmentId") UUID shipmentId);

    Optional<ShipmentOrder> findByShipmentNumber(String shipmentNumber);
}
