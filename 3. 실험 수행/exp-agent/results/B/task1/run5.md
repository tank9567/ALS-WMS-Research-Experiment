# CLI Output

입고 처리 기능 구현이 완료되었습니다.

## 구현 완료 내용

### 1. 프로젝트 구조
- **pom.xml**: Spring Boot 3.2.0, Java 17, PostgreSQL 설정
- **application.yml**: 데이터베이스 연결 설정
- **schema.sql**: 전체 DB 스키마 (16개 테이블)

### 2. Entity 클래스
- `Product`, `Location`, `Supplier`, `SupplierPenalty`
- `PurchaseOrder`, `PurchaseOrderLine`
- `InboundReceipt`, `InboundReceiptLine`
- `Inventory`, `SeasonalConfig`

### 3. Repository
- 각 Entity에 대한 JpaRepository 인터페이스
- 커스텀 쿼리: 공급업체 페널티 조회, 성수기 조회 등

### 4. DTO
- `ApiResponse`: 공통 응답 포맷 (`success`, `data`, `error`)
- `InboundReceiptRequest/Response`: 입고 등록/조회용 DTO

### 5. Service (InboundReceiptService)
**핵심 비즈니스 로직:**
- ✅ 카테고리별 초과입고 허용률 (GENERAL 10%, FRESH 5%, HAZMAT 0%, HIGH_VALUE 3%)
- ✅ 발주 유형별 가중치 (NORMAL ×1, URGENT ×2, IMPORT ×1.5)
- ✅ 성수기 multiplier 적용 (seasonal_config 테이블 조회)
- ✅ HAZMAT은 모든 가중치 적용 시에도 0% 유지
- ✅ 유통기한 잔여율 체크 (<30% 거부, 30~50% 승인필요, >50% 정상)
- ✅ 제조일(manufacture_date) 필수 검증
- ✅ 보관유형 호환성 체크 (FROZEN/COLD/AMBIENT/HAZMAT)
- ✅ 실사 동결 로케이션 거부 (is_frozen=true)
- ✅ 공급업체 페널티 (OVER_DELIVERY, SHORT_SHELF_LIFE)
- ✅ 30일 내 3회 페널티 → PO hold 처리

### 6. Controller (InboundReceiptController)
**REST API 엔드포인트:**
- `POST /api/v1/inbound-receipts` - 입고 등록 (201 Created)
- `POST /api/v1/inbound-receipts/{id}/confirm` - 입고 확정 (200 OK)
- `POST /api/v1/inbound-receipts/{id}/reject` - 입고 거부 (200 OK)
- `POST /api/v1/inbound-receipts/{id}/approve` - 유통기한 경고 승인 (200 OK)
- `GET /api/v1/inbound-receipts/{id}` - 상세 조회 (200 OK)
- `GET /api/v1/inbound-receipts` - 목록 조회 (200 OK)

**HTTP 상태 코드:**
- 400: 필수 필드 누락, 유통기한 검증 실패
- 404: 리소스 없음
- 409: 초과입고, 보관유형 불일치, 로케이션 동결
- 201: 입고 등록 성공
- 200: 기타 성공

모든 요구사항을 충족하는 입고 처리 기능이 완성되었습니다.


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
    <description>Warehouse Management System Backend API</description>

    <properties>
        <java.version>17</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- Spring Boot Starter Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Boot Starter Data JPA -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- Spring Boot Starter Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
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

        <!-- Spring Boot Starter Test -->
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
// FILE: src\main\java\com\wms\controller\InboundReceiptController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.exception.BusinessException;
import com.wms.service.InboundReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    /**
     * POST /api/v1/inbound-receipts - 입고 등록
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
            @Valid @RequestBody InboundReceiptRequest request) {
        try {
            InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/confirm - 입고 확정
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(@PathVariable UUID id) {
        try {
            InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/reject - 입고 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        try {
            InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(id, reason);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/approve - 입고 승인 (pending_approval 상태)
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(@PathVariable UUID id) {
        try {
            InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * GET /api/v1/inbound-receipts/{id} - 입고 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(@PathVariable UUID id) {
        try {
            InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(id);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return handleBusinessException(e);
        }
    }

    /**
     * GET /api/v1/inbound-receipts - 입고 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InboundReceiptResponse>>> getAllInboundReceipts() {
        try {
            List<InboundReceiptResponse> responses = inboundReceiptService.getAllInboundReceipts();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (BusinessException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        }
    }

    /**
     * 비즈니스 예외 처리
     */
    private <T> ResponseEntity<ApiResponse<T>> handleBusinessException(BusinessException e) {
        HttpStatus status;

        switch (e.getCode()) {
            case "NOT_FOUND":
                status = HttpStatus.NOT_FOUND;
                break;
            case "VALIDATION_ERROR":
            case "SHELF_LIFE_REJECTION":
                status = HttpStatus.BAD_REQUEST;
                break;
            case "OVER_DELIVERY":
            case "STORAGE_INCOMPATIBLE":
            case "LOCATION_FROZEN":
            case "INVALID_STATUS":
                status = HttpStatus.CONFLICT;
                break;
            default:
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                break;
        }

        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    /**
     * 전역 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", e.getMessage()));
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
    private ErrorResponse error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorResponse.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorResponse {
        private String code;
        private String message;
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

    @NotEmpty(message = "At least one receipt line is required")
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
    private String poNumber;
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
// FILE: src\main\java\com\wms\entity\InboundReceipt.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceipt {

    @Id
    @Column(name = "receipt_id")
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
    private OffsetDateTime receivedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "inboundReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InboundReceiptLine> lines = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (receiptId == null) {
            receiptId = UUID.randomUUID();
        }
        if (status == null) {
            status = ReceiptStatus.inspecting;
        }
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_lines")
@Data
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
    public void prePersist() {
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    private Integer quantity;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "is_expired", nullable = false)
    private Boolean isExpired;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (inventoryId == null) {
            inventoryId = UUID.randomUUID();
        }
        if (quantity == null) {
            quantity = 0;
        }
        if (isExpired == null) {
            isExpired = false;
        }
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "locations")
@Data
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
    private StorageType storageType;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "current_qty", nullable = false)
    private Integer currentQty;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "is_frozen", nullable = false)
    private Boolean isFrozen;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (locationId == null) {
            locationId = UUID.randomUUID();
        }
        if (storageType == null) {
            storageType = StorageType.AMBIENT;
        }
        if (currentQty == null) {
            currentQty = 0;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (isFrozen == null) {
            isFrozen = false;
        }
    }

    public enum Zone {
        RECEIVING, STORAGE, SHIPPING, HAZMAT
    }

    public enum StorageType {
        AMBIENT, COLD, FROZEN
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@Data
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
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (productId == null) {
            productId = UUID.randomUUID();
        }
        if (category == null) {
            category = ProductCategory.GENERAL;
        }
        if (storageType == null) {
            storageType = StorageType.AMBIENT;
        }
        if (unit == null) {
            unit = "EA";
        }
        if (hasExpiry == null) {
            hasExpiry = false;
        }
        if (manufactureDateRequired == null) {
            manufactureDateRequired = false;
        }
        if (minRemainingShelfLifePct == null) {
            minRemainingShelfLifePct = 30;
        }
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    private PoType poType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PoStatus status;

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (poId == null) {
            poId = UUID.randomUUID();
        }
        if (poType == null) {
            poType = PoType.NORMAL;
        }
        if (status == null) {
            status = PoStatus.pending;
        }
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines")
@Data
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "ordered_qty", nullable = false)
    private Integer orderedQty;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @PrePersist
    public void prePersist() {
        if (poLineId == null) {
            poLineId = UUID.randomUUID();
        }
        if (receivedQty == null) {
            receivedQty = 0;
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
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seasonal_config")
@Data
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
    private BigDecimal multiplier;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (seasonId == null) {
            seasonId = UUID.randomUUID();
        }
        if (multiplier == null) {
            multiplier = new BigDecimal("1.50");
        }
        if (isActive == null) {
            isActive = true;
        }
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
@Data
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
    private SupplierStatus status;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (supplierId == null) {
            supplierId = UUID.randomUUID();
        }
        if (status == null) {
            status = SupplierStatus.active;
        }
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_penalties")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (penaltyId == null) {
            penaltyId = UUID.randomUUID();
        }
    }

    public enum PenaltyType {
        OVER_DELIVERY, SHORT_SHELF_LIFE
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\BusinessException.java
============================================================
package com.wms.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
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

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {
    Optional<Location> findByCode(String code);
}


============================================================
// FILE: src\main\java\com\wms\repository\ProductRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findBySku(String sku);
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Product;
import com.wms.entity.PurchaseOrder;
import com.wms.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    Optional<PurchaseOrderLine> findByPurchaseOrderAndProduct(PurchaseOrder purchaseOrder, Product product);
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
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findByPoNumber(String poNumber);
    List<PurchaseOrder> findBySupplierAndStatus(Supplier supplier, PurchaseOrder.PoStatus status);
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

import com.wms.entity.Supplier;
import com.wms.entity.SupplierPenalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {

    @Query("SELECT COUNT(sp) FROM SupplierPenalty sp WHERE sp.supplier = :supplier AND sp.createdAt >= :startDate")
    long countBySupplierAndCreatedAtAfter(@Param("supplier") Supplier supplier, @Param("startDate") OffsetDateTime startDate);
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
// FILE: src\main\java\com\wms\service\InboundReceiptService.java
============================================================
package com.wms.service;

import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    /**
     * 입고 등록 (inspecting 상태로 생성)
     */
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Purchase order not found"));

        // 2. 입고 receipt 생성
        InboundReceipt receipt = InboundReceipt.builder()
                .purchaseOrder(po)
                .receivedBy(request.getReceivedBy())
                .status(InboundReceipt.ReceiptStatus.inspecting)
                .receivedAt(OffsetDateTime.now())
                .build();

        // 3. 입고 라인 검증 및 추가
        List<InboundReceiptLine> lines = new ArrayList<>();
        boolean requiresApproval = false;

        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Product not found: " + lineReq.getProductId()));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Location not found: " + lineReq.getLocationId()));

            // 3-1. 유통기한 관리 상품 검증
            if (product.getHasExpiry()) {
                if (lineReq.getExpiryDate() == null || lineReq.getManufactureDate() == null) {
                    throw new BusinessException("VALIDATION_ERROR", "Expiry date and manufacture date are required for product: " + product.getSku());
                }

                // 잔여 유통기한 비율 체크
                double remainingPct = calculateRemainingShelfLifePct(lineReq.getExpiryDate(), lineReq.getManufactureDate());

                if (remainingPct < product.getMinRemainingShelfLifePct()) {
                    // 거부 및 페널티 부과
                    recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE, po.getPoId(),
                            "Remaining shelf life " + remainingPct + "% is below minimum " + product.getMinRemainingShelfLifePct() + "%");
                    throw new BusinessException("SHELF_LIFE_REJECTION", "Remaining shelf life is below minimum threshold");
                }

                if (remainingPct >= 30 && remainingPct < 50) {
                    // 승인 필요
                    requiresApproval = true;
                }
            }

            // 3-2. 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 3-3. 실사 동결 로케이션 체크
            if (location.getIsFrozen()) {
                throw new BusinessException("LOCATION_FROZEN", "Location is frozen for cycle count: " + location.getCode());
            }

            // 3-4. 초과입고 검증 (확정 시 체크하지만 미리 검증)
            validateOverDelivery(po, product, lineReq.getQuantity());

            InboundReceiptLine line = InboundReceiptLine.builder()
                    .inboundReceipt(receipt)
                    .product(product)
                    .location(location)
                    .quantity(lineReq.getQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .expiryDate(lineReq.getExpiryDate())
                    .manufactureDate(lineReq.getManufactureDate())
                    .build();

            lines.add(line);
        }

        receipt.setLines(lines);

        // 승인이 필요한 경우 상태 변경
        if (requiresApproval) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
        }

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    /**
     * 입고 확정
     */
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS", "Receipt cannot be confirmed in current status: " + receipt.getStatus());
        }

        PurchaseOrder po = receipt.getPurchaseOrder();

        // 각 라인별 처리
        for (InboundReceiptLine line : receipt.getLines()) {
            Product product = line.getProduct();
            Location location = line.getLocation();

            // 1. 초과입고 재검증
            validateOverDelivery(po, product, line.getQuantity());

            // 2. 재고 반영
            Inventory inventory = inventoryRepository
                    .findByProductAndLocationAndLotNumber(product, location, line.getLotNumber())
                    .orElse(Inventory.builder()
                            .product(product)
                            .location(location)
                            .lotNumber(line.getLotNumber())
                            .quantity(0)
                            .expiryDate(line.getExpiryDate())
                            .manufactureDate(line.getManufactureDate())
                            .receivedAt(OffsetDateTime.now())
                            .isExpired(false)
                            .build());

            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());

            if (inventory.getExpiryDate() == null && line.getExpiryDate() != null) {
                inventory.setExpiryDate(line.getExpiryDate());
            }
            if (inventory.getManufactureDate() == null && line.getManufactureDate() != null) {
                inventory.setManufactureDate(line.getManufactureDate());
            }

            inventoryRepository.save(inventory);

            // 3. 로케이션 current_qty 증가
            location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
            locationRepository.save(location);

            // 4. PO line received_qty 갱신
            PurchaseOrderLine poLine = purchaseOrderLineRepository
                    .findByPurchaseOrderAndProduct(po, product)
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "PO line not found for product: " + product.getSku()));

            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // 5. PO 상태 업데이트
        updatePurchaseOrderStatus(po);

        // 6. 입고 상태 변경
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    /**
     * 입고 거부
     */
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() == InboundReceipt.ReceiptStatus.confirmed ||
            receipt.getStatus() == InboundReceipt.ReceiptStatus.rejected) {
            throw new BusinessException("INVALID_STATUS", "Receipt cannot be rejected in current status: " + receipt.getStatus());
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    /**
     * 입고 승인 (pending_approval 상태에서)
     */
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS", "Receipt is not in pending_approval status");
        }

        // 승인 후 inspecting으로 변경하여 확정 가능하게 함
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return mapToResponse(savedReceipt);
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Inbound receipt not found"));

        return mapToResponse(receipt);
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        List<InboundReceipt> receipts = inboundReceiptRepository.findAll();
        return receipts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ===== Private Helper Methods =====

    /**
     * 초과입고 검증
     */
    private void validateOverDelivery(PurchaseOrder po, Product product, int incomingQty) {
        PurchaseOrderLine poLine = purchaseOrderLineRepository
                .findByPurchaseOrderAndProduct(po, product)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "Product not in PO: " + product.getSku()));

        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int totalAfterReceipt = receivedQty + incomingQty;

        // 카테고리별 기본 허용률
        double allowanceRate = getCategoryAllowanceRate(product.getCategory());

        // 발주 유형별 가중치
        double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());

        // 성수기 가중치
        double seasonalMultiplier = getSeasonalMultiplier();

        // HAZMAT은 어떤 경우에도 0%
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            allowanceRate = 0.0;
        } else {
            allowanceRate = allowanceRate * poTypeMultiplier * seasonalMultiplier;
        }

        double maxAllowed = orderedQty * (1 + allowanceRate / 100.0);

        if (totalAfterReceipt > maxAllowed) {
            // 초과입고 페널티 기록
            recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY, po.getPoId(),
                    "Over delivery: ordered=" + orderedQty + ", total=" + totalAfterReceipt + ", allowed=" + maxAllowed);

            throw new BusinessException("OVER_DELIVERY", "Exceeded maximum allowed quantity. Ordered: " + orderedQty +
                    ", Total after receipt: " + totalAfterReceipt + ", Max allowed: " + maxAllowed);
        }
    }

    /**
     * 카테고리별 초과입고 허용률 (기본값)
     */
    private double getCategoryAllowanceRate(Product.ProductCategory category) {
        switch (category) {
            case GENERAL:
                return 10.0;
            case FRESH:
                return 5.0;
            case HAZMAT:
                return 0.0;
            case HIGH_VALUE:
                return 3.0;
            default:
                return 10.0;
        }
    }

    /**
     * 발주 유형별 가중치
     */
    private double getPoTypeMultiplier(PurchaseOrder.PoType poType) {
        switch (poType) {
            case NORMAL:
                return 1.0;
            case URGENT:
                return 2.0;
            case IMPORT:
                return 1.5;
            default:
                return 1.0;
        }
    }

    /**
     * 성수기 가중치
     */
    private double getSeasonalMultiplier() {
        LocalDate today = LocalDate.now();
        return seasonalConfigRepository.findActiveSeasonByDate(today)
                .map(season -> season.getMultiplier().doubleValue())
                .orElse(1.0);
    }

    /**
     * 잔여 유통기한 비율 계산
     */
    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays == 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    /**
     * 보관 유형 호환성 검증
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Location.StorageType locationType = location.getStorageType();

        // HAZMAT은 HAZMAT zone만 허용
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new BusinessException("STORAGE_INCOMPATIBLE", "HAZMAT products must be stored in HAZMAT zone");
            }
            return;
        }

        // FROZEN 상품 → FROZEN 로케이션만
        if (productType == Product.StorageType.FROZEN && locationType != Location.StorageType.FROZEN) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "FROZEN products require FROZEN location");
        }

        // COLD 상품 → COLD 또는 FROZEN
        if (productType == Product.StorageType.COLD &&
            locationType != Location.StorageType.COLD &&
            locationType != Location.StorageType.FROZEN) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "COLD products require COLD or FROZEN location");
        }

        // AMBIENT 상품 → AMBIENT만
        if (productType == Product.StorageType.AMBIENT && locationType != Location.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_INCOMPATIBLE", "AMBIENT products require AMBIENT location");
        }
    }

    /**
     * 공급업체 페널티 기록 및 PO hold 처리
     */
    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType penaltyType, UUID poId, String description) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .penaltyType(penaltyType)
                .description(description)
                .poId(poId)
                .build();

        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 3회 이상 체크
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierAndCreatedAtAfter(supplier, thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // 해당 공급업체의 pending PO를 모두 hold로 변경
            List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findBySupplierAndStatus(supplier, PurchaseOrder.PoStatus.pending);
            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PurchaseOrder.PoStatus.hold);
                purchaseOrderRepository.save(po);
            }

            // 공급업체 상태도 hold로
            supplier.setStatus(Supplier.SupplierStatus.hold);
        }
    }

    /**
     * PO 상태 업데이트
     */
    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        boolean allCompleted = true;
        boolean anyReceived = false;

        for (PurchaseOrderLine line : po.getLines()) {
            if (line.getReceivedQty() > 0) {
                anyReceived = true;
            }
            if (line.getReceivedQty() < line.getOrderedQty()) {
                allCompleted = false;
            }
        }

        if (allCompleted) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * Entity -> Response DTO 변환
     */
    private InboundReceiptResponse mapToResponse(InboundReceipt receipt) {
        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = receipt.getLines().stream()
                .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
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
                        .build())
                .collect(Collectors.toList());

        return InboundReceiptResponse.builder()
                .receiptId(receipt.getReceiptId())
                .poId(receipt.getPurchaseOrder().getPoId())
                .poNumber(receipt.getPurchaseOrder().getPoNumber())
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
  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        jdbc:
          time_zone: UTC
    database-platform: org.hibernate.dialect.PostgreSQLDialect
  sql:
    init:
      mode: never

server:
  port: 8080


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- 1. 상품 마스터
CREATE TABLE IF NOT EXISTS products (
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

-- 2. 로케이션
CREATE TABLE IF NOT EXISTS locations (
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

-- 3. 재고
CREATE TABLE IF NOT EXISTS inventory (
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

-- 4. 공급업체
CREATE TABLE IF NOT EXISTS suppliers (
    supplier_id     UUID PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    contact_info    VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'hold', 'inactive')),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 5. 공급업체 페널티
CREATE TABLE IF NOT EXISTS supplier_penalties (
    penalty_id      UUID PRIMARY KEY,
    supplier_id     UUID NOT NULL REFERENCES suppliers(supplier_id),
    penalty_type    VARCHAR(50) NOT NULL
                    CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    description     VARCHAR(500),
    po_id           UUID,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 6. 발주서
CREATE TABLE IF NOT EXISTS purchase_orders (
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

CREATE TABLE IF NOT EXISTS purchase_order_lines (
    po_line_id      UUID PRIMARY KEY,
    po_id           UUID NOT NULL REFERENCES purchase_orders(po_id),
    product_id      UUID NOT NULL REFERENCES products(product_id),
    ordered_qty     INTEGER NOT NULL CHECK (ordered_qty > 0),
    received_qty    INTEGER NOT NULL DEFAULT 0 CHECK (received_qty >= 0),
    unit_price      NUMERIC(12,2),
    UNIQUE (po_id, product_id)
);

-- 7. 입고
CREATE TABLE IF NOT EXISTS inbound_receipts (
    receipt_id      UUID PRIMARY KEY,
    po_id           UUID NOT NULL REFERENCES purchase_orders(po_id),
    status          VARCHAR(20) NOT NULL DEFAULT 'inspecting'
                    CHECK (status IN ('inspecting', 'pending_approval', 'confirmed', 'rejected')),
    received_by     VARCHAR(100) NOT NULL,
    received_at     TIMESTAMPTZ DEFAULT NOW(),
    confirmed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS inbound_receipt_lines (
    receipt_line_id UUID PRIMARY KEY,
    receipt_id      UUID NOT NULL REFERENCES inbound_receipts(receipt_id),
    product_id      UUID NOT NULL REFERENCES products(product_id),
    location_id     UUID NOT NULL REFERENCES locations(location_id),
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    lot_number      VARCHAR(50),
    expiry_date     DATE,
    manufacture_date DATE
);

-- 8. 출고 지시서
CREATE TABLE IF NOT EXISTS shipment_orders (
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

CREATE TABLE IF NOT EXISTS shipment_order_lines (
    shipment_line_id UUID PRIMARY KEY,
    shipment_id      UUID NOT NULL REFERENCES shipment_orders(shipment_id),
    product_id       UUID NOT NULL REFERENCES products(product_id),
    requested_qty    INTEGER NOT NULL CHECK (requested_qty > 0),
    picked_qty       INTEGER NOT NULL DEFAULT 0 CHECK (picked_qty >= 0),
    status           VARCHAR(20) NOT NULL DEFAULT 'pending'
                     CHECK (status IN ('pending', 'picked', 'partial', 'backordered'))
);

-- 9. 백오더
CREATE TABLE IF NOT EXISTS backorders (
    backorder_id     UUID PRIMARY KEY,
    shipment_line_id UUID NOT NULL REFERENCES shipment_order_lines(shipment_line_id),
    product_id       UUID NOT NULL REFERENCES products(product_id),
    shortage_qty     INTEGER NOT NULL CHECK (shortage_qty > 0),
    status           VARCHAR(20) NOT NULL DEFAULT 'open'
                     CHECK (status IN ('open', 'fulfilled', 'cancelled')),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    fulfilled_at     TIMESTAMPTZ
);

-- 10. 재고 이동
CREATE TABLE IF NOT EXISTS stock_transfers (
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

-- 11. 재고 조정
CREATE TABLE IF NOT EXISTS inventory_adjustments (
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

-- 12. 감사 로그
CREATE TABLE IF NOT EXISTS audit_logs (
    log_id           UUID PRIMARY KEY,
    event_type       VARCHAR(50) NOT NULL,
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        UUID NOT NULL,
    details          JSONB NOT NULL,
    performed_by     VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 13. 안전재고 기준
CREATE TABLE IF NOT EXISTS safety_stock_rules (
    rule_id          UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id) UNIQUE,
    min_qty          INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty      INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 14. 자동 재발주 이력
CREATE TABLE IF NOT EXISTS auto_reorder_logs (
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

-- 15. 계절 설정
CREATE TABLE IF NOT EXISTS seasonal_config (
    season_id        UUID PRIMARY KEY,
    season_name      VARCHAR(100) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    multiplier       NUMERIC(3,2) NOT NULL DEFAULT 1.50,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 16. 실사 세션
CREATE TABLE IF NOT EXISTS cycle_counts (
    cycle_count_id   UUID PRIMARY KEY,
    location_id      UUID NOT NULL REFERENCES locations(location_id),
    status           VARCHAR(20) NOT NULL DEFAULT 'in_progress'
                     CHECK (status IN ('in_progress', 'completed')),
    started_by       VARCHAR(100) NOT NULL,
    started_at       TIMESTAMPTZ DEFAULT NOW(),
    completed_at     TIMESTAMPTZ
);

