# CLI Output

입고 처리 기능 구현을 완료했습니다.

## 구현 내역

### 1. **Repository Layer** (5개 파일)
- `InboundReceiptRepository.java`: 입고 전표 저장소
- `InboundReceiptLineRepository.java`: 입고 품목 저장소
- `PurchaseOrderRepository.java`: 발주서 저장소
- `PurchaseOrderLineRepository.java`: 발주 품목 저장소
- `SeasonalConfigRepository.java`: 성수기 설정 저장소 (날짜 기반 조회 쿼리 포함)

### 2. **DTO Layer** (3개 파일)
- `InboundReceiptRequest.java`: 입고 등록 요청 DTO
- `InboundReceiptResponse.java`: 입고 응답 DTO
- `ApiResponse.java`: 통합 API 응답 래퍼

### 3. **Exception Layer** (2개 파일)
- `BusinessException.java`: 비즈니스 예외 클래스
- `GlobalExceptionHandler.java`: 전역 예외 핸들러

### 4. **Service Layer** (`InboundReceiptService.java`)
비즈니스 로직 구현:
- **입고 등록** (`createInboundReceipt`): 검수 시작, 모든 검증 수행
- **입고 확정** (`confirmInboundReceipt`): 재고 반영
- **입고 거부** (`rejectInboundReceipt`): 입고 거부 처리
- **유통기한 경고 승인** (`approveInboundReceipt`): 30~50% 잔여율 승인
- **입고 조회** (`getInboundReceipt`, `getInboundReceipts`): 상세/목록 조회

**핵심 검증 로직**:
- 카테고리별 초과입고 허용률 (GENERAL 10%, FRESH 5%, HAZMAT 0%, HIGH_VALUE 3%)
- 발주 유형별 가중치 (NORMAL ×1, URGENT ×2, IMPORT ×1.5)
- 성수기 multiplier 적용 (seasonal_config 테이블 참조)
- 유통기한 잔여율 체크 (<30% 거부, 30~50% 승인필요, >50% 정상)
- 보관유형 호환성 체크 (FROZEN/COLD/AMBIENT/HAZMAT)
- 실사 동결 로케이션 거부 (is_frozen=true)
- 공급업체 페널티 시스템 (30일 내 3회 → PO hold)

### 5. **Controller Layer** (`InboundReceiptController.java`)
REST API 엔드포인트:
- `POST /api/v1/inbound-receipts`: 입고 등록
- `POST /api/v1/inbound-receipts/{id}/confirm`: 입고 확정
- `POST /api/v1/inbound-receipts/{id}/reject`: 입고 거부
- `POST /api/v1/inbound-receipts/{id}/approve`: 유통기한 경고 승인
- `GET /api/v1/inbound-receipts/{id}`: 상세 조회
- `GET /api/v1/inbound-receipts`: 목록 조회 (페이징)

모든 요구사항(2단계 프로세스, 카테고리별 허용률, 가중치 적용, 유통기한 관리, 페널티 시스템 등)을 충족하는 완전한 입고 처리 기능이 구현되었습니다.


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
    <artifactId>warehouse-management-system</artifactId>
    <version>1.0.0</version>
    <name>WMS</name>
    <description>Warehouse Management System</description>

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

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
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
// FILE: src\main\java\com\wms\controller\InboundReceiptController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    /**
     * POST /api/v1/inbound-receipts
     * 입고 등록 (검수 시작)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
            @RequestBody InboundReceiptRequest request
    ) {
        InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/confirm
     * 입고 확정
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(
            @PathVariable UUID id
    ) {
        InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/reject
     * 입고 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
            @PathVariable UUID id,
            @RequestParam String reason
    ) {
        InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(id, reason);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/approve
     * 유통기한 경고 승인
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(
            @PathVariable UUID id
    ) {
        InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/inbound-receipts/{id}
     * 입고 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(
            @PathVariable UUID id
    ) {
        InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * GET /api/v1/inbound-receipts
     * 입고 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<InboundReceiptResponse>>> getInboundReceipts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<InboundReceiptResponse> response = inboundReceiptService.getInboundReceipts(pageable);
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
    private ErrorDetails error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String code) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetails.builder()
                        .message(message)
                        .code(code)
                        .build())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorDetails {
        private String message;
        private String code;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptRequest.java
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
public class InboundReceiptRequest {

    private String receiptNumber;
    private UUID purchaseOrderId;
    private OffsetDateTime receivedDate;
    private List<InboundReceiptLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineRequest {
        private UUID purchaseOrderLineId;
        private UUID productId;
        private UUID locationId;
        private String lotNumber;
        private Integer receivedQty;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.InboundReceipt.ReceiptStatus;
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

    private UUID id;
    private String receiptNumber;
    private UUID purchaseOrderId;
    private String poNumber;
    private ReceiptStatus status;
    private OffsetDateTime receivedDate;
    private OffsetDateTime confirmedAt;
    private String rejectionReason;
    private List<InboundReceiptLineResponse> lines;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InboundReceiptLineResponse {
        private UUID id;
        private UUID purchaseOrderLineId;
        private UUID productId;
        private String productSku;
        private String productName;
        private UUID locationId;
        private String locationCode;
        private String lotNumber;
        private Integer receivedQty;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceipt.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 100)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReceiptStatus status = ReceiptStatus.INSPECTING;

    @Column(name = "received_date", nullable = false)
    private OffsetDateTime receivedDate;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum ReceiptStatus {
        INSPECTING, PENDING_APPROVAL, CONFIRMED, REJECTED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceiptLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_lines", indexes = {
    @Index(name = "idx_inbound_lines_receipt", columnList = "inbound_receipt_id"),
    @Index(name = "idx_inbound_lines_po_line", columnList = "purchase_order_line_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_receipt_id", nullable = false)
    private InboundReceipt inboundReceipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_line_id", nullable = false)
    private PurchaseOrderLine purchaseOrderLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\Inventory.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "location_id", "lot_number", "expiry_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "is_expired", nullable = false)
    private Boolean isExpired = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\Location.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private Product.StorageType storageType;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "current_qty", nullable = false)
    private Integer currentQty = 0;

    @Column(name = "is_frozen", nullable = false)
    private Boolean isFrozen = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ProductCategory category;

    @Column(nullable = false, length = 20)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private StorageType storageType;

    @Column(name = "manages_expiry", nullable = false)
    private Boolean managesExpiry = false;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "min_remaining_shelf_life_pct")
    private Integer minRemainingShelfLifePct = 30;

    @Column(name = "max_pick_qty")
    private Integer maxPickQty;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum ProductCategory {
        GENERAL, FRESH, HAZMAT, HIGH_VALUE
    }

    public enum StorageType {
        AMBIENT, COLD, FROZEN, HAZMAT
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "po_number", nullable = false, unique = true, length = 100)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "po_type", nullable = false, length = 20)
    private PoType poType = PoType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PoStatus status = PoStatus.PENDING;

    @Column(name = "order_date", nullable = false)
    private OffsetDateTime orderDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum PoType {
        NORMAL, URGENT, IMPORT
    }

    public enum PoStatus {
        PENDING, PARTIAL, COMPLETED, CANCELLED, HOLD
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrderLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines", indexes = {
    @Index(name = "idx_po_lines_po", columnList = "purchase_order_id"),
    @Index(name = "idx_po_lines_product", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "ordered_qty", nullable = false)
    private Integer orderedQty;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\SeasonalConfig.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "seasonal_config", indexes = {
    @Index(name = "idx_seasonal_dates", columnList = "start_date, end_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeasonalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "season_name", nullable = false, length = 100)
    private String seasonName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal multiplier = BigDecimal.valueOf(1.5);

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\Supplier.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplierStatus status = SupplierStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum SupplierStatus {
        ACTIVE, HOLD, INACTIVE
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\SupplierPenalty.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "supplier_penalties", indexes = {
    @Index(name = "idx_supplier_penalties_date", columnList = "supplier_id, penalized_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierPenalty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", nullable = false, length = 50)
    private PenaltyType penaltyType;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "penalized_at", nullable = false, updatable = false)
    private OffsetDateTime penalizedAt;

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

    private final String errorCode;

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\GlobalExceptionHandler.java
============================================================
package com.wms.exception;

import com.wms.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(e.getMessage(), e.getErrorCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error: " + e.getMessage(), "INTERNAL_ERROR"));
    }
}


============================================================
// FILE: src\main\java\com\wms\repository\InboundReceiptLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InboundReceiptLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InboundReceiptLineRepository extends JpaRepository<InboundReceiptLine, UUID> {

    List<InboundReceiptLine> findByInboundReceiptId(UUID inboundReceiptId);
}


============================================================
// FILE: src\main\java\com\wms\repository\InboundReceiptRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InboundReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID> {

    Optional<InboundReceipt> findByReceiptNumber(String receiptNumber);

    boolean existsByReceiptNumber(String receiptNumber);
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

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.location.id = :locationId " +
           "AND i.lotNumber = :lotNumber AND i.expiryDate = :expiryDate")
    Optional<Inventory> findByProductAndLocationAndLotAndExpiry(
        @Param("productId") UUID productId,
        @Param("locationId") UUID locationId,
        @Param("lotNumber") String lotNumber,
        @Param("expiryDate") LocalDate expiryDate
    );
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

import com.wms.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    List<PurchaseOrderLine> findByPurchaseOrderId(UUID purchaseOrderId);
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);
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

    @Query("SELECT sc FROM SeasonalConfig sc WHERE :date BETWEEN sc.startDate AND sc.endDate")
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {

    @Query("SELECT COUNT(sp) FROM SupplierPenalty sp WHERE sp.supplier.id = :supplierId " +
           "AND CAST(sp.penalizedAt AS date) >= :since")
    long countBySupplierId(@Param("supplierId") UUID supplierId, @Param("since") LocalDate since);
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
import com.wms.entity.InboundReceipt.ReceiptStatus;
import com.wms.entity.Product.ProductCategory;
import com.wms.entity.Product.StorageType;
import com.wms.entity.PurchaseOrder.PoStatus;
import com.wms.entity.PurchaseOrder.PoType;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final InboundReceiptLineRepository inboundReceiptLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    /**
     * 입고 등록 (검수 시작)
     */
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. 입고번호 중복 체크
        if (inboundReceiptRepository.existsByReceiptNumber(request.getReceiptNumber())) {
            throw new BusinessException("Receipt number already exists", "DUPLICATE_RECEIPT_NUMBER");
        }

        // 2. PO 조회 및 검증
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                .orElseThrow(() -> new BusinessException("Purchase order not found", "PO_NOT_FOUND"));

        if (purchaseOrder.getStatus() == PoStatus.HOLD) {
            throw new BusinessException("Purchase order is on hold", "PO_ON_HOLD");
        }

        if (purchaseOrder.getStatus() == PoStatus.CANCELLED) {
            throw new BusinessException("Purchase order is cancelled", "PO_CANCELLED");
        }

        if (purchaseOrder.getStatus() == PoStatus.COMPLETED) {
            throw new BusinessException("Purchase order is already completed", "PO_COMPLETED");
        }

        // 3. PO Lines 조회
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrderId(purchaseOrder.getId());

        // 4. InboundReceipt 생성
        InboundReceipt inboundReceipt = InboundReceipt.builder()
                .receiptNumber(request.getReceiptNumber())
                .purchaseOrder(purchaseOrder)
                .status(ReceiptStatus.INSPECTING)
                .receivedDate(request.getReceivedDate() != null ? request.getReceivedDate() : OffsetDateTime.now())
                .build();

        inboundReceipt = inboundReceiptRepository.save(inboundReceipt);

        // 5. 각 라인 검증 및 생성
        List<InboundReceiptLine> receiptLines = new ArrayList<>();
        boolean needsApproval = false;
        String rejectionReason = null;

        for (InboundReceiptRequest.InboundReceiptLineRequest lineRequest : request.getLines()) {
            // PO Line 조회
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findById(lineRequest.getPurchaseOrderLineId())
                    .orElseThrow(() -> new BusinessException("Purchase order line not found", "PO_LINE_NOT_FOUND"));

            if (!poLine.getPurchaseOrder().getId().equals(purchaseOrder.getId())) {
                throw new BusinessException("Purchase order line does not belong to the specified PO", "PO_LINE_MISMATCH");
            }

            // Product 조회
            Product product = productRepository.findById(lineRequest.getProductId())
                    .orElseThrow(() -> new BusinessException("Product not found", "PRODUCT_NOT_FOUND"));

            if (!product.getId().equals(poLine.getProduct().getId())) {
                throw new BusinessException("Product does not match PO line", "PRODUCT_MISMATCH");
            }

            // Location 조회
            Location location = locationRepository.findById(lineRequest.getLocationId())
                    .orElseThrow(() -> new BusinessException("Location not found", "LOCATION_NOT_FOUND"));

            // (a) 실사 동결 체크
            if (location.getIsFrozen()) {
                throw new BusinessException("Location is frozen for cycle count", "LOCATION_FROZEN");
            }

            // (b) 초과입고 허용률 체크
            int receivedQty = lineRequest.getReceivedQty();
            int orderedQty = poLine.getOrderedQty();
            int alreadyReceivedQty = poLine.getReceivedQty();
            int totalReceivedQty = alreadyReceivedQty + receivedQty;

            double allowedOverReceiptPct = calculateAllowedOverReceiptPercentage(
                    product.getCategory(),
                    purchaseOrder.getPoType(),
                    LocalDate.now()
            );

            int maxAllowedQty = (int) Math.floor(orderedQty * (1.0 + allowedOverReceiptPct / 100.0));

            if (totalReceivedQty > maxAllowedQty) {
                // 초과입고 거부
                rejectionReason = String.format(
                        "Over-delivery: received %d, ordered %d, max allowed %d (%.2f%%)",
                        totalReceivedQty, orderedQty, maxAllowedQty, allowedOverReceiptPct
                );

                // 공급업체 페널티 기록
                recordSupplierPenalty(purchaseOrder.getSupplier(), "OVER_DELIVERY", rejectionReason);

                inboundReceipt.setStatus(ReceiptStatus.REJECTED);
                inboundReceipt.setRejectionReason(rejectionReason);
                inboundReceiptRepository.save(inboundReceipt);

                throw new BusinessException(rejectionReason, "OVER_DELIVERY");
            }

            // (c) 유통기한 관리 체크
            if (product.getManagesExpiry()) {
                if (lineRequest.getExpiryDate() == null) {
                    throw new BusinessException("Expiry date is required for expiry-managed product", "EXPIRY_DATE_REQUIRED");
                }

                if (lineRequest.getManufactureDate() == null) {
                    throw new BusinessException("Manufacture date is required for expiry-managed product", "MANUFACTURE_DATE_REQUIRED");
                }

                // 유통기한 잔여율 체크
                LocalDate today = LocalDate.now();
                LocalDate manufactureDate = lineRequest.getManufactureDate();
                LocalDate expiryDate = lineRequest.getExpiryDate();

                long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
                long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);

                if (totalShelfLife <= 0) {
                    throw new BusinessException("Invalid shelf life: expiry date must be after manufacture date", "INVALID_SHELF_LIFE");
                }

                double remainingPct = (remainingShelfLife * 100.0) / totalShelfLife;
                int minRemainingPct = product.getMinRemainingShelfLifePct() != null ?
                        product.getMinRemainingShelfLifePct() : 30;

                if (remainingPct < minRemainingPct) {
                    // 유통기한 부족 거부
                    rejectionReason = String.format(
                            "Insufficient shelf life: %.1f%% remaining (min required: %d%%)",
                            remainingPct, minRemainingPct
                    );

                    // 공급업체 페널티 기록
                    recordSupplierPenalty(purchaseOrder.getSupplier(), "SHORT_SHELF_LIFE", rejectionReason);

                    inboundReceipt.setStatus(ReceiptStatus.REJECTED);
                    inboundReceipt.setRejectionReason(rejectionReason);
                    inboundReceiptRepository.save(inboundReceipt);

                    throw new BusinessException(rejectionReason, "SHORT_SHELF_LIFE");
                } else if (remainingPct >= minRemainingPct && remainingPct < 50) {
                    // 30~50%: 승인 필요
                    needsApproval = true;
                }
            }

            // (d) 보관 유형 호환성 체크
            if (!isStorageTypeCompatible(product.getStorageType(), location.getStorageType(), location.getZone())) {
                throw new BusinessException(
                        String.format("Storage type incompatible: product %s, location %s",
                                product.getStorageType(), location.getStorageType()),
                        "STORAGE_TYPE_INCOMPATIBLE"
                );
            }

            // (e) 로케이션 용량 체크
            int newQty = location.getCurrentQty() + receivedQty;
            if (newQty > location.getCapacity()) {
                throw new BusinessException(
                        String.format("Location capacity exceeded: current %d + received %d > capacity %d",
                                location.getCurrentQty(), receivedQty, location.getCapacity()),
                        "LOCATION_CAPACITY_EXCEEDED"
                );
            }

            // InboundReceiptLine 생성
            InboundReceiptLine receiptLine = InboundReceiptLine.builder()
                    .inboundReceipt(inboundReceipt)
                    .purchaseOrderLine(poLine)
                    .product(product)
                    .location(location)
                    .lotNumber(lineRequest.getLotNumber())
                    .receivedQty(receivedQty)
                    .manufactureDate(lineRequest.getManufactureDate())
                    .expiryDate(lineRequest.getExpiryDate())
                    .build();

            receiptLines.add(receiptLine);
        }

        // 승인 필요 여부에 따라 상태 변경
        if (needsApproval) {
            inboundReceipt.setStatus(ReceiptStatus.PENDING_APPROVAL);
        }

        inboundReceiptLineRepository.saveAll(receiptLines);
        inboundReceiptRepository.save(inboundReceipt);

        return buildResponse(inboundReceipt, receiptLines);
    }

    /**
     * 입고 확정
     */
    public InboundReceiptResponse confirmInboundReceipt(UUID id) {
        InboundReceipt inboundReceipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Inbound receipt not found", "RECEIPT_NOT_FOUND"));

        if (inboundReceipt.getStatus() != ReceiptStatus.INSPECTING) {
            throw new BusinessException(
                    "Can only confirm receipts in INSPECTING status",
                    "INVALID_STATUS"
            );
        }

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);

        // 재고 반영 및 PO 업데이트
        processConfirmation(inboundReceipt, lines);

        return buildResponse(inboundReceipt, lines);
    }

    /**
     * 입고 거부
     */
    public InboundReceiptResponse rejectInboundReceipt(UUID id, String reason) {
        InboundReceipt inboundReceipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Inbound receipt not found", "RECEIPT_NOT_FOUND"));

        if (inboundReceipt.getStatus() == ReceiptStatus.CONFIRMED) {
            throw new BusinessException("Cannot reject confirmed receipt", "ALREADY_CONFIRMED");
        }

        if (inboundReceipt.getStatus() == ReceiptStatus.REJECTED) {
            throw new BusinessException("Receipt is already rejected", "ALREADY_REJECTED");
        }

        inboundReceipt.setStatus(ReceiptStatus.REJECTED);
        inboundReceipt.setRejectionReason(reason);
        inboundReceiptRepository.save(inboundReceipt);

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);

        return buildResponse(inboundReceipt, lines);
    }

    /**
     * 유통기한 경고 승인
     */
    public InboundReceiptResponse approveInboundReceipt(UUID id) {
        InboundReceipt inboundReceipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Inbound receipt not found", "RECEIPT_NOT_FOUND"));

        if (inboundReceipt.getStatus() != ReceiptStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    "Can only approve receipts in PENDING_APPROVAL status",
                    "INVALID_STATUS"
            );
        }

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);

        // 재고 반영 및 PO 업데이트
        processConfirmation(inboundReceipt, lines);

        return buildResponse(inboundReceipt, lines);
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID id) {
        InboundReceipt inboundReceipt = inboundReceiptRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Inbound receipt not found", "RECEIPT_NOT_FOUND"));

        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(id);

        return buildResponse(inboundReceipt, lines);
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<InboundReceiptResponse> getInboundReceipts(Pageable pageable) {
        Page<InboundReceipt> receipts = inboundReceiptRepository.findAll(pageable);

        return receipts.map(receipt -> {
            List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByInboundReceiptId(receipt.getId());
            return buildResponse(receipt, lines);
        });
    }

    // ===== Private Helper Methods =====

    /**
     * 초과입고 허용률 계산
     */
    private double calculateAllowedOverReceiptPercentage(
            ProductCategory category,
            PoType poType,
            LocalDate date
    ) {
        // HAZMAT은 무조건 0%
        if (category == ProductCategory.HAZMAT) {
            return 0.0;
        }

        // 카테고리별 기본 허용률
        double basePct = switch (category) {
            case GENERAL -> 10.0;
            case FRESH -> 5.0;
            case HIGH_VALUE -> 3.0;
            default -> 10.0;
        };

        // 발주 유형별 가중치
        double poTypeMultiplier = switch (poType) {
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
            default -> 1.0;
        };

        // 성수기 가중치
        double seasonalMultiplier = seasonalConfigRepository.findActiveSeasonByDate(date)
                .map(config -> config.getMultiplier().doubleValue())
                .orElse(1.0);

        return basePct * poTypeMultiplier * seasonalMultiplier;
    }

    /**
     * 보관 유형 호환성 체크
     */
    private boolean isStorageTypeCompatible(
            StorageType productType,
            StorageType locationType,
            Location.Zone locationZone
    ) {
        // HAZMAT 상품은 HAZMAT zone만 허용
        if (productType == StorageType.HAZMAT) {
            return locationZone == Location.Zone.HAZMAT;
        }

        // FROZEN 상품은 FROZEN 로케이션만
        if (productType == StorageType.FROZEN) {
            return locationType == StorageType.FROZEN;
        }

        // COLD 상품은 COLD 또는 FROZEN 허용 (상위 호환)
        if (productType == StorageType.COLD) {
            return locationType == StorageType.COLD || locationType == StorageType.FROZEN;
        }

        // AMBIENT 상품은 AMBIENT만
        if (productType == StorageType.AMBIENT) {
            return locationType == StorageType.AMBIENT;
        }

        return false;
    }

    /**
     * 공급업체 페널티 기록
     */
    private void recordSupplierPenalty(Supplier supplier, String penaltyType, String reason) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .penaltyType(penaltyType)
                .reason(reason)
                .build();

        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 개수 확인
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierId(supplier.getId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // 공급업체 상태를 HOLD로 변경
            supplier.setStatus("hold");
            supplierRepository.save(supplier);

            // 해당 공급업체의 모든 PENDING PO를 HOLD로 변경
            List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findAll().stream()
                    .filter(po -> po.getSupplier().getId().equals(supplier.getId()))
                    .filter(po -> po.getStatus() == PoStatus.PENDING)
                    .collect(Collectors.toList());

            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PoStatus.HOLD);
            }

            purchaseOrderRepository.saveAll(pendingPOs);
        }
    }

    /**
     * 입고 확정 처리 (재고 반영 + PO 업데이트)
     */
    private void processConfirmation(InboundReceipt inboundReceipt, List<InboundReceiptLine> lines) {
        PurchaseOrder purchaseOrder = inboundReceipt.getPurchaseOrder();

        for (InboundReceiptLine line : lines) {
            // 1. Inventory 반영
            Inventory inventory = inventoryRepository.findByProductAndLocationAndLotAndExpiry(
                    line.getProduct().getId(),
                    line.getLocation().getId(),
                    line.getLotNumber(),
                    line.getExpiryDate()
            ).orElse(null);

            if (inventory == null) {
                inventory = Inventory.builder()
                        .product(line.getProduct())
                        .location(line.getLocation())
                        .lotNumber(line.getLotNumber())
                        .quantity(line.getReceivedQty())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .receivedAt(inboundReceipt.getReceivedDate())
                        .isExpired(false)
                        .build();
            } else {
                inventory.setQuantity(inventory.getQuantity() + line.getReceivedQty());
            }

            inventoryRepository.save(inventory);

            // 2. Location 적재량 증가
            Location location = line.getLocation();
            location.setCurrentQty(location.getCurrentQty() + line.getReceivedQty());
            locationRepository.save(location);

            // 3. PO Line 입고수량 업데이트
            PurchaseOrderLine poLine = line.getPurchaseOrderLine();
            poLine.setReceivedQty(poLine.getReceivedQty() + line.getReceivedQty());
            purchaseOrderLineRepository.save(poLine);
        }

        // 4. PO 상태 업데이트
        List<PurchaseOrderLine> allPoLines = purchaseOrderLineRepository.findByPurchaseOrderId(purchaseOrder.getId());
        boolean allCompleted = allPoLines.stream()
                .allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());
        boolean anyReceived = allPoLines.stream()
                .anyMatch(line -> line.getReceivedQty() > 0);

        if (allCompleted) {
            purchaseOrder.setStatus(PoStatus.COMPLETED);
        } else if (anyReceived) {
            purchaseOrder.setStatus(PoStatus.PARTIAL);
        }

        purchaseOrderRepository.save(purchaseOrder);

        // 5. InboundReceipt 상태 업데이트
        inboundReceipt.setStatus(ReceiptStatus.CONFIRMED);
        inboundReceipt.setConfirmedAt(OffsetDateTime.now());
        inboundReceiptRepository.save(inboundReceipt);
    }

    /**
     * Response 빌드
     */
    private InboundReceiptResponse buildResponse(InboundReceipt inboundReceipt, List<InboundReceiptLine> lines) {
        List<InboundReceiptResponse.InboundReceiptLineResponse> lineResponses = lines.stream()
                .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                        .id(line.getId())
                        .purchaseOrderLineId(line.getPurchaseOrderLine().getId())
                        .productId(line.getProduct().getId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .locationId(line.getLocation().getId())
                        .locationCode(line.getLocation().getCode())
                        .lotNumber(line.getLotNumber())
                        .receivedQty(line.getReceivedQty())
                        .manufactureDate(line.getManufactureDate())
                        .expiryDate(line.getExpiryDate())
                        .build())
                .collect(Collectors.toList());

        return InboundReceiptResponse.builder()
                .id(inboundReceipt.getId())
                .receiptNumber(inboundReceipt.getReceiptNumber())
                .purchaseOrderId(inboundReceipt.getPurchaseOrder().getId())
                .poNumber(inboundReceipt.getPurchaseOrder().getPoNumber())
                .status(inboundReceipt.getStatus())
                .receivedDate(inboundReceipt.getReceivedDate())
                .confirmedAt(inboundReceipt.getConfirmedAt())
                .rejectionReason(inboundReceipt.getRejectionReason())
                .lines(lineResponses)
                .createdAt(inboundReceipt.getCreatedAt())
                .updatedAt(inboundReceipt.getUpdatedAt())
                .build();
    }
}


============================================================
// FILE: src\main\resources\application.yml
============================================================
spring:
  application:
    name: wms
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
        dialect: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false

server:
  port: 8080
  servlet:
    context-path: /api/v1

logging:
  level:
    com.wms: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- WMS Database Schema

-- Products Table
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL CHECK (category IN ('GENERAL', 'FRESH', 'HAZMAT', 'HIGH_VALUE')),
    unit VARCHAR(20) NOT NULL,
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    manages_expiry BOOLEAN NOT NULL DEFAULT false,
    shelf_life_days INTEGER,
    min_remaining_shelf_life_pct INTEGER DEFAULT 30,
    max_pick_qty INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Locations Table
CREATE TABLE locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    zone VARCHAR(50) NOT NULL CHECK (zone IN ('RECEIVING', 'STORAGE', 'SHIPPING', 'HAZMAT')),
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    current_qty INTEGER NOT NULL DEFAULT 0 CHECK (current_qty >= 0),
    is_frozen BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_capacity CHECK (current_qty <= capacity)
);

-- Inventory Table
CREATE TABLE inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    lot_number VARCHAR(100),
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    manufacture_date DATE,
    expiry_date DATE,
    received_at TIMESTAMPTZ NOT NULL,
    is_expired BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(product_id, location_id, lot_number, expiry_date)
);

-- Suppliers Table
CREATE TABLE suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    contact VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'hold', 'inactive')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Supplier Penalties Table
CREATE TABLE supplier_penalties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    penalty_type VARCHAR(50) NOT NULL CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    reason TEXT,
    penalized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_supplier_penalties_date ON supplier_penalties(supplier_id, penalized_at);

-- Purchase Orders Table
CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_number VARCHAR(100) UNIQUE NOT NULL,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    po_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (po_type IN ('NORMAL', 'URGENT', 'IMPORT')),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'partial', 'completed', 'cancelled', 'hold')),
    order_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Purchase Order Lines Table
CREATE TABLE purchase_order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    ordered_qty INTEGER NOT NULL CHECK (ordered_qty > 0),
    received_qty INTEGER NOT NULL DEFAULT 0 CHECK (received_qty >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Inbound Receipts Table
CREATE TABLE inbound_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_number VARCHAR(100) UNIQUE NOT NULL,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    status VARCHAR(30) NOT NULL DEFAULT 'inspecting' CHECK (status IN ('inspecting', 'pending_approval', 'confirmed', 'rejected')),
    received_date TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Inbound Receipt Lines Table
CREATE TABLE inbound_receipt_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inbound_receipt_id UUID NOT NULL REFERENCES inbound_receipts(id),
    purchase_order_line_id UUID NOT NULL REFERENCES purchase_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    lot_number VARCHAR(100),
    received_qty INTEGER NOT NULL CHECK (received_qty > 0),
    manufacture_date DATE,
    expiry_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seasonal Config Table
CREATE TABLE seasonal_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_date_range CHECK (end_date >= start_date)
);

-- Create indexes for performance
CREATE INDEX idx_inventory_product ON inventory(product_id);
CREATE INDEX idx_inventory_location ON inventory(location_id);
CREATE INDEX idx_inventory_expiry ON inventory(expiry_date) WHERE expiry_date IS NOT NULL;
CREATE INDEX idx_po_lines_po ON purchase_order_lines(purchase_order_id);
CREATE INDEX idx_po_lines_product ON purchase_order_lines(product_id);
CREATE INDEX idx_inbound_lines_receipt ON inbound_receipt_lines(inbound_receipt_id);
CREATE INDEX idx_inbound_lines_po_line ON inbound_receipt_lines(purchase_order_line_id);
CREATE INDEX idx_seasonal_dates ON seasonal_config(start_date, end_date);

