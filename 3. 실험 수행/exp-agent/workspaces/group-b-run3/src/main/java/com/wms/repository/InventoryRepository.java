package com.wms.repository;

import com.wms.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
    Optional<Inventory> findByProductProductIdAndLocationLocationIdAndLotNumber(
        UUID productId, UUID locationId, String lotNumber);

    List<Inventory> findByLocationLocationId(UUID locationId);

    List<Inventory> findByProductProductId(UUID productId);

    List<Inventory> findByProductProductIdAndLocationLocationId(UUID productId, UUID locationId);
}
