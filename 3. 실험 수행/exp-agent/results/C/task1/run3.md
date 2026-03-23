# CLI Output

입고 처리 기능 구현이 완료되었습니다.

## 구현 완료 내용

### 1. **DB 스키마** (schema.sql)
- ALS-WMS-CORE-002 기반으로 모든 테이블 정의
- products, locations, inventory, suppliers, supplier_penalties, purchase_orders, purchase_order_lines, inbound_receipts, inbound_receipt_lines, seasonal_config 테이블

### 2. **Entity 클래스**
- Product, Location, Supplier, SupplierPenalty, PurchaseOrder, PurchaseOrderLine, InboundReceipt, InboundReceiptLine, Inventory, SeasonalConfig
- UUID 기반 ID, TIMESTAMPTZ 타입, Enum 타입 제약조건 적용

### 3. **Repository 인터페이스**
- Spring Data JPA 기반 Repository
- 필요한 커스텀 쿼리 메서드 정의 (공급업체 페널티 조회, 성수기 조회 등)

### 4. **Service 계층** (InboundService.java)
ALS-WMS-INB-002의 모든 비즈니스 규칙 구현:

- ✅ **카테고리별 초과입고 허용률**: GENERAL(10%), FRESH(5%), HAZMAT(0%), HIGH_VALUE(3%)
- ✅ **발주 유형별 가중치**: NORMAL(×1), URGENT(×2), IMPORT(×1.5)
- ✅ **성수기 가중치**: seasonal_config 테이블 조회하여 multiplier 적용
- ✅ **HAZMAT 0% 규칙**: 어떤 가중치든 0% 유지
- ✅ **유통기한 잔여율 체크**: <30% 거부, 30~50% 승인필요, >50% 정상
- ✅ **manufacture_date 필수**: 유통기한 관리 상품
- ✅ **보관유형 호환성**: FROZEN/COLD/AMBIENT/HAZMAT zone 체크
- ✅ **실사 동결 로케이션 거부**: is_frozen=true 체크
- ✅ **공급업체 페널티**: OVER_DELIVERY, SHORT_SHELF_LIFE 기록
- ✅ **30일 내 3회 페널티**: 자동으로 pending PO를 hold로 변경
- ✅ **2단계 프로세스**: inspecting → confirmed (재고 반영은 confirmed 시점)
- ✅ **PO 상태 갱신**: 완납 시 completed, 부분입고 시 partial

### 5. **Controller** (InboundController.java)
REST API 엔드포인트:
- `POST /api/v1/inbound-receipts` - 입고 등록
- `POST /api/v1/inbound-receipts/{id}/confirm` - 입고 확정
- `POST /api/v1/inbound-receipts/{id}/reject` - 입고 거부
- `POST /api/v1/inbound-receipts/{id}/approve` - 유통기한 경고 승인
- `GET /api/v1/inbound-receipts/{id}` - 상세 조회
- `GET /api/v1/inbound-receipts` - 목록 조회

### 6. **응답 형식**
```json
{
  "success": true/false,
  "data": {...},
  "error": {"message": "..."}
}
```

### 7. **설정 파일**
- application.properties: PostgreSQL 연결, JPA 설정, UTC 시간대
- pom.xml: Spring Boot 3.2.0, Java 17, PostgreSQL, Lombok

모든 구현은 ALS-WMS-INB-002와 ALS-WMS-CORE-002의 규칙을 준수합니다.


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
    <name>WMS Inbound Module</name>
    <description>Warehouse Management System - Inbound Processing</description>

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
// FILE: src\main\java\com\wms\controller\InboundController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.entity.InboundReceipt;
import com.wms.service.InboundService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public ResponseEntity<ApiResponse<InboundReceipt>> createInboundReceipt(
            @RequestBody InboundReceiptRequest request) {
        try {
            InboundReceipt receipt = inboundService.createInboundReceipt(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(receipt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("입고 등록 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 입고 확정
     * POST /api/v1/inbound-receipts/{id}/confirm
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceipt>> confirmReceipt(@PathVariable UUID id) {
        try {
            InboundReceipt receipt = inboundService.confirmReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(receipt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("입고 확정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 입고 거부
     * POST /api/v1/inbound-receipts/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceipt>> rejectReceipt(@PathVariable UUID id) {
        try {
            InboundReceipt receipt = inboundService.rejectReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(receipt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("입고 거부 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 유통기한 경고 승인
     * POST /api/v1/inbound-receipts/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceipt>> approveShelfLifeWarning(@PathVariable UUID id) {
        try {
            InboundReceipt receipt = inboundService.approveShelfLifeWarning(id);
            return ResponseEntity.ok(ApiResponse.success(receipt));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("승인 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 입고 상세 조회
     * GET /api/v1/inbound-receipts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceipt>> getReceipt(@PathVariable UUID id) {
        try {
            InboundReceipt receipt = inboundService.getReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(receipt));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 입고 목록 조회
     * GET /api/v1/inbound-receipts
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InboundReceipt>>> getAllReceipts() {
        try {
            List<InboundReceipt> receipts = inboundService.getAllReceipts();
            return ResponseEntity.ok(ApiResponse.success(receipts));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private Boolean success;
    private T data;
    private ErrorDetail error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetail.builder().message(message).build())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String message;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundLineRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundLineRequest {
    private UUID productId;
    private UUID locationId;
    private Integer quantity;
    private String lotNumber;
    private LocalDate expiryDate;
    private LocalDate manufactureDate;
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptRequest {
    private UUID poId;
    private String receivedBy;
    private List<InboundLineRequest> lines;
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceipt.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceipt {

    @Id
    @Column(name = "receipt_id")
    private UUID receiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.inspecting;

    @Column(name = "received_by", nullable = false, length = 100)
    private String receivedBy;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public enum Status {
        inspecting, pending_approval, confirmed, rejected
    }

    @PrePersist
    protected void onCreate() {
        if (receiptId == null) {
            receiptId = UUID.randomUUID();
        }
        receivedAt = OffsetDateTime.now();
        createdAt = OffsetDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceiptLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_lines")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptLine {

    @Id
    @Column(name = "receipt_line_id")
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "location_id", "lot_number"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @Column(name = "inventory_id")
    private UUID inventoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "locations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    public enum Zone {
        RECEIVING, STORAGE, SHIPPING, HAZMAT
    }

    @PrePersist
    protected void onCreate() {
        if (locationId == null) {
            locationId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Product.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private Category category = Category.GENERAL;

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

    public enum Category {
        GENERAL, FRESH, HAZMAT, HIGH_VALUE
    }

    public enum StorageType {
        AMBIENT, COLD, FROZEN
    }

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
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder {

    @Id
    @Column(name = "po_id")
    private UUID poId;

    @Column(name = "po_number", unique = true, nullable = false, length = 30)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "po_type", nullable = false, length = 20)
    private PoType poType = PoType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.pending;

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum PoType {
        NORMAL, URGENT, IMPORT
    }

    public enum Status {
        pending, partial, completed, cancelled, hold
    }

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
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrderLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"po_id", "product_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderLine {

    @Id
    @Column(name = "po_line_id")
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seasonal_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private Status status = Status.active;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum Status {
        active, hold, inactive
    }

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
}


============================================================
// FILE: src\main\java\com\wms\entity\SupplierPenalty.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_penalties")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPenalty {

    @Id
    @Column(name = "penalty_id")
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

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public enum PenaltyType {
        OVER_DELIVERY, SHORT_SHELF_LIFE
    }

    @PrePersist
    protected void onCreate() {
        if (penaltyId == null) {
            penaltyId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\repository\InboundReceiptLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InboundReceipt;
import com.wms.entity.InboundReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InboundReceiptLineRepository extends JpaRepository<InboundReceiptLine, UUID> {
    List<InboundReceiptLine> findByInboundReceipt(InboundReceipt inboundReceipt);
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
import com.wms.entity.Location;
import com.wms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
    Optional<Inventory> findByProductAndLocationAndLotNumber(Product product, Location location, String lotNumber);
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

import com.wms.entity.PurchaseOrder;
import com.wms.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    List<PurchaseOrderLine> findByPurchaseOrder(PurchaseOrder purchaseOrder);
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.PurchaseOrder;
import com.wms.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    List<PurchaseOrder> findBySupplierAndStatus(Supplier supplier, PurchaseOrder.Status status);
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
    @Query("SELECT s FROM SeasonalConfig s WHERE s.isActive = true " +
           "AND s.startDate <= :date AND s.endDate >= :date")
    Optional<SeasonalConfig> findActiveSeasonForDate(@Param("date") LocalDate date);
}


============================================================
// FILE: src\main\java\com\wms\repository\SupplierPenaltyRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Supplier;
import com.wms.entity.SupplierPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {
    List<SupplierPenalty> findBySupplierAndCreatedAtAfter(Supplier supplier, OffsetDateTime since);
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

import com.wms.dto.InboundLineRequest;
import com.wms.dto.InboundReceiptRequest;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InboundService {

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
     * 입고 등록 (inspecting 상태)
     * ALS-WMS-INB-002: 입고 등록 시 모든 검증 수행
     */
    @Transactional
    public InboundReceipt createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 확인
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
                .orElseThrow(() -> new IllegalArgumentException("발주서를 찾을 수 없습니다: " + request.getPoId()));

        if (po.getStatus() == PurchaseOrder.Status.hold) {
            throw new IllegalStateException("보류된 발주서입니다. 입고가 불가능합니다.");
        }

        // 2. InboundReceipt 생성 (inspecting 상태)
        InboundReceipt receipt = InboundReceipt.builder()
                .purchaseOrder(po)
                .status(InboundReceipt.Status.inspecting)
                .receivedBy(request.getReceivedBy())
                .build();
        inboundReceiptRepository.save(receipt);

        // 3. 각 라인별 검증 및 저장
        boolean requiresApproval = false;

        for (InboundLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineReq.getProductId()));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + lineReq.getLocationId()));

            // 3.1. 실사 동결 체크
            if (location.getIsFrozen()) {
                throw new IllegalStateException("실사 중인 로케이션에는 입고할 수 없습니다: " + location.getCode());
            }

            // 3.2. 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3.3. 유통기한 관리 상품 검증
            if (product.getHasExpiry()) {
                if (lineReq.getExpiryDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 상품은 유통기한이 필수입니다: " + product.getSku());
                }
                if (lineReq.getManufactureDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 상품은 제조일이 필수입니다: " + product.getSku());
                }

                // 3.4. 유통기한 잔여율 체크
                double remainingPct = calculateRemainingShelfLifePct(
                        lineReq.getManufactureDate(),
                        lineReq.getExpiryDate(),
                        LocalDate.now()
                );

                int minPct = product.getMinRemainingShelfLifePct();

                if (remainingPct < minPct) {
                    // 유통기한 부족 -> 거부 및 페널티
                    recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                            SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                            String.format("유통기한 잔여율 %.1f%% (기준: %d%%)", remainingPct, minPct));
                    throw new IllegalStateException(
                            String.format("유통기한 잔여율이 부족합니다 (%.1f%% < %d%%): %s",
                                    remainingPct, minPct, product.getSku()));
                }

                if (remainingPct >= 30 && remainingPct <= 50) {
                    // 경고 범위 -> 승인 필요
                    requiresApproval = true;
                }
            }

            // 3.5. 초과입고 체크
            validateOverReceive(po, product, lineReq.getQuantity());

            // 3.6. InboundReceiptLine 생성
            InboundReceiptLine line = InboundReceiptLine.builder()
                    .inboundReceipt(receipt)
                    .product(product)
                    .location(location)
                    .quantity(lineReq.getQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .expiryDate(lineReq.getExpiryDate())
                    .manufactureDate(lineReq.getManufactureDate())
                    .build();
            inboundReceiptLineRepository.save(line);
        }

        // 4. 유통기한 경고가 있으면 pending_approval 상태로 변경
        if (requiresApproval) {
            receipt.setStatus(InboundReceipt.Status.pending_approval);
            inboundReceiptRepository.save(receipt);
        }

        // 5. 공급업체 페널티 누적 체크 (30일 내 3회 이상 -> PO hold)
        checkSupplierPenaltyThreshold(po.getSupplier());

        return receipt;
    }

    /**
     * 초과입고 검증
     * ALS-WMS-INB-002: 카테고리별 허용률 + PO유형 가중치 + 성수기 가중치
     */
    private void validateOverReceive(PurchaseOrder po, Product product, int incomingQty) {
        // PO Line 찾기
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrder(po);
        PurchaseOrderLine poLine = poLines.stream()
                .filter(line -> line.getProduct().getProductId().equals(product.getProductId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("발주서에 해당 상품이 없습니다: " + product.getSku()));

        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int totalReceiving = receivedQty + incomingQty;

        // 1. 카테고리별 기본 허용률
        double baseTolerance = switch (product.getCategory()) {
            case GENERAL -> 0.10;
            case FRESH -> 0.05;
            case HAZMAT -> 0.0;
            case HIGH_VALUE -> 0.03;
        };

        // 2. HAZMAT은 무조건 0% (가중치 무시)
        if (product.getCategory() == Product.Category.HAZMAT) {
            if (totalReceiving > orderedQty) {
                recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                        SupplierPenalty.PenaltyType.OVER_DELIVERY,
                        String.format("HAZMAT 초과입고 시도: %d > %d", totalReceiving, orderedQty));
                throw new IllegalStateException(
                        String.format("HAZMAT 상품은 초과입고가 불가능합니다 (발주: %d, 입고시도: %d)",
                                orderedQty, totalReceiving));
            }
            return;
        }

        // 3. PO 유형별 가중치
        double poTypeMultiplier = switch (po.getPoType()) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };

        // 4. 성수기 가중치
        double seasonalMultiplier = 1.0;
        var activeSeason = seasonalConfigRepository.findActiveSeasonForDate(LocalDate.now());
        if (activeSeason.isPresent()) {
            seasonalMultiplier = activeSeason.get().getMultiplier().doubleValue();
        }

        // 5. 최종 허용률 계산
        double finalTolerance = baseTolerance * poTypeMultiplier * seasonalMultiplier;
        int maxAllowed = (int) (orderedQty * (1.0 + finalTolerance));

        // 6. 검증
        if (totalReceiving > maxAllowed) {
            recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                    SupplierPenalty.PenaltyType.OVER_DELIVERY,
                    String.format("초과입고: %d > %d (허용률: %.1f%%)",
                            totalReceiving, maxAllowed, finalTolerance * 100));
            throw new IllegalStateException(
                    String.format("초과입고 한도를 초과했습니다 (발주: %d, 최대허용: %d, 입고시도: %d)",
                            orderedQty, maxAllowed, totalReceiving));
        }
    }

    /**
     * 보관 유형 호환성 검증
     * ALS-WMS-INB-002: 상품과 로케이션의 보관 유형 호환성 체크
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // HAZMAT 상품 -> HAZMAT zone만 허용
        if (product.getCategory() == Product.Category.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new IllegalStateException(
                        String.format("HAZMAT 상품은 HAZMAT zone에만 입고할 수 있습니다: %s", product.getSku()));
            }
            return;
        }

        // 보관 유형 호환성
        boolean compatible = switch (productType) {
            case FROZEN -> locationType == Product.StorageType.FROZEN;
            case COLD -> locationType == Product.StorageType.COLD ||
                        locationType == Product.StorageType.FROZEN;
            case AMBIENT -> locationType == Product.StorageType.AMBIENT;
        };

        if (!compatible) {
            throw new IllegalStateException(
                    String.format("보관 유형이 호환되지 않습니다 (상품: %s, 로케이션: %s): %s",
                            productType, locationType, product.getSku()));
        }
    }

    /**
     * 유통기한 잔여율 계산
     * (만료일 - 오늘) / (만료일 - 제조일) * 100
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
     * 공급업체 페널티 기록
     */
    private void recordSupplierPenalty(Supplier supplier, UUID poId,
                                       SupplierPenalty.PenaltyType type,
                                       String description) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .poId(poId)
                .penaltyType(type)
                .description(description)
                .build();
        supplierPenaltyRepository.save(penalty);
    }

    /**
     * 공급업체 페널티 임계치 체크
     * 최근 30일 내 3회 이상 -> 모든 pending PO를 hold로 변경
     */
    private void checkSupplierPenaltyThreshold(Supplier supplier) {
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        List<SupplierPenalty> recentPenalties =
                supplierPenaltyRepository.findBySupplierAndCreatedAtAfter(supplier, thirtyDaysAgo);

        if (recentPenalties.size() >= 3) {
            // 모든 pending PO를 hold로 변경
            List<PurchaseOrder> pendingPOs =
                    purchaseOrderRepository.findBySupplierAndStatus(supplier, PurchaseOrder.Status.pending);

            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PurchaseOrder.Status.hold);
                purchaseOrderRepository.save(po);
            }
        }
    }

    /**
     * 입고 확정
     * ALS-WMS-INB-002: confirmed 상태로 변경하고 재고 반영
     */
    @Transactional
    public InboundReceipt confirmReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.Status.inspecting &&
            receipt.getStatus() != InboundReceipt.Status.pending_approval) {
            throw new IllegalStateException("검수 중이거나 승인 대기 상태만 확정할 수 있습니다.");
        }

        // 1. 상태 변경
        receipt.setStatus(InboundReceipt.Status.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        inboundReceiptRepository.save(receipt);

        // 2. 재고 반영
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceipt(receipt);
        for (InboundReceiptLine line : lines) {
            updateInventory(line);
            updatePurchaseOrderLine(receipt.getPurchaseOrder(), line.getProduct(), line.getQuantity());
        }

        // 3. PO 상태 갱신
        updatePurchaseOrderStatus(receipt.getPurchaseOrder());

        return receipt;
    }

    /**
     * 재고 반영
     */
    private void updateInventory(InboundReceiptLine line) {
        // 기존 inventory 찾기 (product + location + lot)
        var existingInventory = inventoryRepository.findByProductAndLocationAndLotNumber(
                line.getProduct(), line.getLocation(), line.getLotNumber());

        if (existingInventory.isPresent()) {
            // 기존 재고 증가
            Inventory inv = existingInventory.get();
            inv.setQuantity(inv.getQuantity() + line.getQuantity());
            inventoryRepository.save(inv);
        } else {
            // 신규 재고 생성
            Inventory inv = Inventory.builder()
                    .product(line.getProduct())
                    .location(line.getLocation())
                    .quantity(line.getQuantity())
                    .lotNumber(line.getLotNumber())
                    .expiryDate(line.getExpiryDate())
                    .manufactureDate(line.getManufactureDate())
                    .receivedAt(OffsetDateTime.now())
                    .build();
            inventoryRepository.save(inv);
        }

        // Location current_qty 갱신
        Location location = line.getLocation();
        location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
        locationRepository.save(location);
    }

    /**
     * PO Line received_qty 누적 갱신
     */
    private void updatePurchaseOrderLine(PurchaseOrder po, Product product, int quantity) {
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrder(po);
        PurchaseOrderLine poLine = poLines.stream()
                .filter(line -> line.getProduct().getProductId().equals(product.getProductId()))
                .findFirst()
                .orElseThrow();

        poLine.setReceivedQty(poLine.getReceivedQty() + quantity);
        purchaseOrderLineRepository.save(poLine);
    }

    /**
     * PO 상태 갱신 (모든 라인 완납 -> completed, 일부 -> partial)
     */
    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrder(po);

        boolean allCompleted = poLines.stream()
                .allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());

        boolean anyReceived = poLines.stream()
                .anyMatch(line -> line.getReceivedQty() > 0);

        if (allCompleted) {
            po.setStatus(PurchaseOrder.Status.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.Status.partial);
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * 입고 거부
     */
    @Transactional
    public InboundReceipt rejectReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.Status.inspecting &&
            receipt.getStatus() != InboundReceipt.Status.pending_approval) {
            throw new IllegalStateException("검수 중이거나 승인 대기 상태만 거부할 수 있습니다.");
        }

        receipt.setStatus(InboundReceipt.Status.rejected);
        inboundReceiptRepository.save(receipt);

        return receipt;
    }

    /**
     * 유통기한 경고 승인 (pending_approval -> inspecting)
     */
    @Transactional
    public InboundReceipt approveShelfLifeWarning(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.Status.pending_approval) {
            throw new IllegalStateException("승인 대기 상태만 승인할 수 있습니다.");
        }

        receipt.setStatus(InboundReceipt.Status.inspecting);
        inboundReceiptRepository.save(receipt);

        return receipt;
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceipt getReceipt(UUID receiptId) {
        return inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InboundReceipt> getAllReceipts() {
        return inboundReceiptRepository.findAll();
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
// FILE: src\main\resources\application.properties
============================================================
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/wms
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA Configuration
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

# JSON Configuration
spring.jackson.serialization.write-dates-as-timestamps=false
spring.jackson.time-zone=UTC

# Server Configuration
server.port=8080

# Logging
logging.level.com.wms=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- ========================================
-- WMS Database Schema (Level 2)
-- Based on: ALS-WMS-CORE-002
-- ========================================

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
-- 8. 계절 설정 (Seasonal Config)
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
-- 9. 안전재고 기준 (Safety Stock Rules)
-- ========================================
CREATE TABLE safety_stock_rules (
    rule_id          UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id) UNIQUE,
    min_qty          INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty      INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

