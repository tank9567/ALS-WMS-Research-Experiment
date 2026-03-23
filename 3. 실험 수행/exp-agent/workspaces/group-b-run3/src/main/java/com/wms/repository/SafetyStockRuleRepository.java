package com.wms.repository;

import com.wms.entity.SafetyStockRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SafetyStockRuleRepository extends JpaRepository<SafetyStockRule, UUID> {
    @Query("SELECT s FROM SafetyStockRule s WHERE s.product.productId = :productId")
    Optional<SafetyStockRule> findByProductId(@Param("productId") UUID productId);
}
