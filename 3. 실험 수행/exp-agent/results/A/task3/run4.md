# CLI Output

재고 이동 기능 구현이 완료되었습니다.

## 구현 내용

### 1. Enum
- `TransferStatus`: 이동 승인 상태 (IMMEDIATE, PENDING_APPROVAL, APPROVED, REJECTED)

### 2. Entity
- `StockTransfer`: 재고 이동 이력 테이블 (product, fromLocation, toLocation, inventory, quantity, transferStatus, reason, approvedBy, approvedAt)

### 3. Repository
- `StockTransferRepository`: JPA Repository

### 4. DTO
- `StockTransferRequest`: 이동 요청 (inventoryId, fromLocationCode, toLocationCode, quantity, reason)
- `StockTransferResponse`: 이동 응답 (id, productSku, productName, fromLocationCode, toLocationCode, quantity, transferStatus, reason, approvedBy, approvedAt)

### 5. Service (StockTransferService)
주요 비즈니스 로직:
- **기본 검증**: 동일 로케이션 거부, 재고 부족 체크, 용량 체크, 실사 동결 로케이션 체크
- **보관유형 호환성**: FROZEN→AMBIENT 거부, COLD→AMBIENT 거부, HAZMAT→비HAZMAT zone 거부
- **HAZMAT 혼적 금지**: 도착지 기존 재고와 HAZMAT/비HAZMAT 호환성 체크
- **유통기한 제약**: 만료 상품 이동 불가, 잔여 유통기한 <10% → SHIPPING zone만 허용
- **대량 이동 승인**: 이동 수량 ≥80% → `PENDING_APPROVAL` 상태
- **이동 후 안전재고 체크**: STORAGE zone 재고가 안전재고 이하 → 자동 재발주

### 6. Controller (StockTransferController)
REST API:
- `POST /api/v1/stock-transfers` - 재고 이동 실행
- `POST /api/v1/stock-transfers/{id}/approve` - 대량 이동 승인
- `POST /api/v1/stock-transfers/{id}/reject` - 대량 이동 거부
- `GET /api/v1/stock-transfers/{id}` - 이동 상세 조회
- `GET /api/v1/stock-transfers` - 이동 이력 조회

모든 요구사항이 구현되었으며, 기존 입고/출고 코드와 일관성을 유지했습니다.


# Generated Code


============================================================
// FILE: pom.xml
============================================================
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
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

        <!-- PostgreSQL Driver -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Spring Boot Starter Validation -->
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
// FILE: src\main\java\com\wms\controller\ShipmentController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.PickingResult;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipment-orders")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> createShipmentOrder(
            @RequestBody ShipmentOrderRequest request) {
        ShipmentOrderResponse response = shipmentService.createShipmentOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<PickingResult>> executePicking(@PathVariable UUID id) {
        PickingResult result = shipmentService.executePicking(id);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> confirmShipment(@PathVariable UUID id) {
        ShipmentOrderResponse response = shipmentService.confirmShipment(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(@PathVariable UUID id) {
        ShipmentOrderResponse response = shipmentService.getShipmentOrder(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentOrderResponse>>> getAllShipmentOrders() {
        List<ShipmentOrderResponse> responses = shipmentService.getAllShipmentOrders();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\StockTransferController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.service.StockTransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> executeTransfer(
            @RequestBody StockTransferRequest request) {
        StockTransferResponse response = stockTransferService.executeTransfer(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String approvedBy = body.getOrDefault("approvedBy", "ADMIN");
        StockTransferResponse response = stockTransferService.approveTransfer(id, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String rejectedBy = body.getOrDefault("rejectedBy", "ADMIN");
        StockTransferResponse response = stockTransferService.rejectTransfer(id, rejectedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(@PathVariable UUID id) {
        StockTransferResponse response = stockTransferService.getTransfer(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getAllTransfers() {
        List<StockTransferResponse> response = stockTransferService.getAllTransfers();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ApiResponse.java
============================================================
package com.wms.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private Map<String, String> error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(Map<String, String> error) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(error)
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\PickingResult.java
============================================================
package com.wms.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickingResult {
    private UUID shipmentOrderId;
    private String orderNumber;
    private String status;
    private List<PickedLineResult> pickedLines;
    private List<String> warnings;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PickedLineResult {
        private UUID lineId;
        private String productSku;
        private Integer requestedQty;
        private Integer pickedQty;
        private Integer backorderedQty;
        private String status;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderRequest.java
============================================================
package com.wms.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderRequest {
    private String orderNumber;
    private String customerName;
    private List<ShipmentLineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentLineRequest {
        private String productSku;
        private Integer requestedQty;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderResponse.java
============================================================
package com.wms.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderResponse {
    private UUID id;
    private String orderNumber;
    private String customerName;
    private String status;
    private List<ShipmentLineResponse> lines;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentLineResponse {
        private UUID id;
        private String productSku;
        private String productName;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferRequest.java
============================================================
package com.wms.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferRequest {
    private UUID inventoryId;
    private String fromLocationCode;
    private String toLocationCode;
    private Integer quantity;
    private String reason;
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferResponse.java
============================================================
package com.wms.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferResponse {
    private UUID id;
    private String productSku;
    private String productName;
    private String fromLocationCode;
    private String toLocationCode;
    private Integer quantity;
    private String transferStatus;
    private String reason;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\AuditLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\AutoReorderLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auto_reorder_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoReorderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "trigger_reason", nullable = false, length = 50)
    private String triggerReason;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\Backorder.java
============================================================
package com.wms.entity;

import com.wms.enums.BackorderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "backorders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Backorder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_line_id", nullable = false)
    private ShipmentOrderLine orderLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BackorderStatus status = BackorderStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceipt.java
============================================================
package com.wms.entity;

import com.wms.enums.InboundReceiptStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "receipt_number", unique = true, nullable = false, length = 50)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InboundReceiptStatus status = InboundReceiptStatus.INSPECTING;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @OneToMany(mappedBy = "inboundReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InboundReceiptLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
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
@Table(name = "inbound_receipt_lines")
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
    @JoinColumn(name = "receipt_id", nullable = false)
    private InboundReceipt inboundReceipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_line_id", nullable = false)
    private PurchaseOrderLine purchaseOrderLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

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
@Table(name = "inventory")
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

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "manufacture_date")
    private LocalDate manufactureDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(nullable = false)
    private Boolean expired = false;

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

import com.wms.enums.StorageType;
import com.wms.enums.Zone;
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

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Zone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private StorageType storageType;

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
}


============================================================
// FILE: src\main\java\com\wms\entity\Product.java
============================================================
package com.wms.entity;

import com.wms.enums.ProductCategory;
import com.wms.enums.StorageType;
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

    @Column(unique = true, nullable = false, length = 50)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductCategory category;

    @Column(nullable = false, length = 10)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", nullable = false, length = 20)
    private StorageType storageType;

    @Column(name = "has_expiry", nullable = false)
    private Boolean hasExpiry = false;

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
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrder.java
============================================================
package com.wms.entity;

import com.wms.enums.POStatus;
import com.wms.enums.POType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "po_number", unique = true, nullable = false, length = 50)
    private String poNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "po_type", nullable = false, length = 20)
    private POType poType = POType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private POStatus status = POStatus.PENDING;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
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
@Table(name = "purchase_order_lines")
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
    @JoinColumn(name = "po_id", nullable = false)
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
// FILE: src\main\java\com\wms\entity\SafetyStockRule.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

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
@Table(name = "seasonal_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeasonalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "season_name", nullable = false, length = 50)
    private String seasonName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal multiplier = BigDecimal.valueOf(1.50);

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrder.java
============================================================
package com.wms.entity;

import com.wms.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shipment_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_number", unique = true, nullable = false, length = 50)
    private String orderNumber;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @OneToMany(mappedBy = "shipmentOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShipmentOrderLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrderLine.java
============================================================
package com.wms.entity;

import com.wms.enums.ShipmentLineStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_order_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ShipmentOrder shipmentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "picked_qty", nullable = false)
    private Integer pickedQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentLineStatus status = ShipmentLineStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\StockTransfer.java
============================================================
package com.wms.entity;

import com.wms.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id", nullable = false)
    private Location fromLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id", nullable = false)
    private Location toLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 20)
    private TransferStatus transferStatus;

    @Column(length = 500)
    private String reason;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

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

import com.wms.enums.SupplierStatus;
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

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
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
}


============================================================
// FILE: src\main\java\com\wms\entity\SupplierPenalty.java
============================================================
package com.wms.entity;

import com.wms.enums.PenaltyType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_type", nullable = false, length = 30)
    private PenaltyType penaltyType;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}


============================================================
// FILE: src\main\java\com\wms\enums\BackorderStatus.java
============================================================
package com.wms.enums;

public enum BackorderStatus {
    OPEN,
    FULFILLED,
    CANCELLED
}


============================================================
// FILE: src\main\java\com\wms\enums\InboundReceiptStatus.java
============================================================
package com.wms.enums;

public enum InboundReceiptStatus {
    INSPECTING,
    PENDING_APPROVAL,
    CONFIRMED,
    REJECTED
}


============================================================
// FILE: src\main\java\com\wms\enums\PenaltyType.java
============================================================
package com.wms.enums;

public enum PenaltyType {
    OVER_DELIVERY,
    SHORT_SHELF_LIFE
}


============================================================
// FILE: src\main\java\com\wms\enums\POStatus.java
============================================================
package com.wms.enums;

public enum POStatus {
    PENDING,
    PARTIAL,
    COMPLETED,
    CANCELLED,
    HOLD
}


============================================================
// FILE: src\main\java\com\wms\enums\POType.java
============================================================
package com.wms.enums;

public enum POType {
    NORMAL(1.0),
    URGENT(2.0),
    IMPORT(1.5);

    private final double multiplier;

    POType(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }
}


============================================================
// FILE: src\main\java\com\wms\enums\ProductCategory.java
============================================================
package com.wms.enums;

public enum ProductCategory {
    GENERAL(10.0),
    FRESH(5.0),
    HAZMAT(0.0),
    HIGH_VALUE(3.0);

    private final double overReceivingAllowancePct;

    ProductCategory(double overReceivingAllowancePct) {
        this.overReceivingAllowancePct = overReceivingAllowancePct;
    }

    public double getOverReceivingAllowancePct() {
        return overReceivingAllowancePct;
    }
}


============================================================
// FILE: src\main\java\com\wms\enums\ShipmentLineStatus.java
============================================================
package com.wms.enums;

public enum ShipmentLineStatus {
    PENDING,
    PICKED,
    PARTIAL,
    BACKORDERED
}


============================================================
// FILE: src\main\java\com\wms\enums\ShipmentStatus.java
============================================================
package com.wms.enums;

public enum ShipmentStatus {
    PENDING,
    PICKING,
    PARTIAL,
    SHIPPED,
    CANCELLED
}


============================================================
// FILE: src\main\java\com\wms\enums\StorageType.java
============================================================
package com.wms.enums;

public enum StorageType {
    AMBIENT,
    COLD,
    FROZEN,
    HAZMAT
}


============================================================
// FILE: src\main\java\com\wms\enums\SupplierStatus.java
============================================================
package com.wms.enums;

public enum SupplierStatus {
    ACTIVE,
    HOLD,
    INACTIVE
}


============================================================
// FILE: src\main\java\com\wms\enums\TransferStatus.java
============================================================
package com.wms.enums;

public enum TransferStatus {
    IMMEDIATE,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}


============================================================
// FILE: src\main\java\com\wms\enums\Zone.java
============================================================
package com.wms.enums;

public enum Zone {
    RECEIVING,
    STORAGE,
    SHIPPING,
    HAZMAT
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

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WmsException.class)
    public ResponseEntity<ApiResponse<Object>> handleWmsException(WmsException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .error(error)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "An unexpected error occurred");
        error.put("detail", ex.getMessage());

        ApiResponse<Object> response = ApiResponse.builder()
                .success(false)
                .error(error)
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}


============================================================
// FILE: src\main\java\com\wms\exception\WmsException.java
============================================================
package com.wms.exception;

public class WmsException extends RuntimeException {
    public WmsException(String message) {
        super(message);
    }

    public WmsException(String message, Throwable cause) {
        super(message, cause);
    }
}


============================================================
// FILE: src\main\java\com\wms\repository\AuditLogRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\AutoReorderLogRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.AutoReorderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AutoReorderLogRepository extends JpaRepository<AutoReorderLog, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\BackorderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Backorder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BackorderRepository extends JpaRepository<Backorder, UUID> {
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

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID> {
    Optional<InboundReceipt> findByReceiptNumber(String receiptNumber);
}


============================================================
// FILE: src\main\java\com\wms\repository\InventoryRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
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

import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.PurchaseOrder;
import com.wms.enums.POStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findByPoNumber(String poNumber);
    List<PurchaseOrder> findBySupplierIdAndStatus(UUID supplierId, POStatus status);
}


============================================================
// FILE: src\main\java\com\wms\repository\SafetyStockRuleRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.SafetyStockRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SafetyStockRuleRepository extends JpaRepository<SafetyStockRule, UUID> {
    Optional<SafetyStockRule> findByProductId(UUID productId);
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
    Optional<SeasonalConfig> findByDate(@Param("date") LocalDate date);
}


============================================================
// FILE: src\main\java\com\wms\repository\ShipmentOrderLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.ShipmentOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ShipmentOrderLineRepository extends JpaRepository<ShipmentOrderLine, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\ShipmentOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.ShipmentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentOrderRepository extends JpaRepository<ShipmentOrder, UUID> {
    Optional<ShipmentOrder> findByOrderNumber(String orderNumber);
}


============================================================
// FILE: src\main\java\com\wms\repository\StockTransferRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {
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

    @Query("SELECT COUNT(sp) FROM SupplierPenalty sp WHERE sp.supplier.id = :supplierId AND sp.occurredAt >= :since")
    long countBySupplierId(@Param("supplierId") UUID supplierId, @Param("since") OffsetDateTime since);
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
// FILE: src\main\java\com\wms\service\ShipmentService.java
============================================================
package com.wms.service;

import com.wms.dto.PickingResult;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.enums.*;
import com.wms.exception.WmsException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentOrderRepository shipmentOrderRepository;
    private final ShipmentOrderLineRepository shipmentOrderLineRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final BackorderRepository backorderRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // Validate order number uniqueness
        if (shipmentOrderRepository.findByOrderNumber(request.getOrderNumber()).isPresent()) {
            throw new WmsException("Order number already exists: " + request.getOrderNumber());
        }

        // Check for HAZMAT + FRESH separation requirement
        List<Product> products = new ArrayList<>();
        for (ShipmentOrderRequest.ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findBySku(lineReq.getProductSku())
                    .orElseThrow(() -> new WmsException("Product not found: " + lineReq.getProductSku()));
            products.add(product);
        }

        boolean hasHazmat = products.stream().anyMatch(p -> p.getCategory() == ProductCategory.HAZMAT);
        boolean hasFresh = products.stream().anyMatch(p -> p.getCategory() == ProductCategory.FRESH);

        // If both HAZMAT and FRESH exist, split into separate shipments
        if (hasHazmat && hasFresh) {
            return createSeparateShipments(request, products);
        }

        // Create single shipment order
        return createSingleShipmentOrder(request, products);
    }

    private ShipmentOrderResponse createSingleShipmentOrder(ShipmentOrderRequest request, List<Product> products) {
        ShipmentOrder order = ShipmentOrder.builder()
                .orderNumber(request.getOrderNumber())
                .customerName(request.getCustomerName())
                .status(ShipmentStatus.PENDING)
                .build();

        List<ShipmentOrderLine> lines = new ArrayList<>();
        for (int i = 0; i < request.getLines().size(); i++) {
            ShipmentOrderRequest.ShipmentLineRequest lineReq = request.getLines().get(i);
            Product product = products.get(i);

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(order)
                    .product(product)
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentLineStatus.PENDING)
                    .build();
            lines.add(line);
        }

        order.setLines(lines);
        ShipmentOrder savedOrder = shipmentOrderRepository.save(order);

        return toResponse(savedOrder);
    }

    private ShipmentOrderResponse createSeparateShipments(ShipmentOrderRequest request, List<Product> products) {
        // Create HAZMAT shipment
        List<ShipmentOrderRequest.ShipmentLineRequest> hazmatLines = new ArrayList<>();
        List<Product> hazmatProducts = new ArrayList<>();

        for (int i = 0; i < request.getLines().size(); i++) {
            Product product = products.get(i);
            if (product.getCategory() == ProductCategory.HAZMAT) {
                hazmatLines.add(request.getLines().get(i));
                hazmatProducts.add(product);
            }
        }

        ShipmentOrderRequest hazmatRequest = ShipmentOrderRequest.builder()
                .orderNumber(request.getOrderNumber() + "-HAZMAT")
                .customerName(request.getCustomerName())
                .lines(hazmatLines)
                .build();

        createSingleShipmentOrder(hazmatRequest, hazmatProducts);

        // Create non-HAZMAT shipment (original order)
        List<ShipmentOrderRequest.ShipmentLineRequest> nonHazmatLines = new ArrayList<>();
        List<Product> nonHazmatProducts = new ArrayList<>();

        for (int i = 0; i < request.getLines().size(); i++) {
            Product product = products.get(i);
            if (product.getCategory() != ProductCategory.HAZMAT) {
                nonHazmatLines.add(request.getLines().get(i));
                nonHazmatProducts.add(product);
            }
        }

        ShipmentOrderRequest nonHazmatRequest = ShipmentOrderRequest.builder()
                .orderNumber(request.getOrderNumber())
                .customerName(request.getCustomerName())
                .lines(nonHazmatLines)
                .build();

        return createSingleShipmentOrder(nonHazmatRequest, nonHazmatProducts);
    }

    @Transactional
    public PickingResult executePicking(UUID orderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(orderId)
                .orElseThrow(() -> new WmsException("Shipment order not found"));

        if (order.getStatus() != ShipmentStatus.PENDING) {
            throw new WmsException("Shipment order is not in PENDING status");
        }

        order.setStatus(ShipmentStatus.PICKING);

        List<PickingResult.PickedLineResult> results = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (ShipmentOrderLine line : order.getLines()) {
            PickingResult.PickedLineResult lineResult = pickLine(line, warnings);
            results.add(lineResult);
        }

        // Update order status based on line statuses
        updateOrderStatusAfterPicking(order);

        shipmentOrderRepository.save(order);

        return PickingResult.builder()
                .shipmentOrderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus().name())
                .pickedLines(results)
                .warnings(warnings)
                .build();
    }

    private PickingResult.PickedLineResult pickLine(ShipmentOrderLine line, List<String> warnings) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQty();

        // Find eligible inventory
        List<Inventory> eligibleInventory = findEligibleInventory(product, warnings);

        int totalPicked = 0;
        int remainingQty = requestedQty;

        // Apply max_pick_qty constraint for HAZMAT
        if (product.getCategory() == ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
            if (requestedQty > product.getMaxPickQty()) {
                warnings.add(String.format("HAZMAT product %s requested qty %d exceeds max pick qty %d",
                        product.getSku(), requestedQty, product.getMaxPickQty()));
                remainingQty = product.getMaxPickQty();
            }
        }

        // Pick from inventory (FIFO/FEFO)
        for (Inventory inv : eligibleInventory) {
            if (remainingQty <= 0) break;

            // Check storage type mismatch warning
            if (!inv.getLocation().getStorageType().name().equals(product.getStorageType().name())) {
                warnings.add(String.format("Storage type mismatch: product %s (%s) found in location %s (%s)",
                        product.getSku(), product.getStorageType(), inv.getLocation().getCode(),
                        inv.getLocation().getStorageType()));
                logAudit("INVENTORY", inv.getId(), "STORAGE_TYPE_MISMATCH",
                        "SYSTEM", Map.of("product", product.getSku(), "location", inv.getLocation().getCode()));
            }

            int pickQty = Math.min(inv.getQuantity(), remainingQty);
            inv.setQuantity(inv.getQuantity() - pickQty);

            Location location = inv.getLocation();
            location.setCurrentQty(location.getCurrentQty() - pickQty);
            locationRepository.save(location);

            totalPicked += pickQty;
            remainingQty -= pickQty;
        }

        line.setPickedQty(totalPicked);

        // Determine line status and create backorder if needed
        int backorderedQty = requestedQty - totalPicked;
        ShipmentLineStatus lineStatus;

        if (totalPicked == 0) {
            lineStatus = ShipmentLineStatus.BACKORDERED;
            createBackorder(line, backorderedQty);
        } else if (totalPicked < requestedQty) {
            // Apply partial shipment decision tree
            double fulfillmentRate = (double) totalPicked / requestedQty;

            if (fulfillmentRate >= 0.7) {
                // >= 70%: partial shipment + backorder
                lineStatus = ShipmentLineStatus.PARTIAL;
                createBackorder(line, backorderedQty);
            } else if (fulfillmentRate >= 0.3) {
                // 30-70%: partial shipment + backorder + emergency reorder
                lineStatus = ShipmentLineStatus.PARTIAL;
                createBackorder(line, backorderedQty);
                triggerEmergencyReorder(product, backorderedQty);
            } else {
                // < 30%: full backorder, no partial shipment
                lineStatus = ShipmentLineStatus.BACKORDERED;
                // Revert picked quantity
                line.setPickedQty(0);
                // Restore inventory
                restoreInventory(eligibleInventory, totalPicked);
                createBackorder(line, requestedQty);
                backorderedQty = requestedQty;
                totalPicked = 0;
            }
        } else {
            lineStatus = ShipmentLineStatus.PICKED;
            backorderedQty = 0;
        }

        line.setStatus(lineStatus);
        shipmentOrderLineRepository.save(line);

        return PickingResult.PickedLineResult.builder()
                .lineId(line.getId())
                .productSku(product.getSku())
                .requestedQty(requestedQty)
                .pickedQty(totalPicked)
                .backorderedQty(backorderedQty)
                .status(lineStatus.name())
                .build();
    }

    private List<Inventory> findEligibleInventory(Product product, List<String> warnings) {
        List<Inventory> allInventory = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()))
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> !inv.getExpired())
                .collect(Collectors.toList());

        // Filter by zone for HAZMAT
        if (product.getCategory() == ProductCategory.HAZMAT) {
            allInventory = allInventory.stream()
                    .filter(inv -> inv.getLocation().getZone() == Zone.HAZMAT)
                    .collect(Collectors.toList());
        }

        // Filter frozen locations
        allInventory = allInventory.stream()
                .filter(inv -> !inv.getLocation().getIsFrozen())
                .collect(Collectors.toList());

        // Exclude expired inventory
        LocalDate today = LocalDate.now();
        allInventory = allInventory.stream()
                .filter(inv -> {
                    if (inv.getExpiryDate() != null && inv.getExpiryDate().isBefore(today)) {
                        // Mark as expired
                        inv.setExpired(true);
                        inventoryRepository.save(inv);
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // Calculate remaining shelf life percentage and exclude < 10%
        allInventory = allInventory.stream()
                .filter(inv -> {
                    if (product.getHasExpiry() && inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                        double remainingPct = calculateRemainingShelfLifePct(
                                inv.getManufactureDate(), inv.getExpiryDate(), today);
                        if (remainingPct < 10.0) {
                            // Mark as expired (disposal target)
                            inv.setExpired(true);
                            inventoryRepository.save(inv);
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // Sort by FEFO (expiry first) then FIFO (received date)
        // Priority: remaining shelf life < 30% first
        allInventory.sort((a, b) -> {
            boolean aHasExpiry = a.getExpiryDate() != null && a.getManufactureDate() != null;
            boolean bHasExpiry = b.getExpiryDate() != null && b.getManufactureDate() != null;

            if (aHasExpiry && bHasExpiry) {
                double aPct = calculateRemainingShelfLifePct(a.getManufactureDate(), a.getExpiryDate(), today);
                double bPct = calculateRemainingShelfLifePct(b.getManufactureDate(), b.getExpiryDate(), today);

                boolean aPriority = aPct < 30.0;
                boolean bPriority = bPct < 30.0;

                if (aPriority && !bPriority) return -1;
                if (!aPriority && bPriority) return 1;

                // Both in same priority group, sort by expiry date (FEFO)
                int expiryCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                if (expiryCompare != 0) return expiryCompare;
            } else if (aHasExpiry) {
                return -1; // Expiry items first
            } else if (bHasExpiry) {
                return 1;
            }

            // FIFO fallback
            return a.getReceivedAt().compareTo(b.getReceivedAt());
        });

        return allInventory;
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays <= 0) return 0.0;
        return (remainingDays * 100.0) / totalDays;
    }

    private void restoreInventory(List<Inventory> inventoryList, int totalPicked) {
        int remaining = totalPicked;
        for (Inventory inv : inventoryList) {
            if (remaining <= 0) break;

            int restored = Math.min(remaining, totalPicked);
            inv.setQuantity(inv.getQuantity() + restored);

            Location location = inv.getLocation();
            location.setCurrentQty(location.getCurrentQty() + restored);
            locationRepository.save(location);

            remaining -= restored;
        }
    }

    private void createBackorder(ShipmentOrderLine line, int quantity) {
        Backorder backorder = Backorder.builder()
                .orderLine(line)
                .product(line.getProduct())
                .quantity(quantity)
                .status(BackorderStatus.OPEN)
                .build();
        backorderRepository.save(backorder);
    }

    private void triggerEmergencyReorder(Product product, int shortageQty) {
        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerReason("EMERGENCY_REORDER")
                .requestedQty(shortageQty)
                .build();
        autoReorderLogRepository.save(log);
    }

    private void updateOrderStatusAfterPicking(ShipmentOrder order) {
        long totalLines = order.getLines().size();
        long pickedLines = order.getLines().stream()
                .filter(l -> l.getStatus() == ShipmentLineStatus.PICKED)
                .count();
        long backorderedLines = order.getLines().stream()
                .filter(l -> l.getStatus() == ShipmentLineStatus.BACKORDERED)
                .count();

        if (backorderedLines == totalLines) {
            order.setStatus(ShipmentStatus.PENDING);
        } else if (pickedLines == totalLines) {
            order.setStatus(ShipmentStatus.PICKING);
        } else {
            order.setStatus(ShipmentStatus.PARTIAL);
        }
    }

    @Transactional
    public ShipmentOrderResponse confirmShipment(UUID orderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(orderId)
                .orElseThrow(() -> new WmsException("Shipment order not found"));

        if (order.getStatus() != ShipmentStatus.PICKING && order.getStatus() != ShipmentStatus.PARTIAL) {
            throw new WmsException("Cannot confirm shipment in current status: " + order.getStatus());
        }

        order.setStatus(ShipmentStatus.SHIPPED);
        shipmentOrderRepository.save(order);

        // Check safety stock and trigger auto reorder
        Set<UUID> checkedProducts = new HashSet<>();
        for (ShipmentOrderLine line : order.getLines()) {
            UUID productId = line.getProduct().getId();
            if (!checkedProducts.contains(productId)) {
                checkSafetyStock(line.getProduct());
                checkedProducts.add(productId);
            }
        }

        return toResponse(order);
    }

    private void checkSafetyStock(Product product) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductId(product.getId());
        if (ruleOpt.isEmpty()) return;

        SafetyStockRule rule = ruleOpt.get();

        // Calculate total available inventory (excluding expired)
        int totalAvailable = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()))
                .filter(inv -> !inv.getExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason("SAFETY_STOCK_TRIGGER")
                    .requestedQty(rule.getReorderQty())
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID orderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(orderId)
                .orElseThrow(() -> new WmsException("Shipment order not found"));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private ShipmentOrderResponse toResponse(ShipmentOrder order) {
        List<ShipmentOrderResponse.ShipmentLineResponse> lineResponses = order.getLines().stream()
                .map(line -> ShipmentOrderResponse.ShipmentLineResponse.builder()
                        .id(line.getId())
                        .productSku(line.getProduct().getSku())
                        .productName(line.getProduct().getName())
                        .requestedQty(line.getRequestedQty())
                        .pickedQty(line.getPickedQty())
                        .status(line.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        return ShipmentOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .customerName(order.getCustomerName())
                .status(order.getStatus().name())
                .lines(lineResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private void logAudit(String entityType, UUID entityId, String action, String performedBy, Map<String, Object> details) {
        AuditLog log = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedBy(performedBy)
                .details(details)
                .build();
        auditLogRepository.save(log);
    }
}


============================================================
// FILE: src\main\java\com\wms\service\StockTransferService.java
============================================================
package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.enums.*;
import com.wms.exception.WmsException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final InventoryRepository inventoryRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    @Transactional
    public StockTransferResponse executeTransfer(StockTransferRequest request) {
        // Validate inventory
        Inventory inventory = inventoryRepository.findById(request.getInventoryId())
                .orElseThrow(() -> new WmsException("Inventory not found"));

        // Validate locations
        Location fromLocation = locationRepository.findByCode(request.getFromLocationCode())
                .orElseThrow(() -> new WmsException("From location not found: " + request.getFromLocationCode()));
        Location toLocation = locationRepository.findByCode(request.getToLocationCode())
                .orElseThrow(() -> new WmsException("To location not found: " + request.getToLocationCode()));

        // Validate inventory belongs to from location
        if (!inventory.getLocation().getId().equals(fromLocation.getId())) {
            throw new WmsException("Inventory does not belong to from location");
        }

        // Basic validations
        validateTransfer(inventory, fromLocation, toLocation, request.getQuantity());

        // Determine transfer status
        TransferStatus transferStatus = determineTransferStatus(inventory, request.getQuantity());

        // Create transfer record
        StockTransfer transfer = StockTransfer.builder()
                .product(inventory.getProduct())
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .inventory(inventory)
                .quantity(request.getQuantity())
                .transferStatus(transferStatus)
                .reason(request.getReason())
                .build();

        stockTransferRepository.save(transfer);

        // If immediate, execute transfer
        if (transferStatus == TransferStatus.IMMEDIATE) {
            executeImmediateTransfer(transfer);
        }

        return toResponse(transfer);
    }

    private void validateTransfer(Inventory inventory, Location fromLocation, Location toLocation, Integer quantity) {
        Product product = inventory.getProduct();

        // Same location check
        if (fromLocation.getId().equals(toLocation.getId())) {
            throw new WmsException("Cannot transfer to the same location");
        }

        // Quantity check
        if (quantity <= 0) {
            throw new WmsException("Transfer quantity must be greater than 0");
        }

        if (quantity > inventory.getQuantity()) {
            throw new WmsException("Insufficient inventory quantity");
        }

        // Capacity check
        if (toLocation.getCurrentQty() + quantity > toLocation.getCapacity()) {
            throw new WmsException("Destination location capacity exceeded");
        }

        // Frozen location check
        if (fromLocation.getIsFrozen()) {
            throw new WmsException("Cannot transfer from frozen location");
        }

        if (toLocation.getIsFrozen()) {
            throw new WmsException("Cannot transfer to frozen location");
        }

        // Storage type compatibility check
        validateStorageTypeCompatibility(product, toLocation);

        // HAZMAT mixing check
        validateHazmatMixing(product, toLocation);

        // Expiry date check for products with expiry
        if (product.getHasExpiry() && inventory.getExpiryDate() != null) {
            validateExpiryDateRestrictions(inventory, toLocation);
        }
    }

    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        StorageType productType = product.getStorageType();
        StorageType locationType = toLocation.getStorageType();

        // FROZEN product -> only FROZEN location
        if (productType == StorageType.FROZEN && locationType != StorageType.FROZEN) {
            throw new WmsException("FROZEN product cannot be moved to " + locationType + " location");
        }

        // COLD product -> COLD or FROZEN location
        if (productType == StorageType.COLD &&
            locationType != StorageType.COLD && locationType != StorageType.FROZEN) {
            throw new WmsException("COLD product cannot be moved to AMBIENT location");
        }

        // HAZMAT product -> HAZMAT zone only
        if (product.getCategory() == ProductCategory.HAZMAT && toLocation.getZone() != Zone.HAZMAT) {
            throw new WmsException("HAZMAT product must be in HAZMAT zone");
        }
    }

    private void validateHazmatMixing(Product product, Location toLocation) {
        // Check existing inventory at destination
        List<Inventory> existingInventory = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getLocation().getId().equals(toLocation.getId()))
                .filter(inv -> inv.getQuantity() > 0)
                .collect(Collectors.toList());

        boolean isHazmat = product.getCategory() == ProductCategory.HAZMAT;

        for (Inventory existing : existingInventory) {
            boolean existingIsHazmat = existing.getProduct().getCategory() == ProductCategory.HAZMAT;

            // HAZMAT and non-HAZMAT cannot be mixed
            if (isHazmat != existingIsHazmat) {
                throw new WmsException("Cannot mix HAZMAT and non-HAZMAT products in the same location");
            }
        }
    }

    private void validateExpiryDateRestrictions(Inventory inventory, Location toLocation) {
        LocalDate today = LocalDate.now();
        LocalDate expiryDate = inventory.getExpiryDate();
        LocalDate manufactureDate = inventory.getManufactureDate();

        // Expired products cannot be moved
        if (expiryDate.isBefore(today)) {
            throw new WmsException("Cannot transfer expired product");
        }

        // Calculate remaining shelf life percentage
        if (manufactureDate != null) {
            double remainingPct = calculateRemainingShelfLifePct(manufactureDate, expiryDate, today);

            // < 10% can only go to SHIPPING zone
            if (remainingPct < 10.0 && toLocation.getZone() != Zone.SHIPPING) {
                throw new WmsException("Products with <10% remaining shelf life can only be moved to SHIPPING zone");
            }
        }
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays <= 0) return 0.0;
        return (remainingDays * 100.0) / totalDays;
    }

    private TransferStatus determineTransferStatus(Inventory inventory, Integer quantity) {
        // If transfer quantity >= 80% of inventory, require approval
        double transferRatio = (double) quantity / inventory.getQuantity();
        if (transferRatio >= 0.8) {
            return TransferStatus.PENDING_APPROVAL;
        }
        return TransferStatus.IMMEDIATE;
    }

    private void executeImmediateTransfer(StockTransfer transfer) {
        Inventory inventory = transfer.getInventory();
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();
        Integer quantity = transfer.getQuantity();

        // Deduct from source
        inventory.setQuantity(inventory.getQuantity() - quantity);
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - quantity);

        // Find or create inventory at destination
        Optional<Inventory> destInventoryOpt = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getLocation().getId().equals(toLocation.getId()))
                .filter(inv -> inv.getProduct().getId().equals(inventory.getProduct().getId()))
                .filter(inv -> {
                    // Match by lot number if exists
                    if (inventory.getLotNumber() != null) {
                        return inventory.getLotNumber().equals(inv.getLotNumber());
                    }
                    return true;
                })
                .filter(inv -> {
                    // Match by expiry date if exists
                    if (inventory.getExpiryDate() != null) {
                        return inventory.getExpiryDate().equals(inv.getExpiryDate());
                    }
                    return inv.getExpiryDate() == null;
                })
                .findFirst();

        if (destInventoryOpt.isPresent()) {
            // Add to existing inventory
            Inventory destInventory = destInventoryOpt.get();
            destInventory.setQuantity(destInventory.getQuantity() + quantity);
        } else {
            // Create new inventory at destination
            Inventory newInventory = Inventory.builder()
                    .product(inventory.getProduct())
                    .location(toLocation)
                    .lotNumber(inventory.getLotNumber())
                    .quantity(quantity)
                    .manufactureDate(inventory.getManufactureDate())
                    .expiryDate(inventory.getExpiryDate())
                    .receivedAt(inventory.getReceivedAt())
                    .expired(false)
                    .build();
            inventoryRepository.save(newInventory);
        }

        // Update destination location quantity
        toLocation.setCurrentQty(toLocation.getCurrentQty() + quantity);

        locationRepository.save(fromLocation);
        locationRepository.save(toLocation);
        inventoryRepository.save(inventory);

        // Check safety stock after transfer (for STORAGE zone)
        if (fromLocation.getZone() == Zone.STORAGE) {
            checkSafetyStock(inventory.getProduct());
        }
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new WmsException("Transfer not found"));

        if (transfer.getTransferStatus() != TransferStatus.PENDING_APPROVAL) {
            throw new WmsException("Transfer is not pending approval");
        }

        transfer.setTransferStatus(TransferStatus.APPROVED);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(OffsetDateTime.now());

        stockTransferRepository.save(transfer);

        // Execute the transfer
        executeImmediateTransfer(transfer);

        return toResponse(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String rejectedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new WmsException("Transfer not found"));

        if (transfer.getTransferStatus() != TransferStatus.PENDING_APPROVAL) {
            throw new WmsException("Transfer is not pending approval");
        }

        transfer.setTransferStatus(TransferStatus.REJECTED);
        transfer.setApprovedBy(rejectedBy);
        transfer.setApprovedAt(OffsetDateTime.now());

        stockTransferRepository.save(transfer);

        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new WmsException("Transfer not found"));
        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferResponse> getAllTransfers() {
        return stockTransferRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private void checkSafetyStock(Product product) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductId(product.getId());
        if (ruleOpt.isEmpty()) return;

        SafetyStockRule rule = ruleOpt.get();

        // Calculate total available inventory in STORAGE zone
        int totalStorageQty = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getId().equals(product.getId()))
                .filter(inv -> inv.getLocation().getZone() == Zone.STORAGE)
                .filter(inv -> !inv.getExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        if (totalStorageQty <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason("SAFETY_STOCK_TRIGGER")
                    .requestedQty(rule.getReorderQty())
                    .build();
            autoReorderLogRepository.save(log);
        }
    }

    private StockTransferResponse toResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
                .id(transfer.getId())
                .productSku(transfer.getProduct().getSku())
                .productName(transfer.getProduct().getName())
                .fromLocationCode(transfer.getFromLocation().getCode())
                .toLocationCode(transfer.getToLocation().getCode())
                .quantity(transfer.getQuantity())
                .transferStatus(transfer.getTransferStatus().name())
                .reason(transfer.getReason())
                .approvedBy(transfer.getApprovedBy())
                .approvedAt(transfer.getApprovedAt())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
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
    name: WMS

  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db
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
    database-platform: org.hibernate.dialect.PostgreSQLDialect

  sql:
    init:
      mode: never

server:
  port: 8080
  servlet:
    context-path: /api/v1

logging:
  level:
    com.wms: DEBUG
    org.hibernate.SQL: DEBUG


============================================================
// FILE: src\main\resources\schema.sql
============================================================
-- WMS Database Schema

-- Products table
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(20) NOT NULL CHECK (category IN ('GENERAL', 'FRESH', 'HAZMAT', 'HIGH_VALUE')),
    unit VARCHAR(10) NOT NULL,
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    has_expiry BOOLEAN NOT NULL DEFAULT FALSE,
    min_remaining_shelf_life_pct INTEGER DEFAULT 30,
    max_pick_qty INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Locations table
CREATE TABLE IF NOT EXISTS locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    zone VARCHAR(20) NOT NULL CHECK (zone IN ('RECEIVING', 'STORAGE', 'SHIPPING', 'HAZMAT')),
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    current_qty INTEGER NOT NULL DEFAULT 0 CHECK (current_qty >= 0),
    is_frozen BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Inventory table
CREATE TABLE IF NOT EXISTS inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    lot_number VARCHAR(50),
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    manufacture_date DATE,
    expiry_date DATE,
    received_at TIMESTAMPTZ NOT NULL,
    expired BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Suppliers table
CREATE TABLE IF NOT EXISTS suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    contact VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'hold', 'inactive')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Supplier penalties table
CREATE TABLE IF NOT EXISTS supplier_penalties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    penalty_type VARCHAR(30) NOT NULL CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Purchase orders table
CREATE TABLE IF NOT EXISTS purchase_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_number VARCHAR(50) UNIQUE NOT NULL,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    po_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (po_type IN ('NORMAL', 'URGENT', 'IMPORT')),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'partial', 'completed', 'cancelled', 'hold')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Purchase order lines table
CREATE TABLE IF NOT EXISTS purchase_order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_id UUID NOT NULL REFERENCES purchase_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    ordered_qty INTEGER NOT NULL CHECK (ordered_qty > 0),
    received_qty INTEGER NOT NULL DEFAULT 0 CHECK (received_qty >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seasonal config table
CREATE TABLE IF NOT EXISTS seasonal_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_name VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.50,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (end_date >= start_date)
);

-- Inbound receipts table
CREATE TABLE IF NOT EXISTS inbound_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_number VARCHAR(50) UNIQUE NOT NULL,
    po_id UUID NOT NULL REFERENCES purchase_orders(id),
    status VARCHAR(30) NOT NULL DEFAULT 'inspecting' CHECK (status IN ('inspecting', 'pending_approval', 'confirmed', 'rejected')),
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Inbound receipt lines table
CREATE TABLE IF NOT EXISTS inbound_receipt_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id UUID NOT NULL REFERENCES inbound_receipts(id),
    po_line_id UUID NOT NULL REFERENCES purchase_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    received_qty INTEGER NOT NULL CHECK (received_qty > 0),
    lot_number VARCHAR(50),
    manufacture_date DATE,
    expiry_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Shipment orders table
CREATE TABLE IF NOT EXISTS shipment_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(50) UNIQUE NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'picking', 'partial', 'shipped', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Shipment order lines table
CREATE TABLE IF NOT EXISTS shipment_order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES shipment_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    requested_qty INTEGER NOT NULL CHECK (requested_qty > 0),
    picked_qty INTEGER NOT NULL DEFAULT 0 CHECK (picked_qty >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'picked', 'partial', 'backordered')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Backorders table
CREATE TABLE IF NOT EXISTS backorders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_line_id UUID NOT NULL REFERENCES shipment_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'fulfilled', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Stock transfers table
CREATE TABLE IF NOT EXISTS stock_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id UUID NOT NULL REFERENCES inventory(id),
    from_location_id UUID NOT NULL REFERENCES locations(id),
    to_location_id UUID NOT NULL REFERENCES locations(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    transfer_status VARCHAR(30) NOT NULL DEFAULT 'immediate' CHECK (transfer_status IN ('immediate', 'pending_approval', 'approved', 'rejected')),
    transferred_at TIMESTAMPTZ,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Inventory adjustments table
CREATE TABLE IF NOT EXISTS inventory_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id UUID NOT NULL REFERENCES inventory(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    product_id UUID NOT NULL REFERENCES products(id),
    system_qty INTEGER NOT NULL,
    actual_qty INTEGER NOT NULL,
    difference INTEGER NOT NULL,
    reason TEXT NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (approval_status IN ('auto_approved', 'pending', 'approved', 'rejected')),
    approved_at TIMESTAMPTZ,
    approved_by VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Audit logs table
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Safety stock rules table
CREATE TABLE IF NOT EXISTS safety_stock_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id) UNIQUE,
    min_qty INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Auto reorder logs table
CREATE TABLE IF NOT EXISTS auto_reorder_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    trigger_reason VARCHAR(50) NOT NULL,
    requested_qty INTEGER NOT NULL CHECK (requested_qty > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Cycle counts table
CREATE TABLE IF NOT EXISTS cycle_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES locations(id),
    status VARCHAR(20) NOT NULL DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'completed')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_inventory_location_id ON inventory(location_id);
CREATE INDEX idx_inventory_expiry_date ON inventory(expiry_date);
CREATE INDEX idx_po_lines_po_id ON purchase_order_lines(po_id);
CREATE INDEX idx_po_lines_product_id ON purchase_order_lines(product_id);
CREATE INDEX idx_receipt_lines_receipt_id ON inbound_receipt_lines(receipt_id);
CREATE INDEX idx_receipt_lines_po_line_id ON inbound_receipt_lines(po_line_id);
CREATE INDEX idx_supplier_penalties_supplier_id ON supplier_penalties(supplier_id);
CREATE INDEX idx_supplier_penalties_occurred_at ON supplier_penalties(occurred_at);
CREATE INDEX idx_seasonal_config_dates ON seasonal_config(start_date, end_date);

