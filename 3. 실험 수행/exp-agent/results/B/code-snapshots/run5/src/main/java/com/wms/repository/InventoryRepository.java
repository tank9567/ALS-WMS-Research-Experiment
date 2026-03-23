package com.wms.repository;

import com.wms.entity.Inventory;
import com.wms.entity.Location;
import com.wms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
    Optional<Inventory> findByProductAndLocationAndLotNumber(Product product, Location location, String lotNumber);
    List<Inventory> findByLocation(Location location);
    List<Inventory> findByProductAndLocation(Product product, Location location);
}
