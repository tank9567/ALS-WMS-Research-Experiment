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
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.wms</groupId>
    <artifactId>wms-backend</artifactId>
    <version>1.0.0</version>
    <name>WMS Backend</name>
    <description>Warehouse Management System Backend</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Data JPA -->
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

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot Starter Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Spring Boot Test -->
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
// FILE: src\main\java\com\wms\controller\InboundController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound-receipts")
@RequiredArgsConstructor
public class InboundController {

    private final InboundService inboundService;

    /**
     * 입고 등록 (inspecting 상태)
     * POST /api/v1/inbound-receipts
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createReceipt(
            @Valid @RequestBody InboundReceiptRequest request) {
        InboundReceiptResponse response = inboundService.createReceipt(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    /**
     * 입고 확정 (confirmed)
     * POST /api/v1/inbound-receipts/{id}/confirm
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundService.confirmReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 입고 거부 (rejected)
     * POST /api/v1/inbound-receipts/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundService.rejectReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 유통기한 경고 승인 (pending_approval -> inspecting)
     * POST /api/v1/inbound-receipts/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundService.approveReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 입고 상세 조회
     * GET /api/v1/inbound-receipts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getReceipt(@PathVariable UUID id) {
        InboundReceiptResponse response = inboundService.getReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 입고 목록 조회
     * GET /api/v1/inbound-receipts
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InboundReceiptResponse>>> getReceipts(Pageable pageable) {
        Page<InboundReceiptResponse> response = inboundService.getReceipts(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ApiResponse.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
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

    public static <T> ApiResponse<T> error(String message, String code) {
        return ApiResponse.<T>builder()
            .success(false)
            .error(new ErrorInfo(message, code))
            .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorInfo {
        private String message;
        private String code;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptRequest.java
============================================================
package com.wms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptRequest {
    @NotNull(message = "PO ID is required")
    private UUID poId;

    @NotBlank(message = "Received by is required")
    private String receivedBy;

    @NotEmpty(message = "Receipt lines are required")
    @Valid
    private List<InboundReceiptLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineRequest {
        @NotNull(message = "Product ID is required")
        private UUID productId;

        @NotNull(message = "Location ID is required")
        private UUID locationId;

        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        private Integer quantity;

        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptResponse.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptResponse {
    private UUID receiptId;
    private UUID poId;
    private String status;
    private String receivedBy;
    private OffsetDateTime receivedAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime createdAt;
    private List<InboundReceiptLineResponse> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineResponse {
        private UUID receiptLineId;
        private UUID productId;
        private UUID locationId;
        private Integer quantity;
        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceipt.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
    @Column(name = "receipt_id")
    private UUID receiptId;

    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReceiptStatus status = ReceiptStatus.inspecting;

    @Column(name = "received_by", nullable = false, length = 100)
    private String receivedBy;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "inboundReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InboundReceiptLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (receiptId == null) {
            receiptId = UUID.randomUUID();
        }
        if (receivedAt == null) {
            receivedAt = OffsetDateTime.now();
        }
        createdAt = OffsetDateTime.now();
    }

    public enum ReceiptStatus {
        inspecting, pending_approval, confirmed, rejected
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceiptLine.java
============================================================
package com.wms.entity;

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
    @Column(name = "receipt_line_id")
    private UUID receiptLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private InboundReceipt inboundReceipt;

    @Column(name = "receipt_id", insertable = false, updatable = false)
    private UUID receiptId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @PrePersist
    protected void onCreate() {
        if (receiptLineId == null) {
            receiptLineId = UUID.randomUUID();
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Inventory.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    @Column(name = "inventory_id")
    private UUID inventoryId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "is_expired", nullable = false)
    private Boolean isExpired = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (inventoryId == null) {
            inventoryId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Location.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
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
    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "code", unique = true, nullable = false, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", nullable = false, length = 50)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private Product.StorageType storageType = Product.StorageType.AMBIENT;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "current_qty", nullable = false)
    private Integer currentQty = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_frozen", nullable = false)
    private Boolean isFrozen = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (locationId == null) {
            locationId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public enum Zone {
        RECEIVING, STORAGE, SHIPPING, HAZMAT
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Product.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
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
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "sku", unique = true, nullable = false, length = 50)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private ProductCategory category = ProductCategory.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private StorageType storageType = StorageType.AMBIENT;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit = "EA";

    @Column(name = "has_expiry", nullable = false)
    private Boolean hasExpiry = false;

    @Column(name = "min_remaining_shelf_life_pct")
    private Integer minRemainingShelfLifePct = 30;

    @Column(name = "max_pick_qty")
    private Integer maxPickQty;

    @Column(name = "manufacture_date_required", nullable = false)
    private Boolean manufactureDateRequired = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (productId == null) {
            productId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum ProductCategory {
        GENERAL, FRESH, HAZMAT, HIGH_VALUE
    }

    public enum StorageType {
        AMBIENT, COLD, FROZEN
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
    @Column(name = "po_id")
    private UUID poId;

    @Column(name = "po_number", unique = true, nullable = false, length = 30)
    private String poNumber;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "po_type", nullable = false, length = 20)
    private PoType poType = PoType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PoStatus status = PoStatus.pending;

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (poId == null) {
            poId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum PoType {
        NORMAL, URGENT, IMPORT
    }

    public enum PoStatus {
        pending, partial, completed, cancelled, hold
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrderLine.java
============================================================
package com.wms.entity;

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
    @Column(name = "po_line_id")
    private UUID poLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "po_id", insertable = false, updatable = false)
    private UUID poId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "ordered_qty", nullable = false)
    private Integer orderedQty;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty = 0;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @PrePersist
    protected void onCreate() {
        if (poLineId == null) {
            poLineId = UUID.randomUUID();
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\SeasonalConfig.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    @Column(name = "season_id")
    private UUID seasonId;

    @Column(name = "season_name", nullable = false, length = 100)
    private String seasonName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "multiplier", nullable = false, precision = 3, scale = 2)
    private BigDecimal multiplier = new BigDecimal("1.50");

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (seasonId == null) {
            seasonId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Supplier.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
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
    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_info", length = 500)
    private String contactInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status = SupplierStatus.active;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (supplierId == null) {
            supplierId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum SupplierStatus {
        active, hold, inactive
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\SupplierPenalty.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
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
    @Column(name = "penalty_id")
    private UUID penaltyId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", nullable = false, length = 50)
    private PenaltyType penaltyType;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "po_id")
    private UUID poId;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (penaltyId == null) {
            penaltyId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public enum PenaltyType {
        OVER_DELIVERY, SHORT_SHELF_LIFE
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\InboundException.java
============================================================
package com.wms.exception;

public class InboundException extends RuntimeException {
    private final String errorCode;

    public InboundException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\ResourceNotFoundException.java
============================================================
package com.wms.exception;

public class ResourceNotFoundException extends RuntimeException {
    private final String errorCode;

    public ResourceNotFoundException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}


============================================================
// FILE: src\main\java\com\wms\repository\InboundReceiptLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InboundReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InboundReceiptLineRepository extends JpaRepository<InboundReceiptLine, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\InboundReceiptRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InboundReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\InventoryRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId AND i.locationId = :locationId AND i.lotNumber = :lotNumber")
    Optional<Inventory> findByProductIdAndLocationIdAndLotNumber(
        @Param("productId") UUID productId,
        @Param("locationId") UUID locationId,
        @Param("lotNumber") String lotNumber
    );
}


============================================================
// FILE: src\main\java\com\wms\repository\LocationRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\ProductRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    List<PurchaseOrderLine> findByPoId(UUID poId);

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.poId = :poId AND pol.productId = :productId")
    Optional<PurchaseOrderLine> findByPoIdAndProductId(@Param("poId") UUID poId, @Param("productId") UUID productId);
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    @Modifying
    @Query("UPDATE PurchaseOrder po SET po.status = 'hold' WHERE po.supplierId = :supplierId AND po.status = 'pending'")
    int updateStatusToHoldBySupplierId(@Param("supplierId") UUID supplierId);
}


============================================================
// FILE: src\main\java\com\wms\repository\SeasonalConfigRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.SeasonalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeasonalConfigRepository extends JpaRepository<SeasonalConfig, UUID> {
    @Query("SELECT sc FROM SeasonalConfig sc WHERE sc.isActive = true AND :date BETWEEN sc.startDate AND sc.endDate")
    Optional<SeasonalConfig> findActiveSeasonByDate(@Param("date") LocalDate date);
}


============================================================
// FILE: src\main\java\com\wms\repository\SupplierPenaltyRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.SupplierPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {
    @Query("SELECT COUNT(p) FROM SupplierPenalty p WHERE p.supplierId = :supplierId AND p.createdAt >= :since")
    long countBySupplierIdAndCreatedAtAfter(@Param("supplierId") UUID supplierId, @Param("since") OffsetDateTime since);
}


============================================================
// FILE: src\main\java\com\wms\repository\SupplierRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\service\InboundService.java
============================================================
package com.wms.service;

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.InboundException;
import com.wms.exception.ResourceNotFoundException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundService {

    private final InboundReceiptRepository receiptRepository;
    private final InboundReceiptLineRepository receiptLineRepository;
    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderLineRepository poLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository penaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    /**
     * 입고 등록 (inspecting 상태)
     * ALS-WMS-INB-002 규칙 준수
     */
    @Transactional
    public InboundReceiptResponse createReceipt(InboundReceiptRequest request) {
        // 1. PO 조회 및 검증
        PurchaseOrder po = poRepository.findById(request.getPoId())
            .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found", "PO_NOT_FOUND"));

        if (po.getStatus() == PurchaseOrder.PoStatus.hold) {
            throw new InboundException("Purchase order is on hold", "PO_ON_HOLD");
        }

        // 2. 각 라인별 검증 수행
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            validateReceiptLine(po, lineReq);
        }

        // 3. InboundReceipt 생성 (inspecting 상태)
        InboundReceipt receipt = InboundReceipt.builder()
            .poId(request.getPoId())
            .status(InboundReceipt.ReceiptStatus.inspecting)
            .receivedBy(request.getReceivedBy())
            .build();

        // 4. 유통기한 잔여율 체크하여 상태 결정
        boolean needsApproval = checkIfNeedsApproval(request.getLines());
        if (needsApproval) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
        }

        receiptRepository.save(receipt);

        // 5. InboundReceiptLine 생성
        List<InboundReceiptLine> lines = request.getLines().stream()
            .map(lineReq -> {
                InboundReceiptLine line = InboundReceiptLine.builder()
                    .inboundReceipt(receipt)
                    .productId(lineReq.getProductId())
                    .locationId(lineReq.getLocationId())
                    .quantity(lineReq.getQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .expiryDate(lineReq.getExpiryDate())
                    .manufactureDate(lineReq.getManufactureDate())
                    .build();
                return line;
            })
            .collect(Collectors.toList());

        receiptLineRepository.saveAll(lines);
        receipt.setLines(lines);

        return toResponse(receipt);
    }

    /**
     * 입고 라인별 검증 (ALS-WMS-INB-002 Constraints 준수)
     */
    private void validateReceiptLine(PurchaseOrder po, InboundReceiptRequest.InboundReceiptLineRequest lineReq) {
        // 1. Product 조회
        Product product = productRepository.findById(lineReq.getProductId())
            .orElseThrow(() -> new ResourceNotFoundException("Product not found", "PRODUCT_NOT_FOUND"));

        // 2. Location 조회
        Location location = locationRepository.findById(lineReq.getLocationId())
            .orElseThrow(() -> new ResourceNotFoundException("Location not found", "LOCATION_NOT_FOUND"));

        // 3. PO Line 조회
        PurchaseOrderLine poLine = poLineRepository.findByPoIdAndProductId(po.getPoId(), product.getProductId())
            .orElseThrow(() -> new InboundException("Product not found in purchase order", "PRODUCT_NOT_IN_PO"));

        // 4. 유통기한 관리 상품 체크
        if (product.getHasExpiry()) {
            if (lineReq.getExpiryDate() == null) {
                recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                    "Expiry date is required for products with expiry management");
                throw new InboundException("Expiry date is required", "EXPIRY_DATE_REQUIRED");
            }
            if (lineReq.getManufactureDate() == null) {
                recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                    "Manufacture date is required for products with expiry management");
                throw new InboundException("Manufacture date is required", "MANUFACTURE_DATE_REQUIRED");
            }

            // 유통기한 잔여율 체크
            double remainingPct = calculateRemainingShelfLifePct(lineReq.getManufactureDate(), lineReq.getExpiryDate());
            if (remainingPct < product.getMinRemainingShelfLifePct()) {
                recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                    String.format("Remaining shelf life %.1f%% is below minimum %d%%", remainingPct, product.getMinRemainingShelfLifePct()));
                throw new InboundException(
                    String.format("Remaining shelf life %.1f%% is below minimum %d%%", remainingPct, product.getMinRemainingShelfLifePct()),
                    "SHORT_SHELF_LIFE");
            }
        }

        // 5. 보관 유형 호환성 체크 (ALS-WMS-INB-002 Constraint)
        validateStorageTypeCompatibility(product, location);

        // 6. 실사 동결 로케이션 체크
        if (location.getIsFrozen()) {
            throw new InboundException("Cannot receive to frozen location", "LOCATION_FROZEN");
        }

        // 7. 초과입고 허용률 체크
        validateOverReceiveLimit(po, poLine, lineReq.getQuantity(), product);
    }

    /**
     * 유통기한 잔여율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        return (double) remainingDays / totalDays * 100.0;
    }

    /**
     * 승인 필요 여부 체크 (유통기한 30~50%)
     */
    private boolean checkIfNeedsApproval(List<InboundReceiptRequest.InboundReceiptLineRequest> lines) {
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : lines) {
            if (lineReq.getManufactureDate() != null && lineReq.getExpiryDate() != null) {
                double remainingPct = calculateRemainingShelfLifePct(lineReq.getManufactureDate(), lineReq.getExpiryDate());
                if (remainingPct >= 30 && remainingPct <= 50) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 보관 유형 호환성 검증 (ALS-WMS-INB-002)
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // FROZEN 상품 → FROZEN 로케이션만
        if (productType == Product.StorageType.FROZEN && locationType != Product.StorageType.FROZEN) {
            throw new InboundException("FROZEN products can only be received to FROZEN locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // COLD 상품 → COLD 또는 FROZEN 로케이션
        if (productType == Product.StorageType.COLD &&
            locationType != Product.StorageType.COLD && locationType != Product.StorageType.FROZEN) {
            throw new InboundException("COLD products can only be received to COLD or FROZEN locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // AMBIENT 상품 → AMBIENT 로케이션만
        if (productType == Product.StorageType.AMBIENT && locationType != Product.StorageType.AMBIENT) {
            throw new InboundException("AMBIENT products can only be received to AMBIENT locations", "STORAGE_TYPE_INCOMPATIBLE");
        }

        // HAZMAT 상품 → HAZMAT zone 로케이션만
        if (product.getCategory() == Product.ProductCategory.HAZMAT && location.getZone() != Location.Zone.HAZMAT) {
            throw new InboundException("HAZMAT products can only be received to HAZMAT zone", "HAZMAT_ZONE_REQUIRED");
        }
    }

    /**
     * 초과입고 허용률 검증 (ALS-WMS-INB-002 카테고리별/PO유형별/성수기 가중치 적용)
     */
    private void validateOverReceiveLimit(PurchaseOrder po, PurchaseOrderLine poLine, int receiveQty, Product product) {
        int orderedQty = poLine.getOrderedQty();
        int alreadyReceivedQty = poLine.getReceivedQty();
        int totalReceiveQty = alreadyReceivedQty + receiveQty;

        // 1. 카테고리별 기본 허용률
        double baseTolerance = getCategoryTolerance(product.getCategory());

        // 2. HAZMAT은 항상 0% (예외 없음)
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (totalReceiveQty > orderedQty) {
                recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.OVER_DELIVERY,
                    String.format("HAZMAT over-delivery not allowed: ordered=%d, receiving=%d", orderedQty, totalReceiveQty));
                throw new InboundException(
                    String.format("HAZMAT over-delivery not allowed: ordered=%d, total receiving=%d", orderedQty, totalReceiveQty),
                    "HAZMAT_OVER_DELIVERY");
            }
            return;
        }

        // 3. PO 유형별 가중치
        double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());

        // 4. 성수기 가중치
        double seasonalMultiplier = getSeasonalMultiplier();

        // 5. 최종 허용률 계산
        double finalTolerance = baseTolerance * poTypeMultiplier * seasonalMultiplier;
        int maxAllowed = (int) (orderedQty * (1 + finalTolerance));

        log.debug("Over-receive check: category={}, poType={}, baseTolerance={}%, poMultiplier={}, seasonMultiplier={}, finalTolerance={}%, maxAllowed={}",
            product.getCategory(), po.getPoType(), baseTolerance * 100, poTypeMultiplier, seasonalMultiplier, finalTolerance * 100, maxAllowed);

        if (totalReceiveQty > maxAllowed) {
            recordPenaltyAndReject(po.getSupplierId(), po.getPoId(), SupplierPenalty.PenaltyType.OVER_DELIVERY,
                String.format("Over-delivery: ordered=%d, max allowed=%d, receiving=%d", orderedQty, maxAllowed, totalReceiveQty));
            throw new InboundException(
                String.format("Over-delivery: ordered=%d, max allowed=%d, total receiving=%d", orderedQty, maxAllowed, totalReceiveQty),
                "OVER_DELIVERY");
        }
    }

    /**
     * 카테고리별 초과입고 허용률 (ALS-WMS-INB-002)
     */
    private double getCategoryTolerance(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 0.10;    // 10%
            case FRESH -> 0.05;      // 5%
            case HAZMAT -> 0.00;     // 0%
            case HIGH_VALUE -> 0.03; // 3%
        };
    }

    /**
     * PO 유형별 가중치 (ALS-WMS-INB-002)
     */
    private double getPoTypeMultiplier(PurchaseOrder.PoType poType) {
        return switch (poType) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };
    }

    /**
     * 성수기 가중치 조회 (ALS-WMS-INB-002)
     */
    private double getSeasonalMultiplier() {
        LocalDate today = LocalDate.now();
        return seasonalConfigRepository.findActiveSeasonByDate(today)
            .map(season -> season.getMultiplier().doubleValue())
            .orElse(1.0);
    }

    /**
     * 공급업체 페널티 기록 및 hold 처리 (ALS-WMS-INB-002)
     */
    private void recordPenaltyAndReject(UUID supplierId, UUID poId, SupplierPenalty.PenaltyType penaltyType, String description) {
        // 페널티 기록
        SupplierPenalty penalty = SupplierPenalty.builder()
            .supplierId(supplierId)
            .poId(poId)
            .penaltyType(penaltyType)
            .description(description)
            .build();
        penaltyRepository.save(penalty);

        // 최근 30일 페널티 카운트
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = penaltyRepository.countBySupplierIdAndCreatedAtAfter(supplierId, thirtyDaysAgo);

        // 3회 이상이면 해당 공급업체의 모든 pending PO를 hold로 변경
        if (penaltyCount >= 3) {
            int updatedCount = poRepository.updateStatusToHoldBySupplierId(supplierId);
            log.warn("Supplier {} has {} penalties in last 30 days. {} pending POs set to hold.",
                supplierId, penaltyCount, updatedCount);
        }
    }

    /**
     * 입고 확정 (confirmed) - 재고 반영
     * ALS-WMS-INB-002: confirmed 시점에만 재고 증가
     */
    @Transactional
    public InboundReceiptResponse confirmReceipt(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new InboundException("Receipt is not in inspecting or pending_approval status", "INVALID_STATUS");
        }

        // 1. 상태를 confirmed로 변경
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        receiptRepository.save(receipt);

        // 2. 재고 반영
        PurchaseOrder po = poRepository.findById(receipt.getPoId())
            .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found", "PO_NOT_FOUND"));

        for (InboundReceiptLine line : receipt.getLines()) {
            // 2-1. Inventory 증가
            Inventory inventory = inventoryRepository.findByProductIdAndLocationIdAndLotNumber(
                    line.getProductId(), line.getLocationId(), line.getLotNumber())
                .orElseGet(() -> {
                    Inventory newInv = Inventory.builder()
                        .productId(line.getProductId())
                        .locationId(line.getLocationId())
                        .lotNumber(line.getLotNumber())
                        .expiryDate(line.getExpiryDate())
                        .manufactureDate(line.getManufactureDate())
                        .receivedAt(receipt.getReceivedAt())
                        .quantity(0)
                        .build();
                    return newInv;
                });

            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
            inventoryRepository.save(inventory);

            // 2-2. Location.current_qty 증가
            Location location = locationRepository.findById(line.getLocationId())
                .orElseThrow(() -> new ResourceNotFoundException("Location not found", "LOCATION_NOT_FOUND"));
            location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
            locationRepository.save(location);

            // 2-3. PO Line received_qty 누적 갱신
            PurchaseOrderLine poLine = poLineRepository.findByPoIdAndProductId(po.getPoId(), line.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("PO line not found", "PO_LINE_NOT_FOUND"));
            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            poLineRepository.save(poLine);
        }

        // 3. PO 상태 갱신 (모든 라인 완납 여부 체크)
        updatePurchaseOrderStatus(po);

        return toResponse(receipt);
    }

    /**
     * PO 상태 갱신 (completed / partial)
     */
    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        List<PurchaseOrderLine> lines = poLineRepository.findByPoId(po.getPoId());
        boolean allFulfilled = lines.stream().allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());
        boolean anyReceived = lines.stream().anyMatch(line -> line.getReceivedQty() > 0);

        if (allFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }
        poRepository.save(po);
    }

    /**
     * 입고 거부 (rejected)
     */
    @Transactional
    public InboundReceiptResponse rejectReceipt(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new InboundException("Receipt is not in inspecting or pending_approval status", "INVALID_STATUS");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        receiptRepository.save(receipt);

        return toResponse(receipt);
    }

    /**
     * 유통기한 경고 승인 (pending_approval -> inspecting)
     */
    @Transactional
    public InboundReceiptResponse approveReceipt(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new InboundException("Receipt is not in pending_approval status", "INVALID_STATUS");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        receiptRepository.save(receipt);

        return toResponse(receipt);
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceiptResponse getReceipt(UUID receiptId) {
        InboundReceipt receipt = receiptRepository.findById(receiptId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt not found", "RECEIPT_NOT_FOUND"));
        return toResponse(receipt);
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<InboundReceiptResponse> getReceipts(Pageable pageable) {
        return receiptRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Entity -> DTO 변환
     */
    private InboundReceiptResponse toResponse(InboundReceipt receipt) {
        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = receipt.getLines().stream()
            .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                .receiptLineId(line.getReceiptLineId())
                .productId(line.getProductId())
                .locationId(line.getLocationId())
                .quantity(line.getQuantity())
                .lotNumber(line.getLotNumber())
                .expiryDate(line.getExpiryDate())
                .manufactureDate(line.getManufactureDate())
                .build())
            .collect(Collectors.toList());

        return InboundReceiptResponse.builder()
            .receiptId(receipt.getReceiptId())
            .poId(receipt.getPoId())
            .status(receipt.getStatus().name())
            .receivedBy(receipt.getReceivedBy())
            .receivedAt(receipt.getReceivedAt())
            .confirmedAt(receipt.getConfirmedAt())
            .createdAt(receipt.getCreatedAt())
            .lines(lineResponses)
            .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\WmsApplication.java
============================================================
package com.wms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }
}


============================================================
// FILE: src\main\resources\application.yml
============================================================
spring:
  application:
    name: wms-backend

  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db
    username: wms_user
    password: wms_password
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
    open-in-view: false

  sql:
    init:
      mode: never

server:
  port: 8080
  error:
    include-message: always
    include-stacktrace: never

logging:
  level:
    com.wms: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- ========================================
-- 1. 상품 마스터
-- ========================================
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

-- ========================================
-- 2. 로케이션 (창고 내 적재 위치)
-- ========================================
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

-- ========================================
-- 3. 재고 (로케이션별 상품 재고)
-- ========================================
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

-- ========================================
-- 4. 공급업체 마스터
-- ========================================
CREATE TABLE suppliers (
    supplier_id     UUID PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    contact_info    VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'hold', 'inactive')),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 5. 공급업체 페널티
-- ========================================
CREATE TABLE supplier_penalties (
    penalty_id      UUID PRIMARY KEY,
    supplier_id     UUID NOT NULL REFERENCES suppliers(supplier_id),
    penalty_type    VARCHAR(50) NOT NULL
                    CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    description     VARCHAR(500),
    po_id           UUID,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 6. 발주서 (Purchase Order)
-- ========================================
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

-- ========================================
-- 7. 입고 (Inbound Receipt)
-- ========================================
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

-- ========================================
-- 8. 출고 지시서 (Shipment Order)
-- ========================================
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

-- ========================================
-- 9. 백오더 (Backorder)
-- ========================================
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

-- ========================================
-- 10. 재고 이동 (Stock Transfer)
-- ========================================
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

-- ========================================
-- 11. 재고 조정 (Inventory Adjustment)
-- ========================================
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

-- ========================================
-- 12. 감사 로그 (Audit Log)
-- ========================================
CREATE TABLE audit_logs (
    log_id           UUID PRIMARY KEY,
    event_type       VARCHAR(50) NOT NULL,
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        UUID NOT NULL,
    details          JSONB NOT NULL,
    performed_by     VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 13. 안전재고 기준 (Safety Stock Rules)
-- ========================================
CREATE TABLE safety_stock_rules (
    rule_id          UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id) UNIQUE,
    min_qty          INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty      INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 14. 자동 재발주 이력 (Auto Reorder Logs)
-- ========================================
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

-- ========================================
-- 15. 계절 설정 (Seasonal Config)
-- ========================================
CREATE TABLE seasonal_config (
    season_id        UUID PRIMARY KEY,
    season_name      VARCHAR(100) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    multiplier       NUMERIC(3,2) NOT NULL DEFAULT 1.50,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 16. 실사 세션 (Cycle Count)
-- ========================================
CREATE TABLE cycle_counts (
    cycle_count_id   UUID PRIMARY KEY,
    location_id      UUID NOT NULL REFERENCES locations(location_id),
    status           VARCHAR(20) NOT NULL DEFAULT 'in_progress'
                     CHECK (status IN ('in_progress', 'completed')),
    started_by       VARCHAR(100) NOT NULL,
    started_at       TIMESTAMPTZ DEFAULT NOW(),
    completed_at     TIMESTAMPTZ
);

