# CLI Output

Error: Reached max turns (50)

# Generated Code


============================================================
// FILE: pom.xml
============================================================
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.wms</groupId>
    <artifactId>wms-inbound</artifactId>
    <version>1.0.0</version>
    <name>WMS Inbound Processing</name>
    <description>Warehouse Management System - Inbound Receipt Processing</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- PostgreSQL Driver -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>


============================================================
// FILE: src\main\java\com\wms\inbound\dto\ApiResponse.java
============================================================
package com.wms.inbound.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorInfo error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .error(new ErrorInfo(code, message))
            .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorInfo {
        private String code;
        private String message;
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\dto\InboundReceiptCreateRequest.java
============================================================
package com.wms.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptCreateRequest {

    @NotNull(message = "PO ID는 필수입니다")
    private UUID poId;

    @NotBlank(message = "수령자는 필수입니다")
    private String receivedBy;

    @NotEmpty(message = "입고 품목은 최소 1개 이상이어야 합니다")
    @Valid
    private List<InboundReceiptLineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineRequest {

        @NotNull(message = "상품 ID는 필수입니다")
        private UUID productId;

        @NotNull(message = "로케이션 ID는 필수입니다")
        private UUID locationId;

        @NotNull(message = "수량은 필수입니다")
        private Integer quantity;

        private String lotNumber;

        private LocalDate expiryDate;

        private LocalDate manufactureDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\dto\InboundReceiptResponse.java
============================================================
package com.wms.inbound.dto;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptResponse {

    private UUID receiptId;
    private UUID poId;
    private String poNumber;
    private String status;
    private String receivedBy;
    private Instant receivedAt;
    private Instant confirmedAt;
    private List<InboundReceiptLineResponse> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineResponse {
        private UUID receiptLineId;
        private UUID productId;
        private String productSku;
        private String productName;
        private UUID locationId;
        private String locationCode;
        private Integer quantity;
        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\InboundReceipt.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceipt {

    @Id
    @Column(name = "receipt_id", nullable = false)
    private UUID receiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReceiptStatus status;

    @Column(name = "received_by", nullable = false, length = 100)
    private String receivedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum ReceiptStatus {
        inspecting, pending_approval, confirmed, rejected
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\InboundReceiptLine.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptLine {

    @Id
    @Column(name = "receipt_line_id", nullable = false)
    private UUID receiptLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private InboundReceipt inboundReceipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\Inventory.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "location_id", "lot_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @Column(name = "inventory_id", nullable = false)
    private UUID inventoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "is_expired", nullable = false)
    private Boolean isExpired;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\Location.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Id
    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false, length = 50)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private Product.StorageType storageType;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "current_qty", nullable = false)
    private Integer currentQty;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "is_frozen", nullable = false)
    private Boolean isFrozen;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum Zone {
        RECEIVING, STORAGE, SHIPPING, HAZMAT
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\Product.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "sku", nullable = false, unique = true, length = 50)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ProductCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private StorageType storageType;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "has_expiry", nullable = false)
    private Boolean hasExpiry;

    @Column(name = "min_remaining_shelf_life_pct")
    private Integer minRemainingShelfLifePct;

    @Column(name = "max_pick_qty")
    private Integer maxPickQty;

    @Column(name = "manufacture_date_required", nullable = false)
    private Boolean manufactureDateRequired;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum ProductCategory {
        GENERAL, FRESH, HAZMAT, HIGH_VALUE
    }

    public enum StorageType {
        AMBIENT, COLD, FROZEN
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\PurchaseOrder.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrder {

    @Id
    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @Column(name = "po_number", nullable = false, unique = true, length = 30)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "po_type", nullable = false, length = 20)
    private PoType poType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PoStatus status;

    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum PoType {
        NORMAL, URGENT, IMPORT
    }

    public enum PoStatus {
        pending, partial, completed, cancelled, hold
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\PurchaseOrderLine.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"po_id", "product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderLine {

    @Id
    @Column(name = "po_line_id", nullable = false)
    private UUID poLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "ordered_qty", nullable = false)
    private Integer orderedQty;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\SafetyStockRule.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "safety_stock_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SafetyStockRule {

    @Id
    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @OneToOne
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\SeasonalConfig.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "seasonal_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeasonalConfig {

    @Id
    @Column(name = "season_id", nullable = false)
    private UUID seasonId;

    @Column(name = "season_name", nullable = false, length = 100)
    private String seasonName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "multiplier", nullable = false, precision = 3, scale = 2)
    private BigDecimal multiplier;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\Supplier.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_info", length = 500)
    private String contactInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum SupplierStatus {
        active, hold, inactive
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\entity\SupplierPenalty.java
============================================================
package com.wms.inbound.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_penalties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierPenalty {

    @Id
    @Column(name = "penalty_id", nullable = false)
    private UUID penaltyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", nullable = false, length = 50)
    private PenaltyType penaltyType;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "po_id")
    private UUID poId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum PenaltyType {
        OVER_DELIVERY, SHORT_SHELF_LIFE
    }
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\InboundReceiptLineRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.InboundReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InboundReceiptLineRepository extends JpaRepository<InboundReceiptLine, UUID> {

    @Query("SELECT irl FROM InboundReceiptLine irl WHERE irl.inboundReceipt.receiptId = :receiptId")
    List<InboundReceiptLine> findByReceiptId(@Param("receiptId") UUID receiptId);
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\InboundReceiptRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.InboundReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\InventoryRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("SELECT i FROM Inventory i WHERE i.product.productId = :productId " +
           "AND i.location.locationId = :locationId AND i.lotNumber = :lotNumber")
    Optional<Inventory> findByProductAndLocationAndLot(
        @Param("productId") UUID productId,
        @Param("locationId") UUID locationId,
        @Param("lotNumber") String lotNumber
    );
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\LocationRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\ProductRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\PurchaseOrderLineRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.purchaseOrder.poId = :poId " +
           "AND pol.product.productId = :productId")
    Optional<PurchaseOrderLine> findByPoAndProduct(@Param("poId") UUID poId, @Param("productId") UUID productId);

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.purchaseOrder.poId = :poId")
    List<PurchaseOrderLine> findByPoId(@Param("poId") UUID poId);
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\PurchaseOrderRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @Modifying
    @Query("UPDATE PurchaseOrder p SET p.status = 'hold' WHERE p.supplier.supplierId = :supplierId AND p.status = 'pending'")
    void holdPendingOrdersBySupplier(@Param("supplierId") UUID supplierId);
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\SeasonalConfigRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.SeasonalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeasonalConfigRepository extends JpaRepository<SeasonalConfig, UUID> {

    @Query("SELECT sc FROM SeasonalConfig sc WHERE sc.isActive = true " +
           "AND :date BETWEEN sc.startDate AND sc.endDate")
    Optional<SeasonalConfig> findActiveSeason(@Param("date") LocalDate date);
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\SupplierPenaltyRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.SupplierPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {

    @Query("SELECT COUNT(p) FROM SupplierPenalty p WHERE p.supplier.supplierId = :supplierId " +
           "AND p.createdAt >= :since")
    long countBySupplierId30Days(@Param("supplierId") UUID supplierId, @Param("since") Instant since);
}


============================================================
// FILE: src\main\java\com\wms\inbound\repository\SupplierRepository.java
============================================================
package com.wms.inbound.repository;

import com.wms.inbound.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\inbound\service\InboundReceiptService.java
============================================================
package com.wms.inbound.service;

import com.wms.inbound.dto.InboundReceiptCreateRequest;
import com.wms.inbound.dto.InboundReceiptResponse;
import com.wms.inbound.entity.*;
import com.wms.inbound.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final InboundReceiptLineRepository inboundReceiptLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    /**
     * 입고 등록 (검수 상태)
     */
    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptCreateRequest request) {
        // 1. PO 검증
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
            .orElseThrow(() -> new IllegalArgumentException("발주서를 찾을 수 없습니다"));

        // 2. 입고 Receipt 생성
        InboundReceipt receipt = InboundReceipt.builder()
            .receiptId(UUID.randomUUID())
            .purchaseOrder(po)
            .status(InboundReceipt.ReceiptStatus.inspecting)
            .receivedBy(request.getReceivedBy())
            .receivedAt(Instant.now())
            .build();

        InboundReceipt.ReceiptStatus finalStatus = InboundReceipt.ReceiptStatus.inspecting;

        // 3. 각 품목별 검증 및 입고 라인 생성
        for (InboundReceiptCreateRequest.InboundReceiptLineRequest lineRequest : request.getLines()) {
            Product product = productRepository.findById(lineRequest.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineRequest.getProductId()));

            Location location = locationRepository.findById(lineRequest.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + lineRequest.getLocationId()));

            // 3-1. 실사 동결 체크
            if (Boolean.TRUE.equals(location.getIsFrozen())) {
                throw new IllegalStateException("실사 동결된 로케이션에는 입고할 수 없습니다: " + location.getCode());
            }

            // 3-2. 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3-3. 유통기한 관리 상품 체크
            if (Boolean.TRUE.equals(product.getHasExpiry())) {
                if (lineRequest.getExpiryDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 상품은 유통기한이 필수입니다: " + product.getSku());
                }
                if (lineRequest.getManufactureDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 상품은 제조일이 필수입니다: " + product.getSku());
                }

                // 3-4. 유통기한 잔여율 체크
                double remainingPct = calculateRemainingShelfLifePct(
                    lineRequest.getManufactureDate(),
                    lineRequest.getExpiryDate(),
                    LocalDate.now()
                );

                int minPct = product.getMinRemainingShelfLifePct() != null ?
                    product.getMinRemainingShelfLifePct() : 30;

                if (remainingPct < minPct) {
                    // 잔여율 < 30% → 입고 거부 + 페널티
                    recordSupplierPenalty(po, SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                        String.format("유통기한 부족: %s (잔여율 %.1f%% < %d%%)",
                            product.getSku(), remainingPct, minPct));
                    throw new IllegalStateException(
                        String.format("유통기한 잔여율 부족: %s (%.1f%% < %d%%)",
                            product.getSku(), remainingPct, minPct));
                } else if (remainingPct >= minPct && remainingPct <= 50) {
                    // 잔여율 30~50% → 승인 대기
                    finalStatus = InboundReceipt.ReceiptStatus.pending_approval;
                }
            }

            // 3-5. 초과입고 검증
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findByPoAndProduct(po.getPoId(), product.getProductId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "발주서에 해당 상품이 없습니다: " + product.getSku()));

            validateOverReceive(po, product, poLine, lineRequest.getQuantity());

            // 3-6. 입고 라인 생성
            InboundReceiptLine receiptLine = InboundReceiptLine.builder()
                .receiptLineId(UUID.randomUUID())
                .inboundReceipt(receipt)
                .product(product)
                .location(location)
                .quantity(lineRequest.getQuantity())
                .lotNumber(lineRequest.getLotNumber())
                .expiryDate(lineRequest.getExpiryDate())
                .manufactureDate(lineRequest.getManufactureDate())
                .build();

            inboundReceiptLineRepository.save(receiptLine);
        }

        // 4. 최종 상태 설정 및 저장
        receipt.setStatus(finalStatus);
        inboundReceiptRepository.save(receipt);

        return mapToResponse(receipt);
    }

    /**
     * 입고 확정
     */
    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("검수 중 또는 승인 대기 상태에서만 확정할 수 있습니다");
        }

        // 1. 재고 반영
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receiptId);
        for (InboundReceiptLine line : lines) {
            // 재고 추가
            Inventory inventory = inventoryRepository.findByProductAndLocationAndLot(
                line.getProduct().getProductId(),
                line.getLocation().getLocationId(),
                line.getLotNumber()
            ).orElse(null);

            if (inventory != null) {
                inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
            } else {
                inventory = Inventory.builder()
                    .inventoryId(UUID.randomUUID())
                    .product(line.getProduct())
                    .location(line.getLocation())
                    .quantity(line.getQuantity())
                    .lotNumber(line.getLotNumber())
                    .expiryDate(line.getExpiryDate())
                    .manufactureDate(line.getManufactureDate())
                    .receivedAt(Instant.now())
                    .isExpired(false)
                    .build();
            }
            inventoryRepository.save(inventory);

            // 로케이션 적재량 증가
            Location location = line.getLocation();
            location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
            locationRepository.save(location);

            // PO 라인 received_qty 갱신
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findByPoAndProduct(
                receipt.getPurchaseOrder().getPoId(),
                line.getProduct().getProductId()
            ).orElseThrow();

            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // 2. PO 상태 갱신
        updatePurchaseOrderStatus(receipt.getPurchaseOrder().getPoId());

        // 3. 입고 전표 상태 갱신
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(Instant.now());
        inboundReceiptRepository.save(receipt);

        return mapToResponse(receipt);
    }

    /**
     * 입고 거부
     */
    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("검수 중 또는 승인 대기 상태에서만 거부할 수 있습니다");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        inboundReceiptRepository.save(receipt);

        return mapToResponse(receipt);
    }

    /**
     * 유통기한 경고 승인
     */
    @Transactional
    public InboundReceiptResponse approveShelfLifeWarning(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태에서만 승인할 수 있습니다");
        }

        // 승인 시 검수 중 상태로 변경 (이후 confirm으로 확정)
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        inboundReceiptRepository.save(receipt);

        return mapToResponse(receipt);
    }

    /**
     * 입고 상세 조회
     */
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다"));

        return mapToResponse(receipt);
    }

    /**
     * 입고 목록 조회
     */
    public Page<InboundReceiptResponse> getInboundReceipts(Pageable pageable) {
        return inboundReceiptRepository.findAll(pageable)
            .map(this::mapToResponse);
    }

    // ===== 내부 유틸리티 메서드 =====

    /**
     * 보관 유형 호환성 검증
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new IllegalStateException(
                    "위험물은 HAZMAT zone 로케이션에만 입고할 수 있습니다");
            }
        }

        if (productType == Product.StorageType.FROZEN) {
            if (locationType != Product.StorageType.FROZEN) {
                throw new IllegalStateException(
                    "FROZEN 상품은 FROZEN 로케이션에만 입고할 수 있습니다");
            }
        } else if (productType == Product.StorageType.COLD) {
            if (locationType != Product.StorageType.COLD &&
                locationType != Product.StorageType.FROZEN) {
                throw new IllegalStateException(
                    "COLD 상품은 COLD 또는 FROZEN 로케이션에만 입고할 수 있습니다");
            }
        } else if (productType == Product.StorageType.AMBIENT) {
            if (locationType != Product.StorageType.AMBIENT) {
                throw new IllegalStateException(
                    "AMBIENT 상품은 AMBIENT 로케이션에만 입고할 수 있습니다");
            }
        }
    }

    /**
     * 유통기한 잔여율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate,
                                                   LocalDate expiryDate,
                                                   LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    /**
     * 초과입고 검증
     */
    private void validateOverReceive(PurchaseOrder po, Product product,
                                      PurchaseOrderLine poLine, int incomingQty) {
        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int totalReceiving = receivedQty + incomingQty;

        // 카테고리별 기본 허용률
        double baseTolerance = getCategoryTolerance(product.getCategory());

        // HAZMAT은 무조건 0%
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            baseTolerance = 0.0;
        } else {
            // PO 유형별 가중치
            double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());
            baseTolerance *= poTypeMultiplier;

            // 성수기 가중치
            BigDecimal seasonMultiplier = getSeasonalMultiplier(LocalDate.now());
            baseTolerance *= seasonMultiplier.doubleValue();
        }

        int maxAllowed = (int) Math.floor(orderedQty * (1.0 + baseTolerance));

        if (totalReceiving > maxAllowed) {
            // 초과입고 → 페널티 기록 후 거부
            recordSupplierPenalty(po, SupplierPenalty.PenaltyType.OVER_DELIVERY,
                String.format("초과입고: %s (입고 %d > 허용 %d)",
                    product.getSku(), totalReceiving, maxAllowed));

            throw new IllegalStateException(
                String.format("초과입고 거부: %s (입고 %d > 허용 %d, 허용률 %.1f%%)",
                    product.getSku(), totalReceiving, maxAllowed, baseTolerance * 100));
        }
    }

    /**
     * 카테고리별 초과입고 허용률
     */
    private double getCategoryTolerance(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 0.10;  // 10%
            case FRESH -> 0.05;    // 5%
            case HAZMAT -> 0.0;    // 0%
            case HIGH_VALUE -> 0.03; // 3%
        };
    }

    /**
     * PO 유형별 가중치
     */
    private double getPoTypeMultiplier(PurchaseOrder.PoType poType) {
        return switch (poType) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };
    }

    /**
     * 성수기 가중치 조회
     */
    private BigDecimal getSeasonalMultiplier(LocalDate date) {
        return seasonalConfigRepository.findActiveSeason(date)
            .map(SeasonalConfig::getMultiplier)
            .orElse(BigDecimal.ONE);
    }

    /**
     * 공급업체 페널티 기록
     */
    private void recordSupplierPenalty(PurchaseOrder po, SupplierPenalty.PenaltyType type, String description) {
        SupplierPenalty penalty = SupplierPenalty.builder()
            .penaltyId(UUID.randomUUID())
            .supplier(po.getSupplier())
            .penaltyType(type)
            .description(description)
            .poId(po.getPoId())
            .build();

        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 체크
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        long penaltyCount = supplierPenaltyRepository.countBySupplierId30Days(
            po.getSupplier().getSupplierId(), since);

        if (penaltyCount >= 3) {
            // pending PO를 hold로 변경
            purchaseOrderRepository.holdPendingOrdersBySupplier(po.getSupplier().getSupplierId());
        }
    }

    /**
     * PO 상태 갱신 (모든 라인 완납 여부 체크)
     */
    private void updatePurchaseOrderStatus(UUID poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId).orElseThrow();
        List<PurchaseOrderLine> lines = purchaseOrderLineRepository.findByPoId(poId);

        boolean allFulfilled = lines.stream()
            .allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());
        boolean anyReceived = lines.stream()
            .anyMatch(line -> line.getReceivedQty() > 0);

        if (allFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * Entity → Response 매핑
     */
    private InboundReceiptResponse mapToResponse(InboundReceipt receipt) {
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receipt.getReceiptId());

        return InboundReceiptResponse.builder()
            .receiptId(receipt.getReceiptId())
            .poId(receipt.getPurchaseOrder().getPoId())
            .poNumber(receipt.getPurchaseOrder().getPoNumber())
            .status(receipt.getStatus().name())
            .receivedBy(receipt.getReceivedBy())
            .receivedAt(receipt.getReceivedAt())
            .confirmedAt(receipt.getConfirmedAt())
            .lines(lines.stream().map(this::mapLineToResponse).collect(Collectors.toList()))
            .build();
    }

    private InboundReceiptResponse.InboundReceiptLineResponse mapLineToResponse(InboundReceiptLine line) {
        return InboundReceiptResponse.InboundReceiptLineResponse.builder()
            .receiptLineId(line.getReceiptLineId())
            .productId(line.getProduct().getProductId())
            .productSku(line.getProduct().getSku())
            .productName(line.getProduct().getName())
            .locationId(line.getLocation().getLocationId())
            .locationCode(line.getLocation().getCode())
            .quantity(line.getQuantity())
            .lotNumber(line.getLotNumber())
            .expiryDate(line.getExpiryDate())
            .manufactureDate(line.getManufactureDate())
            .build();
    }
}


============================================================
// FILE: src\main\resources\application.yml
============================================================
spring:
  application:
    name: wms-inbound

  datasource:
    url: jdbc:postgresql://localhost:5432/wms
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        jdbc:
          time_zone: UTC

  sql:
    init:
      mode: never

server:
  port: 8080

logging:
  level:
    com.wms: DEBUG
    org.hibernate.SQL: DEBUG


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- ========================================
-- WMS Database Schema (Level 2)
-- ========================================

-- 1. 상품 마스터
CREATE TABLE products (
    product_id      UUID PRIMARY KEY,
    sku             VARCHAR(50) UNIQUE NOT NULL,
    name            VARCHAR(200) NOT NULL,
    category        VARCHAR(50) NOT NULL DEFAULT 'GENERAL'
                    CHECK (category IN ('GENERAL', 'FRESH', 'HAZMAT', 'HIGH_VALUE')),
    storage_type    VARCHAR(20) NOT NULL DEFAULT 'AMBIENT'
                    CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN')),
    unit            VARCHAR(20) NOT NULL DEFAULT 'EA',
    has_expiry      BOOLEAN NOT NULL DEFAULT false,
    min_remaining_shelf_life_pct INTEGER DEFAULT 30,
    max_pick_qty    INTEGER,
    manufacture_date_required BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 2. 로케이션 (창고 내 적재 위치)
CREATE TABLE locations (
    location_id     UUID PRIMARY KEY,
    code            VARCHAR(20) UNIQUE NOT NULL,
    zone            VARCHAR(50) NOT NULL
                    CHECK (zone IN ('RECEIVING', 'STORAGE', 'SHIPPING', 'HAZMAT')),
    storage_type    VARCHAR(20) NOT NULL DEFAULT 'AMBIENT'
                    CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN')),
    capacity        INTEGER NOT NULL,
    current_qty     INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    is_frozen       BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 3. 재고 (로케이션별 상품 재고)
CREATE TABLE inventory (
    inventory_id    UUID PRIMARY KEY,
    product_id      UUID NOT NULL REFERENCES products(product_id),
    location_id     UUID NOT NULL REFERENCES locations(location_id),
    quantity        INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    lot_number      VARCHAR(50),
    expiry_date     DATE,
    manufacture_date DATE,
    received_at     TIMESTAMPTZ NOT NULL,
    is_expired      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (product_id, location_id, lot_number)
);

-- 4. 공급업체 마스터
CREATE TABLE suppliers (
    supplier_id     UUID PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    contact_info    VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'hold', 'inactive')),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 5. 공급업체 페널티
CREATE TABLE supplier_penalties (
    penalty_id      UUID PRIMARY KEY,
    supplier_id     UUID NOT NULL REFERENCES suppliers(supplier_id),
    penalty_type    VARCHAR(50) NOT NULL
                    CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    description     VARCHAR(500),
    po_id           UUID,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 6. 발주서 (Purchase Order)
CREATE TABLE purchase_orders (
    po_id           UUID PRIMARY KEY,
    po_number       VARCHAR(30) UNIQUE NOT NULL,
    supplier_id     UUID NOT NULL REFERENCES suppliers(supplier_id),
    po_type         VARCHAR(20) NOT NULL DEFAULT 'NORMAL'
                    CHECK (po_type IN ('NORMAL', 'URGENT', 'IMPORT')),
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'partial', 'completed', 'cancelled', 'hold')),
    ordered_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE purchase_order_lines (
    po_line_id      UUID PRIMARY KEY,
    po_id           UUID NOT NULL REFERENCES purchase_orders(po_id),
    product_id      UUID NOT NULL REFERENCES products(product_id),
    ordered_qty     INTEGER NOT NULL CHECK (ordered_qty > 0),
    received_qty    INTEGER NOT NULL DEFAULT 0 CHECK (received_qty >= 0),
    unit_price      NUMERIC(12,2),
    UNIQUE (po_id, product_id)
);

-- 7. 입고 (Inbound Receipt)
CREATE TABLE inbound_receipts (
    receipt_id      UUID PRIMARY KEY,
    po_id           UUID NOT NULL REFERENCES purchase_orders(po_id),
    status          VARCHAR(20) NOT NULL DEFAULT 'inspecting'
                    CHECK (status IN ('inspecting', 'pending_approval', 'confirmed', 'rejected')),
    received_by     VARCHAR(100) NOT NULL,
    received_at     TIMESTAMPTZ DEFAULT NOW(),
    confirmed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE inbound_receipt_lines (
    receipt_line_id UUID PRIMARY KEY,
    receipt_id      UUID NOT NULL REFERENCES inbound_receipts(receipt_id),
    product_id      UUID NOT NULL REFERENCES products(product_id),
    location_id     UUID NOT NULL REFERENCES locations(location_id),
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    lot_number      VARCHAR(50),
    expiry_date     DATE,
    manufacture_date DATE
);

-- 8. 출고 지시서 (Shipment Order)
CREATE TABLE shipment_orders (
    shipment_id     UUID PRIMARY KEY,
    shipment_number VARCHAR(30) UNIQUE NOT NULL,
    customer_name   VARCHAR(200) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'picking', 'partial', 'shipped', 'cancelled')),
    requested_at    TIMESTAMPTZ NOT NULL,
    shipped_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE shipment_order_lines (
    shipment_line_id UUID PRIMARY KEY,
    shipment_id      UUID NOT NULL REFERENCES shipment_orders(shipment_id),
    product_id       UUID NOT NULL REFERENCES products(product_id),
    requested_qty    INTEGER NOT NULL CHECK (requested_qty > 0),
    picked_qty       INTEGER NOT NULL DEFAULT 0 CHECK (picked_qty >= 0),
    status           VARCHAR(20) NOT NULL DEFAULT 'pending'
                     CHECK (status IN ('pending', 'picked', 'partial', 'backordered'))
);

-- 9. 백오더 (Backorder)
CREATE TABLE backorders (
    backorder_id     UUID PRIMARY KEY,
    shipment_line_id UUID NOT NULL REFERENCES shipment_order_lines(shipment_line_id),
    product_id       UUID NOT NULL REFERENCES products(product_id),
    shortage_qty     INTEGER NOT NULL CHECK (shortage_qty > 0),
    status           VARCHAR(20) NOT NULL DEFAULT 'open'
                     CHECK (status IN ('open', 'fulfilled', 'cancelled')),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    fulfilled_at     TIMESTAMPTZ
);

-- 10. 재고 이동 (Stock Transfer)
CREATE TABLE stock_transfers (
    transfer_id      UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id),
    from_location_id UUID NOT NULL REFERENCES locations(location_id),
    to_location_id   UUID NOT NULL REFERENCES locations(location_id),
    quantity         INTEGER NOT NULL CHECK (quantity > 0),
    lot_number       VARCHAR(50),
    reason           VARCHAR(500),
    transfer_status  VARCHAR(20) NOT NULL DEFAULT 'immediate'
                     CHECK (transfer_status IN ('immediate', 'pending_approval', 'approved', 'rejected')),
    transferred_by   VARCHAR(100) NOT NULL,
    approved_by      VARCHAR(100),
    transferred_at   TIMESTAMPTZ DEFAULT NOW(),
    CHECK (from_location_id != to_location_id)
);

-- 11. 재고 조정 (Inventory Adjustment)
CREATE TABLE inventory_adjustments (
    adjustment_id    UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id),
    location_id      UUID NOT NULL REFERENCES locations(location_id),
    system_qty       INTEGER NOT NULL,
    actual_qty       INTEGER NOT NULL,
    difference       INTEGER NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    requires_approval BOOLEAN NOT NULL DEFAULT false,
    approval_status  VARCHAR(20) NOT NULL DEFAULT 'auto_approved'
                     CHECK (approval_status IN ('auto_approved', 'pending', 'approved', 'rejected')),
    approved_by      VARCHAR(100),
    adjusted_by      VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    approved_at      TIMESTAMPTZ
);

-- 12. 감사 로그 (Audit Log)
CREATE TABLE audit_logs (
    log_id           UUID PRIMARY KEY,
    event_type       VARCHAR(50) NOT NULL,
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        UUID NOT NULL,
    details          JSONB NOT NULL,
    performed_by     VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 13. 안전재고 기준 (Safety Stock Rules)
CREATE TABLE safety_stock_rules (
    rule_id          UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id) UNIQUE,
    min_qty          INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty      INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 14. 자동 재발주 이력 (Auto Reorder Logs)
CREATE TABLE auto_reorder_logs (
    reorder_log_id   UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id),
    trigger_type     VARCHAR(50) NOT NULL
                     CHECK (trigger_type IN ('SAFETY_STOCK_TRIGGER', 'URGENT_REORDER')),
    current_stock    INTEGER NOT NULL,
    min_qty          INTEGER NOT NULL,
    reorder_qty      INTEGER NOT NULL,
    triggered_by     VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 15. 계절 설정 (Seasonal Config)
CREATE TABLE seasonal_config (
    season_id        UUID PRIMARY KEY,
    season_name      VARCHAR(100) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    multiplier       NUMERIC(3,2) NOT NULL DEFAULT 1.50,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 16. 실사 세션 (Cycle Count)
CREATE TABLE cycle_counts (
    cycle_count_id   UUID PRIMARY KEY,
    location_id      UUID NOT NULL REFERENCES locations(location_id),
    status           VARCHAR(20) NOT NULL DEFAULT 'in_progress'
                     CHECK (status IN ('in_progress', 'completed')),
    started_by       VARCHAR(100) NOT NULL,
    started_at       TIMESTAMPTZ DEFAULT NOW(),
    completed_at     TIMESTAMPTZ
);

-- 인덱스 생성
CREATE INDEX idx_inventory_product ON inventory(product_id);
CREATE INDEX idx_inventory_location ON inventory(location_id);
CREATE INDEX idx_po_lines_po ON purchase_order_lines(po_id);
CREATE INDEX idx_receipt_lines_receipt ON inbound_receipt_lines(receipt_id);
CREATE INDEX idx_supplier_penalties_supplier ON supplier_penalties(supplier_id);
CREATE INDEX idx_supplier_penalties_created ON supplier_penalties(created_at);
CREATE INDEX idx_seasonal_config_dates ON seasonal_config(start_date, end_date, is_active);

