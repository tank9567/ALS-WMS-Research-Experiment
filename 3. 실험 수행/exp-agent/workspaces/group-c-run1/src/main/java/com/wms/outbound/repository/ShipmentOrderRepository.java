package com.wms.outbound.repository;

import com.wms.outbound.entity.ShipmentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentOrderRepository extends JpaRepository<ShipmentOrder, UUID> {
    Optional<ShipmentOrder> findByOrderNumber(String orderNumber);
}
