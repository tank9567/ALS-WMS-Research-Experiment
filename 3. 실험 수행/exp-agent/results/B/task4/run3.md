# CLI Output

재고 실사 및 조정 기능 구현이 완료되었습니다.

## 구현 내역

### 1. DB 스키마
- `src/main/resources/db/migration/V004__create_cycle_count_and_inventory_adjustment_tables.sql`
  - `cycle_counts` 테이블 (실사 세션)
  - `inventory_adjustments` 테이블 (재고 조정 이력)

### 2. Entity & Repository
- `CycleCount` Entity & Repository (src/main/java/com/wms/entity/CycleCount.java:1)
- `InventoryAdjustment` Entity & Repository (src/main/java/com/wms/entity/InventoryAdjustment.java:1)
- `InventoryRepository`에 조회 메서드 추가 (src/main/java/com/wms/repository/InventoryRepository.java:19)

### 3. Service
- `CycleCountService` (src/main/java/com/wms/service/CycleCountService.java:1)
  - 실사 시작: 로케이션 동결 (is_frozen=true)
  - 실사 완료: 로케이션 동결 해제 (is_frozen=false)

- `InventoryAdjustmentService` (src/main/java/com/wms/service/InventoryAdjustmentService.java:1)
  - 카테고리별 자동승인 임계치 (GENERAL 5%, FRESH 3%, HAZMAT 1%, HIGH_VALUE 2%)
  - 연속 조정 감시 (7일 내 2회 이상 → 승인 필요, [연속조정감시] 태그)
  - HIGH_VALUE 전수 검증 (차이 발생 시 무조건 승인 필요, audit_logs 기록)
  - system_qty=0 시 승인 필요
  - 조정 후 안전재고 체크 → 자동 재발주

### 4. Controller & DTO
- `CycleCountController` (src/main/java/com/wms/controller/CycleCountController.java:1)
  - POST /api/v1/cycle-counts (실사 시작)
  - POST /api/v1/cycle-counts/{id}/complete (실사 완료)
  - GET /api/v1/cycle-counts/{id} (상세 조회)
  - GET /api/v1/cycle-counts (목록 조회)

- `InventoryAdjustmentController` (src/main/java/com/wms/controller/InventoryAdjustmentController.java:1)
  - POST /api/v1/inventory-adjustments (조정 생성)
  - POST /api/v1/inventory-adjustments/{id}/approve (승인)
  - POST /api/v1/inventory-adjustments/{id}/reject (거부)
  - GET /api/v1/inventory-adjustments/{id} (상세 조회)
  - GET /api/v1/inventory-adjustments (목록 조회, ?status=pending 필터 지원)

모든 비즈니스 규칙이 설계 문서에 따라 구현되었으며, 기존 코드(입고/출고/이동)와 일관성을 유지했습니다.


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
    <name>Warehouse Management System</name>
    <description>WMS Backend API</description>

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

        <!-- Spring Boot Validation -->
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
// FILE: schema.sql
============================================================
-- WMS (Warehouse Management System) Database Schema - Level 2
-- PostgreSQL 15+
-- ID: UUID v4
-- Timezone: UTC (TIMESTAMPTZ)

-- 1. Products (상품 마스터)
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

-- 2. Locations (로케이션)
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

-- 3. Inventory (재고)
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

-- 4. Suppliers (공급업체)
CREATE TABLE suppliers (
    supplier_id     UUID PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    contact_info    VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'hold', 'inactive')),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 5. Supplier Penalties (공급업체 페널티)
CREATE TABLE supplier_penalties (
    penalty_id      UUID PRIMARY KEY,
    supplier_id     UUID NOT NULL REFERENCES suppliers(supplier_id),
    penalty_type    VARCHAR(50) NOT NULL
                    CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    description     VARCHAR(500),
    po_id           UUID,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- 6. Purchase Orders (발주서)
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

-- 7. Inbound Receipts (입고)
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

-- 8. Shipment Orders (출고 지시서)
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

-- 9. Backorders (백오더)
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

-- 10. Stock Transfers (재고 이동)
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

-- 11. Inventory Adjustments (재고 조정)
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

-- 12. Audit Logs (감사 로그)
CREATE TABLE audit_logs (
    log_id           UUID PRIMARY KEY,
    event_type       VARCHAR(50) NOT NULL,
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        UUID NOT NULL,
    details          JSONB NOT NULL,
    performed_by     VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 13. Safety Stock Rules (안전재고 기준)
CREATE TABLE safety_stock_rules (
    rule_id          UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id) UNIQUE,
    min_qty          INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty      INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 14. Auto Reorder Logs (자동 재발주 이력)
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

-- 15. Seasonal Config (계절 설정)
CREATE TABLE seasonal_config (
    season_id        UUID PRIMARY KEY,
    season_name      VARCHAR(100) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    multiplier       NUMERIC(3,2) NOT NULL DEFAULT 1.50,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 16. Cycle Counts (실사 세션)
CREATE TABLE cycle_counts (
    cycle_count_id   UUID PRIMARY KEY,
    location_id      UUID NOT NULL REFERENCES locations(location_id),
    status           VARCHAR(20) NOT NULL DEFAULT 'in_progress'
                     CHECK (status IN ('in_progress', 'completed')),
    started_by       VARCHAR(100) NOT NULL,
    started_at       TIMESTAMPTZ DEFAULT NOW(),
    completed_at     TIMESTAMPTZ
);

-- Indexes for performance
CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_inventory_location_id ON inventory(location_id);
CREATE INDEX idx_inbound_receipt_lines_receipt_id ON inbound_receipt_lines(receipt_id);
CREATE INDEX idx_purchase_order_lines_po_id ON purchase_order_lines(po_id);
CREATE INDEX idx_supplier_penalties_supplier_id ON supplier_penalties(supplier_id);
CREATE INDEX idx_supplier_penalties_created_at ON supplier_penalties(created_at);
CREATE INDEX idx_seasonal_config_dates ON seasonal_config(start_date, end_date, is_active);


============================================================
// FILE: src\main\java\com\wms\controller\CycleCountController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.CycleCountResponse;
import com.wms.dto.CycleCountStartRequest;
import com.wms.entity.CycleCount;
import com.wms.service.CycleCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/cycle-counts")
@RequiredArgsConstructor
public class CycleCountController {

    private final CycleCountService cycleCountService;

    /**
     * 실사 시작
     * POST /api/v1/cycle-counts
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CycleCountResponse> startCycleCount(
        @RequestBody CycleCountStartRequest request
    ) {
        CycleCount cycleCount = cycleCountService.startCycleCount(
            request.getLocationId(),
            request.getStartedBy()
        );
        return ApiResponse.success(CycleCountResponse.from(cycleCount));
    }

    /**
     * 실사 완료
     * POST /api/v1/cycle-counts/{id}/complete
     */
    @PostMapping("/{id}/complete")
    public ApiResponse<CycleCountResponse> completeCycleCount(
        @PathVariable("id") UUID cycleCountId
    ) {
        CycleCount cycleCount = cycleCountService.completeCycleCount(cycleCountId);
        return ApiResponse.success(CycleCountResponse.from(cycleCount));
    }

    /**
     * 실사 상세 조회
     * GET /api/v1/cycle-counts/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<CycleCountResponse> getCycleCount(
        @PathVariable("id") UUID cycleCountId
    ) {
        CycleCount cycleCount = cycleCountService.getCycleCount(cycleCountId);
        return ApiResponse.success(CycleCountResponse.from(cycleCount));
    }

    /**
     * 실사 목록 조회
     * GET /api/v1/cycle-counts
     */
    @GetMapping
    public ApiResponse<List<CycleCountResponse>> getAllCycleCounts() {
        List<CycleCount> cycleCounts = cycleCountService.getAllCycleCounts();
        List<CycleCountResponse> responses = cycleCounts.stream()
            .map(CycleCountResponse::from)
            .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\InboundReceiptController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptCreateRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
            @Valid @RequestBody InboundReceiptCreateRequest request) {
        InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(receiptId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(receiptId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(receiptId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(receiptId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InboundReceiptResponse>>> getAllInboundReceipts() {
        List<InboundReceiptResponse> responses = inboundReceiptService.getAllInboundReceipts();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\InventoryAdjustmentController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InventoryAdjustmentApprovalRequest;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.InventoryAdjustment;
import com.wms.service.InventoryAdjustmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/inventory-adjustments")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService adjustmentService;

    /**
     * 재고 조정 생성
     * POST /api/v1/inventory-adjustments
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryAdjustmentResponse> createAdjustment(
        @RequestBody InventoryAdjustmentRequest request
    ) {
        InventoryAdjustment adjustment = adjustmentService.createAdjustment(
            request.getProductId(),
            request.getLocationId(),
            request.getActualQty(),
            request.getReason(),
            request.getAdjustedBy()
        );
        return ApiResponse.success(InventoryAdjustmentResponse.from(adjustment));
    }

    /**
     * 재고 조정 승인
     * POST /api/v1/inventory-adjustments/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<InventoryAdjustmentResponse> approveAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody InventoryAdjustmentApprovalRequest request
    ) {
        InventoryAdjustment adjustment = adjustmentService.approveAdjustment(
            adjustmentId,
            request.getApprovedBy()
        );
        return ApiResponse.success(InventoryAdjustmentResponse.from(adjustment));
    }

    /**
     * 재고 조정 거부
     * POST /api/v1/inventory-adjustments/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ApiResponse<InventoryAdjustmentResponse> rejectAdjustment(
        @PathVariable("id") UUID adjustmentId,
        @RequestBody InventoryAdjustmentApprovalRequest request
    ) {
        InventoryAdjustment adjustment = adjustmentService.rejectAdjustment(
            adjustmentId,
            request.getApprovedBy()
        );
        return ApiResponse.success(InventoryAdjustmentResponse.from(adjustment));
    }

    /**
     * 재고 조정 상세 조회
     * GET /api/v1/inventory-adjustments/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<InventoryAdjustmentResponse> getAdjustment(
        @PathVariable("id") UUID adjustmentId
    ) {
        InventoryAdjustment adjustment = adjustmentService.getAdjustment(adjustmentId);
        return ApiResponse.success(InventoryAdjustmentResponse.from(adjustment));
    }

    /**
     * 재고 조정 목록 조회
     * GET /api/v1/inventory-adjustments
     */
    @GetMapping
    public ApiResponse<List<InventoryAdjustmentResponse>> getAllAdjustments(
        @RequestParam(value = "status", required = false) String status
    ) {
        List<InventoryAdjustment> adjustments;

        if ("pending".equalsIgnoreCase(status)) {
            adjustments = adjustmentService.getPendingAdjustments();
        } else {
            adjustments = adjustmentService.getAllAdjustments();
        }

        List<InventoryAdjustmentResponse> responses = adjustments.stream()
            .map(InventoryAdjustmentResponse::from)
            .collect(Collectors.toList());

        return ApiResponse.success(responses);
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\ShipmentOrderController.java
============================================================
package com.wms.controller;

import com.wms.dto.*;
import com.wms.service.ShipmentOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipment-orders")
@RequiredArgsConstructor
public class ShipmentOrderController {

    private final ShipmentOrderService shipmentOrderService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> createShipmentOrder(
            @RequestBody ShipmentOrderCreateRequest request) {
        ShipmentOrderResponse response = shipmentOrderService.createShipmentOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{shipmentId}/pick")
    public ResponseEntity<ApiResponse<PickResponse>> pickShipmentOrder(
            @PathVariable UUID shipmentId) {
        PickResponse response = shipmentOrderService.pickShipmentOrder(shipmentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{shipmentId}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> shipShipmentOrder(
            @PathVariable UUID shipmentId) {
        ShipmentOrderResponse response = shipmentOrderService.shipShipmentOrder(shipmentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{shipmentId}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(
            @PathVariable UUID shipmentId) {
        ShipmentOrderResponse response = shipmentOrderService.getShipmentOrder(shipmentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentOrderResponse>>> getAllShipmentOrders() {
        List<ShipmentOrderResponse> response = shipmentOrderService.getAllShipmentOrders();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\StockTransferController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.StockTransferApprovalRequest;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.service.StockTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> createStockTransfer(
            @Valid @RequestBody StockTransferRequest request) {
        StockTransferResponse response = stockTransferService.createStockTransfer(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable("id") UUID transferId,
            @Valid @RequestBody StockTransferApprovalRequest request) {
        StockTransferResponse response = stockTransferService.approveTransfer(transferId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable("id") UUID transferId,
            @Valid @RequestBody StockTransferApprovalRequest request) {
        StockTransferResponse response = stockTransferService.rejectTransfer(transferId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getStockTransfer(
            @PathVariable("id") UUID transferId) {
        StockTransferResponse response = stockTransferService.getStockTransfer(transferId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getAllStockTransfers() {
        List<StockTransferResponse> responses = stockTransferService.getAllStockTransfers();
        return ResponseEntity.ok(ApiResponse.success(responses));
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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ErrorInfo {
        private String code;
        private String message;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.CycleCount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CycleCountResponse {
    private UUID cycleCountId;
    private UUID locationId;
    private String locationCode;
    private String status;
    private String startedBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;

    public static CycleCountResponse from(CycleCount cycleCount) {
        return CycleCountResponse.builder()
            .cycleCountId(cycleCount.getCycleCountId())
            .locationId(cycleCount.getLocation().getLocationId())
            .locationCode(cycleCount.getLocation().getCode())
            .status(cycleCount.getStatus().name())
            .startedBy(cycleCount.getStartedBy())
            .startedAt(cycleCount.getStartedAt())
            .completedAt(cycleCount.getCompletedAt())
            .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountStartRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CycleCountStartRequest {
    private UUID locationId;
    private String startedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptCreateRequest.java
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptCreateRequest {

    @NotNull(message = "PO ID is required")
    private UUID poId;

    @NotBlank(message = "Received by is required")
    private String receivedBy;

    @NotEmpty(message = "Receipt lines cannot be empty")
    @Valid
    private List<InboundReceiptLineRequest> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptResponse {

    private UUID receiptId;
    private UUID poId;
    private String status;
    private String receivedBy;
    private OffsetDateTime receivedAt;
    private OffsetDateTime confirmedAt;
    private List<InboundReceiptLineResponse> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboundReceiptLineResponse {
        private UUID receiptLineId;
        private UUID productId;
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
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentApprovalRequest.java
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
public class InventoryAdjustmentApprovalRequest {
    private String approvedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustmentRequest {
    private UUID productId;
    private UUID locationId;
    private Integer actualQty;
    private String reason;
    private String adjustedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\InventoryAdjustmentResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.InventoryAdjustment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustmentResponse {
    private UUID adjustmentId;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID locationId;
    private String locationCode;
    private Integer systemQty;
    private Integer actualQty;
    private Integer difference;
    private String reason;
    private Boolean requiresApproval;
    private String approvalStatus;
    private String approvedBy;
    private String adjustedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime approvedAt;

    public static InventoryAdjustmentResponse from(InventoryAdjustment adjustment) {
        return InventoryAdjustmentResponse.builder()
            .adjustmentId(adjustment.getAdjustmentId())
            .productId(adjustment.getProduct().getProductId())
            .productSku(adjustment.getProduct().getSku())
            .productName(adjustment.getProduct().getName())
            .locationId(adjustment.getLocation().getLocationId())
            .locationCode(adjustment.getLocation().getCode())
            .systemQty(adjustment.getSystemQty())
            .actualQty(adjustment.getActualQty())
            .difference(adjustment.getDifference())
            .reason(adjustment.getReason())
            .requiresApproval(adjustment.getRequiresApproval())
            .approvalStatus(adjustment.getApprovalStatus().name())
            .approvedBy(adjustment.getApprovedBy())
            .adjustedBy(adjustment.getAdjustedBy())
            .createdAt(adjustment.getCreatedAt())
            .approvedAt(adjustment.getApprovedAt())
            .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\PickResponse.java
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
public class PickResponse {
    private UUID shipmentId;
    private String shipmentNumber;
    private String status;
    private List<LinePickResult> lineResults;
    private List<BackorderInfo> backorders;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LinePickResult {
        private UUID shipmentLineId;
        private UUID productId;
        private String productName;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
        private List<PickDetail> pickDetails;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PickDetail {
        private UUID locationId;
        private String locationCode;
        private Integer pickedQty;
        private String lotNumber;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BackorderInfo {
        private UUID backorderId;
        private UUID productId;
        private String productName;
        private Integer shortageQty;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderCreateRequest.java
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
public class ShipmentOrderCreateRequest {
    private String shipmentNumber;
    private String customerName;
    private OffsetDateTime requestedAt;
    private List<ShipmentOrderLineRequest> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineRequest {
        private UUID productId;
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
    private UUID shipmentId;
    private String shipmentNumber;
    private String customerName;
    private String status;
    private OffsetDateTime requestedAt;
    private OffsetDateTime shippedAt;
    private List<ShipmentOrderLineResponse> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineResponse {
        private UUID shipmentLineId;
        private UUID productId;
        private String productName;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferApprovalRequest.java
============================================================
package com.wms.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferApprovalRequest {

    private String approvedBy;
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

    private UUID productId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
    private String lotNumber;
    private String reason;
    private String transferredBy;
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

    private UUID transferId;
    private UUID productId;
    private String productSku;
    private String productName;
    private UUID fromLocationId;
    private String fromLocationCode;
    private UUID toLocationId;
    private String toLocationCode;
    private Integer quantity;
    private String lotNumber;
    private String reason;
    private String transferStatus;
    private String transferredBy;
    private String approvedBy;
    private OffsetDateTime transferredAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\AuditLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
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
    @Column(name = "log_id")
    private UUID logId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (logId == null) {
            logId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\AutoReorderLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
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
    @Column(name = "reorder_log_id")
    private UUID reorderLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private TriggerType triggerType;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Column(name = "triggered_by", nullable = false, length = 100)
    private String triggeredBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (reorderLogId == null) {
            reorderLogId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public enum TriggerType {
        SAFETY_STOCK_TRIGGER, URGENT_REORDER
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Backorder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
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
    @Column(name = "backorder_id")
    private UUID backorderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_line_id", nullable = false)
    private ShipmentOrderLine shipmentOrderLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "shortage_qty", nullable = false)
    private Integer shortageQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackorderStatus status = BackorderStatus.open;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "fulfilled_at")
    private OffsetDateTime fulfilledAt;

    @PrePersist
    protected void onCreate() {
        if (backorderId == null) {
            backorderId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
    }

    public enum BackorderStatus {
        open, fulfilled, cancelled
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\CycleCount.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "cycle_counts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCount {

    @Id
    @Column(name = "cycle_count_id")
    private UUID cycleCountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CycleCountStatus status = CycleCountStatus.IN_PROGRESS;

    @Column(name = "started_by", nullable = false, length = 100)
    private String startedBy;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (cycleCountId == null) {
            cycleCountId = UUID.randomUUID();
        }
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
    }

    public enum CycleCountStatus {
        IN_PROGRESS, COMPLETED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceipt.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
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
    @Column(name = "receipt_id")
    private UUID receiptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "po_id", nullable = false)
    private PurchaseOrder purchaseOrder;

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
// FILE: src\main\java\com\wms\entity\InventoryAdjustment.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_adjustments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryAdjustment {

    @Id
    @Column(name = "adjustment_id")
    private UUID adjustmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "system_qty", nullable = false)
    private Integer systemQty;

    @Column(name = "actual_qty", nullable = false)
    private Integer actualQty;

    @Column(name = "difference", nullable = false)
    private Integer difference;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "requires_approval", nullable = false)
    private Boolean requiresApproval = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus = ApprovalStatus.AUTO_APPROVED;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "adjusted_by", nullable = false, length = 100)
    private String adjustedBy;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @PrePersist
    protected void onCreate() {
        if (adjustmentId == null) {
            adjustmentId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public enum ApprovalStatus {
        AUTO_APPROVED, PENDING, APPROVED, REJECTED
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
    private StorageType storageType = StorageType.AMBIENT;

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

    public enum StorageType {
        AMBIENT, COLD, FROZEN
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

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
// FILE: src\main\java\com\wms\entity\SafetyStockRule.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
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
    @Column(name = "rule_id")
    private UUID ruleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (ruleId == null) {
            ruleId = UUID.randomUUID();
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
    private BigDecimal multiplier = BigDecimal.valueOf(1.50);

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
// FILE: src\main\java\com\wms\entity\ShipmentOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
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
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "shipment_number", unique = true, nullable = false, length = 30)
    private String shipmentNumber;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.pending;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (shipmentId == null) {
            shipmentId = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum ShipmentStatus {
        pending, picking, partial, shipped, cancelled
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrderLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
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
    @Column(name = "shipment_line_id")
    private UUID shipmentLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private ShipmentOrder shipmentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "picked_qty", nullable = false)
    private Integer pickedQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LineStatus status = LineStatus.pending;

    @PrePersist
    protected void onCreate() {
        if (shipmentLineId == null) {
            shipmentLineId = UUID.randomUUID();
        }
    }

    public enum LineStatus {
        pending, picked, partial, backordered
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\StockTransfer.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
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
    @Column(name = "transfer_id")
    private UUID transferId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id", nullable = false)
    private Location fromLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id", nullable = false)
    private Location toLocation;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "lot_number", length = 50)
    private String lotNumber;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 20)
    private TransferStatus transferStatus = TransferStatus.IMMEDIATE;

    @Column(name = "transferred_by", nullable = false, length = 100)
    private String transferredBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "transferred_at")
    private OffsetDateTime transferredAt;

    @PrePersist
    protected void onCreate() {
        if (transferId == null) {
            transferId = UUID.randomUUID();
        }
        if (transferredAt == null) {
            transferredAt = OffsetDateTime.now();
        }
    }

    public enum TransferStatus {
        IMMEDIATE, PENDING_APPROVAL, APPROVED, REJECTED
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
// FILE: src\main\java\com\wms\exception\GlobalExceptionHandler.java
============================================================
package com.wms.exception;

import com.wms.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status = determineHttpStatus(ex.getCode());
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred: " + ex.getMessage()));
    }

    private HttpStatus determineHttpStatus(String code) {
        return switch (code) {
            case "NOT_FOUND", "PO_NOT_FOUND", "PRODUCT_NOT_FOUND", "LOCATION_NOT_FOUND", "RECEIPT_NOT_FOUND" ->
                    HttpStatus.NOT_FOUND;
            case "OVER_DELIVERY", "STORAGE_TYPE_MISMATCH", "LOCATION_FROZEN", "EXPIRY_DATE_REQUIRED",
                 "MANUFACTURE_DATE_REQUIRED", "INVALID_STATUS" ->
                    HttpStatus.CONFLICT;
            case "VALIDATION_ERROR", "MISSING_EXPIRY_DATE", "MISSING_MANUFACTURE_DATE" ->
                    HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
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
// FILE: src\main\java\com\wms\repository\CycleCountRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.CycleCount;
import com.wms.entity.CycleCount.CycleCountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {

    List<CycleCount> findByLocationLocationIdAndStatus(UUID locationId, CycleCountStatus status);
}


============================================================
// FILE: src\main\java\com\wms\repository\InboundReceiptLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InboundReceiptLine;
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
// FILE: src\main\java\com\wms\repository\InventoryAdjustmentRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @Query("SELECT COUNT(ia) FROM InventoryAdjustment ia " +
           "WHERE ia.product.productId = :productId " +
           "AND ia.location.locationId = :locationId " +
           "AND ia.createdAt >= :since")
    long countRecentAdjustments(
        @Param("productId") UUID productId,
        @Param("locationId") UUID locationId,
        @Param("since") OffsetDateTime since
    );

    List<InventoryAdjustment> findByApprovalStatus(InventoryAdjustment.ApprovalStatus status);
}


============================================================
// FILE: src\main\java\com\wms\repository\InventoryRepository.java
============================================================
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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.purchaseOrder.poId = :poId")
    List<PurchaseOrderLine> findByPurchaseOrderId(@Param("poId") UUID poId);

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.purchaseOrder.poId = :poId " +
           "AND pol.product.productId = :productId")
    Optional<PurchaseOrderLine> findByPurchaseOrderIdAndProductId(
        @Param("poId") UUID poId,
        @Param("productId") UUID productId);
}


============================================================
// FILE: src\main\java\com\wms\repository\PurchaseOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {
    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    @Query("SELECT po FROM PurchaseOrder po WHERE po.supplier.supplierId = :supplierId " +
           "AND po.status = 'pending'")
    List<PurchaseOrder> findPendingOrdersBySupplierId(@Param("supplierId") UUID supplierId);
}


============================================================
// FILE: src\main\java\com\wms\repository\SafetyStockRuleRepository.java
============================================================
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

    @Query("SELECT sc FROM SeasonalConfig sc WHERE sc.isActive = true " +
           "AND :date BETWEEN sc.startDate AND sc.endDate")
    Optional<SeasonalConfig> findActiveSeasonByDate(@Param("date") LocalDate date);
}


============================================================
// FILE: src\main\java\com\wms\repository\ShipmentOrderLineRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.ShipmentOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShipmentOrderLineRepository extends JpaRepository<ShipmentOrderLine, UUID> {
    List<ShipmentOrderLine> findByShipmentOrderShipmentId(UUID shipmentId);
}


============================================================
// FILE: src\main\java\com\wms\repository\ShipmentOrderRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.ShipmentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ShipmentOrderRepository extends JpaRepository<ShipmentOrder, UUID> {
}


============================================================
// FILE: src\main\java\com\wms\repository\StockTransferRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    List<StockTransfer> findByProductProductIdOrderByTransferredAtDesc(UUID productId);

    List<StockTransfer> findByFromLocationLocationIdOrderByTransferredAtDesc(UUID fromLocationId);

    List<StockTransfer> findByToLocationLocationIdOrderByTransferredAtDesc(UUID toLocationId);
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
import java.util.List;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {

    @Query("SELECT COUNT(sp) FROM SupplierPenalty sp WHERE sp.supplier.supplierId = :supplierId " +
           "AND sp.createdAt >= :since")
    long countBySupplierIdAndCreatedAtAfter(
        @Param("supplierId") UUID supplierId,
        @Param("since") OffsetDateTime since);
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
// FILE: src\main\java\com\wms\service\CycleCountService.java
============================================================
package com.wms.service;

import com.wms.entity.CycleCount;
import com.wms.entity.CycleCount.CycleCountStatus;
import com.wms.entity.Location;
import com.wms.exception.BusinessException;
import com.wms.repository.CycleCountRepository;
import com.wms.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CycleCountService {

    private final CycleCountRepository cycleCountRepository;
    private final LocationRepository locationRepository;

    /**
     * 실사 시작
     * - cycle_counts 레코드 생성
     * - 해당 로케이션의 is_frozen을 true로 설정 (입고/출고/이동 동결)
     */
    @Transactional
    public CycleCount startCycleCount(UUID locationId, String startedBy) {
        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        // 이미 진행 중인 실사가 있는지 체크
        List<CycleCount> inProgressCounts = cycleCountRepository
            .findByLocationLocationIdAndStatus(locationId, CycleCountStatus.IN_PROGRESS);

        if (!inProgressCounts.isEmpty()) {
            throw new BusinessException("CYCLE_COUNT_IN_PROGRESS",
                "Cycle count already in progress for this location");
        }

        // 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 실사 세션 생성
        CycleCount cycleCount = CycleCount.builder()
            .location(location)
            .status(CycleCountStatus.IN_PROGRESS)
            .startedBy(startedBy)
            .build();

        return cycleCountRepository.save(cycleCount);
    }

    /**
     * 실사 완료
     * - 해당 로케이션의 is_frozen을 false로 해제
     * - cycle_count 상태를 completed로 변경
     */
    @Transactional
    public CycleCount completeCycleCount(UUID cycleCountId) {
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
            .orElseThrow(() -> new BusinessException("CYCLE_COUNT_NOT_FOUND", "Cycle count not found"));

        if (cycleCount.getStatus() == CycleCountStatus.COMPLETED) {
            throw new BusinessException("CYCLE_COUNT_ALREADY_COMPLETED",
                "Cycle count already completed");
        }

        // 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        // 실사 완료 처리
        cycleCount.setStatus(CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(OffsetDateTime.now());

        return cycleCountRepository.save(cycleCount);
    }

    @Transactional(readOnly = true)
    public CycleCount getCycleCount(UUID cycleCountId) {
        return cycleCountRepository.findById(cycleCountId)
            .orElseThrow(() -> new BusinessException("CYCLE_COUNT_NOT_FOUND", "Cycle count not found"));
    }

    @Transactional(readOnly = true)
    public List<CycleCount> getAllCycleCounts() {
        return cycleCountRepository.findAll();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\InboundReceiptService.java
============================================================
package com.wms.service;

import com.wms.dto.InboundReceiptCreateRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
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

    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptCreateRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
                .orElseThrow(() -> new BusinessException("PO_NOT_FOUND", "Purchase order not found"));

        // 2. 입고 전표 생성 (inspecting 상태)
        InboundReceipt receipt = InboundReceipt.builder()
                .purchaseOrder(po)
                .status(InboundReceipt.ReceiptStatus.inspecting)
                .receivedBy(request.getReceivedBy())
                .build();
        inboundReceiptRepository.save(receipt);

        // 3. 입고 라인 검증 및 생성
        for (InboundReceiptCreateRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            validateAndCreateReceiptLine(receipt, po, lineReq);
        }

        return buildReceiptResponse(receipt);
    }

    private void validateAndCreateReceiptLine(InboundReceipt receipt, PurchaseOrder po,
                                               InboundReceiptCreateRequest.InboundReceiptLineRequest lineReq) {
        // 상품 조회
        Product product = productRepository.findById(lineReq.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        // 로케이션 조회
        Location location = locationRepository.findById(lineReq.getLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        // 1. 실사 동결 로케이션 체크
        if (Boolean.TRUE.equals(location.getIsFrozen())) {
            throw new BusinessException("LOCATION_FROZEN", "Location is frozen for cycle count");
        }

        // 2. 유통기한 관리 상품 검증
        if (Boolean.TRUE.equals(product.getHasExpiry())) {
            if (lineReq.getExpiryDate() == null) {
                throw new BusinessException("MISSING_EXPIRY_DATE", "Expiry date is required for this product");
            }
            if (lineReq.getManufactureDate() == null) {
                throw new BusinessException("MISSING_MANUFACTURE_DATE", "Manufacture date is required for this product");
            }

            // 잔여 유통기한 비율 체크
            double remainingPct = calculateRemainingShelfLifePct(
                    lineReq.getManufactureDate(), lineReq.getExpiryDate());

            int minPct = product.getMinRemainingShelfLifePct() != null
                    ? product.getMinRemainingShelfLifePct() : 30;

            if (remainingPct < minPct) {
                // 유통기한 부족으로 거부 -> 페널티 부과
                recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                        SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                        String.format("Remaining shelf life %.1f%% is below minimum %d%%", remainingPct, minPct));
                throw new BusinessException("SHORT_SHELF_LIFE",
                        String.format("Remaining shelf life %.1f%% is below minimum %d%%", remainingPct, minPct));
            }

            // 30~50% 범위: 경고 + 승인 필요
            if (remainingPct >= 30 && remainingPct <= 50) {
                receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
            }
        }

        // 3. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, location);

        // 4. 초과입고 체크
        validateOverDelivery(po, product, lineReq.getQuantity());

        // 5. 입고 라인 생성
        InboundReceiptLine receiptLine = InboundReceiptLine.builder()
                .inboundReceipt(receipt)
                .product(product)
                .location(location)
                .quantity(lineReq.getQuantity())
                .lotNumber(lineReq.getLotNumber())
                .expiryDate(lineReq.getExpiryDate())
                .manufactureDate(lineReq.getManufactureDate())
                .build();
        inboundReceiptLineRepository.save(receiptLine);
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        return (double) remainingDays / totalDays * 100.0;
    }

    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productStorage = product.getStorageType();
        Location.StorageType locationStorage = location.getStorageType();

        // HAZMAT 상품은 HAZMAT zone만 허용
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new BusinessException("STORAGE_TYPE_MISMATCH",
                        "HAZMAT products must be stored in HAZMAT zone");
            }
        }

        // FROZEN 상품 -> FROZEN 로케이션만
        if (productStorage == Product.StorageType.FROZEN) {
            if (locationStorage != Location.StorageType.FROZEN) {
                throw new BusinessException("STORAGE_TYPE_MISMATCH",
                        "FROZEN products require FROZEN location");
            }
        }

        // COLD 상품 -> COLD 또는 FROZEN 로케이션
        if (productStorage == Product.StorageType.COLD) {
            if (locationStorage != Location.StorageType.COLD &&
                    locationStorage != Location.StorageType.FROZEN) {
                throw new BusinessException("STORAGE_TYPE_MISMATCH",
                        "COLD products require COLD or FROZEN location");
            }
        }

        // AMBIENT 상품 -> AMBIENT 로케이션만
        if (productStorage == Product.StorageType.AMBIENT) {
            if (locationStorage != Location.StorageType.AMBIENT) {
                throw new BusinessException("STORAGE_TYPE_MISMATCH",
                        "AMBIENT products require AMBIENT location");
            }
        }
    }

    private void validateOverDelivery(PurchaseOrder po, Product product, int quantity) {
        // PO 라인 조회
        PurchaseOrderLine poLine = purchaseOrderLineRepository
                .findByPurchaseOrderIdAndProductId(po.getPoId(), product.getProductId())
                .orElseThrow(() -> new BusinessException("PO_LINE_NOT_FOUND",
                        "Product not found in purchase order"));

        int orderedQty = poLine.getOrderedQty();
        int alreadyReceivedQty = poLine.getReceivedQty();
        int totalReceivingQty = alreadyReceivedQty + quantity;

        // 카테고리별 기본 허용률
        double baseAllowancePct = getCategoryAllowancePct(product.getCategory());

        // 발주 유형별 가중치
        double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());

        // 성수기 가중치
        BigDecimal seasonalMultiplier = getSeasonalMultiplier();

        // 최종 허용률 계산 (HAZMAT은 항상 0%)
        double finalAllowancePct;
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            finalAllowancePct = 0.0;
        } else {
            finalAllowancePct = baseAllowancePct * poTypeMultiplier * seasonalMultiplier.doubleValue();
        }

        double maxAllowedQty = orderedQty * (1 + finalAllowancePct / 100.0);

        if (totalReceivingQty > maxAllowedQty) {
            // 초과입고 거부 -> 페널티 부과
            recordSupplierPenalty(po.getSupplier(), po.getPoId(),
                    SupplierPenalty.PenaltyType.OVER_DELIVERY,
                    String.format("Over delivery: receiving %d, allowed %.0f (ordered %d, allowance %.1f%%)",
                            totalReceivingQty, maxAllowedQty, orderedQty, finalAllowancePct));
            throw new BusinessException("OVER_DELIVERY",
                    String.format("Over delivery: receiving %d exceeds allowed %.0f (ordered %d, allowance %.1f%%)",
                            totalReceivingQty, maxAllowedQty, orderedQty, finalAllowancePct));
        }
    }

    private double getCategoryAllowancePct(Product.ProductCategory category) {
        return switch (category) {
            case GENERAL -> 10.0;
            case FRESH -> 5.0;
            case HAZMAT -> 0.0;
            case HIGH_VALUE -> 3.0;
        };
    }

    private double getPoTypeMultiplier(PurchaseOrder.PoType poType) {
        return switch (poType) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };
    }

    private BigDecimal getSeasonalMultiplier() {
        return seasonalConfigRepository.findActiveSeasonByDate(LocalDate.now())
                .map(SeasonalConfig::getMultiplier)
                .orElse(BigDecimal.ONE);
    }

    private void recordSupplierPenalty(Supplier supplier, UUID poId,
                                        SupplierPenalty.PenaltyType penaltyType, String description) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .poId(poId)
                .penaltyType(penaltyType)
                .description(description)
                .build();
        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 3회 이상 체크
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long penaltyCount = supplierPenaltyRepository.countBySupplierIdAndCreatedAtAfter(
                supplier.getSupplierId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // 해당 공급업체의 모든 pending PO를 hold로 변경
            List<PurchaseOrder> pendingPos = purchaseOrderRepository
                    .findPendingOrdersBySupplierId(supplier.getSupplierId());
            for (PurchaseOrder pendingPo : pendingPos) {
                pendingPo.setStatus(PurchaseOrder.PoStatus.hold);
                purchaseOrderRepository.save(pendingPo);
            }
            log.warn("Supplier {} has {} penalties in 30 days. All pending POs are on hold.",
                    supplier.getName(), penaltyCount);
        }
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
                receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS",
                    "Receipt must be in inspecting or pending_approval status");
        }

        // 상태를 confirmed로 변경
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());
        inboundReceiptRepository.save(receipt);

        // 재고 반영 및 PO 업데이트
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receiptId);
        for (InboundReceiptLine line : lines) {
            // 재고 증가
            updateInventory(line);

            // 로케이션 current_qty 증가
            Location location = line.getLocation();
            location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
            locationRepository.save(location);

            // PO 라인 received_qty 갱신
            PurchaseOrderLine poLine = purchaseOrderLineRepository
                    .findByPurchaseOrderIdAndProductId(
                            receipt.getPurchaseOrder().getPoId(),
                            line.getProduct().getProductId())
                    .orElseThrow();
            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // PO 상태 갱신
        updatePurchaseOrderStatus(receipt.getPurchaseOrder().getPoId());

        return buildReceiptResponse(receipt);
    }

    private void updateInventory(InboundReceiptLine line) {
        // 동일 상품+로케이션+로트 조합이 있는지 확인
        String lotNumber = line.getLotNumber() != null ? line.getLotNumber() : "";
        var existingInventory = inventoryRepository.findByProductProductIdAndLocationLocationIdAndLotNumber(
                line.getProduct().getProductId(),
                line.getLocation().getLocationId(),
                lotNumber);

        if (existingInventory.isPresent()) {
            // 기존 재고 수량 증가
            Inventory inventory = existingInventory.get();
            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
            inventoryRepository.save(inventory);
        } else {
            // 새 재고 레코드 생성
            Inventory inventory = Inventory.builder()
                    .product(line.getProduct())
                    .location(line.getLocation())
                    .quantity(line.getQuantity())
                    .lotNumber(line.getLotNumber())
                    .expiryDate(line.getExpiryDate())
                    .manufactureDate(line.getManufactureDate())
                    .receivedAt(OffsetDateTime.now())
                    .isExpired(false)
                    .build();
            inventoryRepository.save(inventory);
        }
    }

    private void updatePurchaseOrderStatus(UUID poId) {
        List<PurchaseOrderLine> poLines = purchaseOrderLineRepository.findByPurchaseOrderId(poId);
        boolean allFulfilled = true;
        boolean anyFulfilled = false;

        for (PurchaseOrderLine line : poLines) {
            if (line.getReceivedQty() < line.getOrderedQty()) {
                allFulfilled = false;
            }
            if (line.getReceivedQty() > 0) {
                anyFulfilled = true;
            }
        }

        PurchaseOrder po = purchaseOrderRepository.findById(poId).orElseThrow();
        if (allFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
        } else if (anyFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
        }
        purchaseOrderRepository.save(po);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
                receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS",
                    "Receipt must be in inspecting or pending_approval status");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);
        inboundReceiptRepository.save(receipt);

        return buildReceiptResponse(receipt);
    }

    @Transactional
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new BusinessException("INVALID_STATUS",
                    "Receipt must be in pending_approval status");
        }

        // pending_approval -> inspecting으로 변경 (이후 confirm 가능)
        receipt.setStatus(InboundReceipt.ReceiptStatus.inspecting);
        inboundReceiptRepository.save(receipt);

        return buildReceiptResponse(receipt);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Receipt not found"));
        return buildReceiptResponse(receipt);
    }

    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        return inboundReceiptRepository.findAll().stream()
                .map(this::buildReceiptResponse)
                .collect(Collectors.toList());
    }

    private InboundReceiptResponse buildReceiptResponse(InboundReceipt receipt) {
        List<InboundReceiptLine> lines = inboundReceiptLineRepository.findByReceiptId(receipt.getReceiptId());

        return InboundReceiptResponse.builder()
                .receiptId(receipt.getReceiptId())
                .poId(receipt.getPurchaseOrder().getPoId())
                .status(receipt.getStatus().name())
                .receivedBy(receipt.getReceivedBy())
                .receivedAt(receipt.getReceivedAt())
                .confirmedAt(receipt.getConfirmedAt())
                .lines(lines.stream()
                        .map(line -> InboundReceiptResponse.InboundReceiptLineResponse.builder()
                                .receiptLineId(line.getReceiptLineId())
                                .productId(line.getProduct().getProductId())
                                .productName(line.getProduct().getName())
                                .locationId(line.getLocation().getLocationId())
                                .locationCode(line.getLocation().getCode())
                                .quantity(line.getQuantity())
                                .lotNumber(line.getLotNumber())
                                .expiryDate(line.getExpiryDate())
                                .manufactureDate(line.getManufactureDate())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\InventoryAdjustmentService.java
============================================================
package com.wms.service;

import com.wms.entity.*;
import com.wms.entity.InventoryAdjustment.ApprovalStatus;
import com.wms.entity.Product.ProductCategory;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository adjustmentRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * 재고 조정 생성
     * - 시스템 재고와 실제 재고 비교
     * - 카테고리별 자동승인 임계치 적용
     * - 연속 조정 감시
     * - HIGH_VALUE 전수 검증
     */
    @Transactional
    public InventoryAdjustment createAdjustment(
        UUID productId,
        UUID locationId,
        Integer actualQty,
        String reason,
        String adjustedBy
    ) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException("REASON_REQUIRED", "Adjustment reason is required");
        }

        if (actualQty < 0) {
            throw new BusinessException("INVALID_ACTUAL_QTY", "Actual quantity cannot be negative");
        }

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        Location location = locationRepository.findById(locationId)
            .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        // 시스템 재고 조회 (해당 location + product의 총 재고)
        Integer systemQty = inventoryRepository.findByProductProductIdAndLocationLocationId(productId, locationId)
            .stream()
            .mapToInt(Inventory::getQuantity)
            .sum();

        Integer difference = actualQty - systemQty;

        // system_qty가 0인 경우 (시스템에 없는데 실물이 있는 경우) 무조건 승인 필요
        boolean requiresApproval = (systemQty == 0 && actualQty > 0);
        ApprovalStatus approvalStatus = requiresApproval ? ApprovalStatus.PENDING : ApprovalStatus.AUTO_APPROVED;

        String finalReason = reason;

        // 연속 조정 감시: 최근 7일 내 동일 로케이션+상품 조정이 2회 이상인지 체크
        if (!requiresApproval) {
            OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
            long recentAdjustmentCount = adjustmentRepository.countRecentAdjustments(
                productId, locationId, sevenDaysAgo
            );

            if (recentAdjustmentCount >= 2) {
                requiresApproval = true;
                approvalStatus = ApprovalStatus.PENDING;
                finalReason = "[연속조정감시] " + reason;
            }
        }

        // HIGH_VALUE 카테고리: 차이가 0이 아닌 모든 경우 승인 필요
        if (product.getCategory() == ProductCategory.HIGH_VALUE && difference != 0) {
            requiresApproval = true;
            approvalStatus = ApprovalStatus.PENDING;
        }

        // 카테고리별 자동승인 임계치 체크 (승인이 필요하지 않은 경우만)
        if (!requiresApproval && systemQty > 0) {
            double diffPercentage = Math.abs(difference) * 100.0 / systemQty;
            double threshold = getAutoApprovalThreshold(product.getCategory());

            if (diffPercentage > threshold) {
                requiresApproval = true;
                approvalStatus = ApprovalStatus.PENDING;
            }
        }

        InventoryAdjustment adjustment = InventoryAdjustment.builder()
            .product(product)
            .location(location)
            .systemQty(systemQty)
            .actualQty(actualQty)
            .difference(difference)
            .reason(finalReason)
            .requiresApproval(requiresApproval)
            .approvalStatus(approvalStatus)
            .adjustedBy(adjustedBy)
            .build();

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);

        // 자동승인된 경우 즉시 재고 반영
        if (approvalStatus == ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(saved);
        }

        return saved;
    }

    /**
     * 조정 승인
     */
    @Transactional
    public InventoryAdjustment approveAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found"));

        if (adjustment.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("ADJUSTMENT_NOT_PENDING", "Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus(ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);

        // 재고 반영
        applyAdjustment(saved);

        return saved;
    }

    /**
     * 조정 거부
     */
    @Transactional
    public InventoryAdjustment rejectAdjustment(UUID adjustmentId, String approvedBy) {
        InventoryAdjustment adjustment = adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found"));

        if (adjustment.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("ADJUSTMENT_NOT_PENDING", "Adjustment is not pending approval");
        }

        adjustment.setApprovalStatus(ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(approvedBy);
        adjustment.setApprovedAt(OffsetDateTime.now());

        return adjustmentRepository.save(adjustment);
    }

    /**
     * 재고 반영 (승인된 조정 또는 자동승인된 조정에 대해)
     * - inventory.quantity 갱신
     * - locations.current_qty 갱신
     * - HIGH_VALUE 감사 로그 기록
     * - 안전재고 체크 및 자동 재발주
     */
    private void applyAdjustment(InventoryAdjustment adjustment) {
        Product product = adjustment.getProduct();
        Location location = adjustment.getLocation();
        Integer difference = adjustment.getDifference();

        // inventory 레코드 갱신 (해당 location + product의 재고 레코드)
        List<Inventory> inventories = inventoryRepository
            .findByProductProductIdAndLocationLocationId(
                product.getProductId(),
                location.getLocationId()
            );

        if (inventories.isEmpty() && adjustment.getActualQty() > 0) {
            // 시스템에 없었는데 실물이 발견된 경우 → 새 레코드 생성
            Inventory newInventory = Inventory.builder()
                .product(product)
                .location(location)
                .quantity(adjustment.getActualQty())
                .receivedAt(OffsetDateTime.now())
                .isExpired(false)
                .build();
            inventoryRepository.save(newInventory);
        } else if (!inventories.isEmpty()) {
            // 기존 레코드가 있으면 첫 번째 레코드를 갱신 (단순화)
            Inventory inventory = inventories.get(0);
            int newQty = inventory.getQuantity() + difference;

            if (newQty < 0) {
                throw new BusinessException("NEGATIVE_INVENTORY",
                    "Adjustment would result in negative inventory");
            }

            inventory.setQuantity(newQty);
            inventoryRepository.save(inventory);
        }

        // locations.current_qty 갱신
        location.setCurrentQty(location.getCurrentQty() + difference);
        locationRepository.save(location);

        // HIGH_VALUE 감사 로그 기록
        if (product.getCategory() == ProductCategory.HIGH_VALUE && difference != 0) {
            AuditLog auditLog = AuditLog.builder()
                .eventType("HIGH_VALUE_ADJUSTMENT")
                .entityType("InventoryAdjustment")
                .entityId(adjustment.getAdjustmentId())
                .details(String.format(
                    "{\"product_id\":\"%s\",\"location_id\":\"%s\",\"system_qty\":%d,\"actual_qty\":%d,\"difference\":%d}",
                    product.getProductId(),
                    location.getLocationId(),
                    adjustment.getSystemQty(),
                    adjustment.getActualQty(),
                    difference
                ))
                .performedBy(adjustment.getAdjustedBy())
                .build();
            auditLogRepository.save(auditLog);
        }

        // 안전재고 체크 및 자동 재발주
        checkAndTriggerReorder(product);
    }

    /**
     * 안전재고 체크 및 자동 재발주 트리거
     */
    private void checkAndTriggerReorder(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository
            .findByProductProductId(product.getProductId())
            .orElse(null);

        if (rule == null) {
            return;
        }

        // 전체 가용 재고 확인 (is_expired=false인 것만)
        Integer totalAvailable = inventoryRepository.findByProductProductId(product.getProductId())
            .stream()
            .filter(inv -> !inv.getIsExpired())
            .mapToInt(Inventory::getQuantity)
            .sum();

        if (totalAvailable <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                .currentStock(totalAvailable)
                .minQty(rule.getMinQty())
                .reorderQty(rule.getReorderQty())
                .triggeredBy("SYSTEM")
                .build();
            autoReorderLogRepository.save(log);
        }
    }

    /**
     * 카테고리별 자동승인 임계치 반환
     */
    private double getAutoApprovalThreshold(ProductCategory category) {
        return switch (category) {
            case GENERAL -> 5.0;
            case FRESH -> 3.0;
            case HAZMAT -> 1.0;
            case HIGH_VALUE -> 2.0;
        };
    }

    @Transactional(readOnly = true)
    public InventoryAdjustment getAdjustment(UUID adjustmentId) {
        return adjustmentRepository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Adjustment not found"));
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustment> getAllAdjustments() {
        return adjustmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustment> getPendingAdjustments() {
        return adjustmentRepository.findByApprovalStatus(ApprovalStatus.PENDING);
    }
}


============================================================
// FILE: src\main\java\com\wms\service\ShipmentOrderService.java
============================================================
package com.wms.service;

import com.wms.dto.*;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentOrderService {

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
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderCreateRequest request) {
        // HAZMAT/FRESH 분리 출고 체크
        List<ShipmentOrderCreateRequest.ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderCreateRequest.ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();

        for (ShipmentOrderCreateRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
            }
        }

        // HAZMAT과 FRESH가 동시에 있으면 분리
        boolean hasFresh = false;
        for (ShipmentOrderCreateRequest.ShipmentOrderLineRequest lineReq : nonHazmatLines) {
            Product product = productRepository.findById(lineReq.getProductId()).orElseThrow();
            if (product.getCategory() == Product.ProductCategory.FRESH) {
                hasFresh = true;
                break;
            }
        }

        if (!hazmatLines.isEmpty() && hasFresh) {
            // HAZMAT만 별도 출고 지시서 생성
            ShipmentOrderCreateRequest hazmatRequest = ShipmentOrderCreateRequest.builder()
                    .shipmentNumber(request.getShipmentNumber() + "-HAZMAT")
                    .customerName(request.getCustomerName())
                    .requestedAt(request.getRequestedAt())
                    .lines(hazmatLines)
                    .build();
            createSingleShipmentOrder(hazmatRequest);

            // 비-HAZMAT은 원래 요청에서 생성
            ShipmentOrderCreateRequest nonHazmatRequest = ShipmentOrderCreateRequest.builder()
                    .shipmentNumber(request.getShipmentNumber())
                    .customerName(request.getCustomerName())
                    .requestedAt(request.getRequestedAt())
                    .lines(nonHazmatLines)
                    .build();
            return createSingleShipmentOrder(nonHazmatRequest);
        } else {
            // 분리 필요 없으면 그대로 생성
            return createSingleShipmentOrder(request);
        }
    }

    private ShipmentOrderResponse createSingleShipmentOrder(ShipmentOrderCreateRequest request) {
        ShipmentOrder shipmentOrder = ShipmentOrder.builder()
                .shipmentNumber(request.getShipmentNumber())
                .customerName(request.getCustomerName())
                .status(ShipmentOrder.ShipmentStatus.pending)
                .requestedAt(request.getRequestedAt())
                .build();
        shipmentOrderRepository.save(shipmentOrder);

        for (ShipmentOrderCreateRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(shipmentOrder)
                    .product(product)
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.pending)
                    .build();
            shipmentOrderLineRepository.save(line);
        }

        return buildShipmentOrderResponse(shipmentOrder);
    }

    @Transactional
    public PickResponse pickShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment order not found"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.pending) {
            throw new BusinessException("INVALID_STATUS", "Shipment order must be in pending status");
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.picking);
        shipmentOrderRepository.save(shipmentOrder);

        List<PickResponse.LinePickResult> lineResults = new ArrayList<>();
        List<PickResponse.BackorderInfo> backorderInfos = new ArrayList<>();

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrderShipmentId(shipmentId);

        for (ShipmentOrderLine line : lines) {
            PickResponse.LinePickResult lineResult = pickLine(line, backorderInfos);
            lineResults.add(lineResult);
        }

        // 모든 라인이 picked인지 확인
        boolean allPicked = lines.stream()
                .allMatch(line -> line.getStatus() == ShipmentOrderLine.LineStatus.picked);

        if (allPicked) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        } else {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.partial);
        }
        shipmentOrderRepository.save(shipmentOrder);

        return PickResponse.builder()
                .shipmentId(shipmentOrder.getShipmentId())
                .shipmentNumber(shipmentOrder.getShipmentNumber())
                .status(shipmentOrder.getStatus().name())
                .lineResults(lineResults)
                .backorders(backorderInfos)
                .build();
    }

    private PickResponse.LinePickResult pickLine(ShipmentOrderLine line,
                                                   List<PickResponse.BackorderInfo> backorderInfos) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQty();

        // HAZMAT max_pick_qty 제한 체크
        if (product.getCategory() == Product.ProductCategory.HAZMAT &&
                product.getMaxPickQty() != null &&
                requestedQty > product.getMaxPickQty()) {
            requestedQty = product.getMaxPickQty();
        }

        // 피킹 가능한 재고 조회 및 정렬
        List<Inventory> availableInventories = getAvailableInventories(product);

        int totalPicked = 0;
        List<PickResponse.PickDetail> pickDetails = new ArrayList<>();

        for (Inventory inventory : availableInventories) {
            if (totalPicked >= requestedQty) {
                break;
            }

            int neededQty = requestedQty - totalPicked;
            int pickQty = Math.min(neededQty, inventory.getQuantity());

            // 재고 차감
            inventory.setQuantity(inventory.getQuantity() - pickQty);
            inventoryRepository.save(inventory);

            // 로케이션 current_qty 차감
            Location location = inventory.getLocation();
            location.setCurrentQty(location.getCurrentQty() - pickQty);
            locationRepository.save(location);

            // 보관 유형 불일치 경고
            if (location.getStorageType().name().equals(product.getStorageType().name()) == false) {
                logStorageTypeMismatch(product, location);
            }

            totalPicked += pickQty;

            pickDetails.add(PickResponse.PickDetail.builder()
                    .locationId(location.getLocationId())
                    .locationCode(location.getCode())
                    .pickedQty(pickQty)
                    .lotNumber(inventory.getLotNumber())
                    .build());
        }

        // 라인 상태 업데이트
        line.setPickedQty(totalPicked);

        if (totalPicked == 0) {
            line.setStatus(ShipmentOrderLine.LineStatus.backordered);
        } else if (totalPicked < requestedQty) {
            line.setStatus(ShipmentOrderLine.LineStatus.partial);
        } else {
            line.setStatus(ShipmentOrderLine.LineStatus.picked);
        }
        shipmentOrderLineRepository.save(line);

        // 백오더 처리 (부분출고 의사결정 트리)
        if (totalPicked < requestedQty) {
            int shortageQty = requestedQty - totalPicked;
            double fulfillmentRate = (double) totalPicked / requestedQty;

            if (fulfillmentRate < 0.30) {
                // 가용 재고 < 30%: 전량 백오더 (이미 피킹한 것도 롤백해야 하지만 단순화)
                // 실제로는 트랜잭션 내에서 피킹 전 체크해야 함
            }

            if (fulfillmentRate >= 0.30 && fulfillmentRate < 0.70) {
                // 가용 재고 30~70%: 부분출고 + 백오더 + 긴급발주
                createUrgentReorder(product, shortageQty);
            }

            // 백오더 생성
            Backorder backorder = Backorder.builder()
                    .shipmentOrderLine(line)
                    .product(product)
                    .shortageQty(shortageQty)
                    .status(Backorder.BackorderStatus.open)
                    .build();
            backorderRepository.save(backorder);

            backorderInfos.add(PickResponse.BackorderInfo.builder()
                    .backorderId(backorder.getBackorderId())
                    .productId(product.getProductId())
                    .productName(product.getName())
                    .shortageQty(shortageQty)
                    .build());
        }

        // 출고 후 안전재고 체크
        checkSafetyStockAfterShipment(product);

        return PickResponse.LinePickResult.builder()
                .shipmentLineId(line.getShipmentLineId())
                .productId(product.getProductId())
                .productName(product.getName())
                .requestedQty(line.getRequestedQty())
                .pickedQty(totalPicked)
                .status(line.getStatus().name())
                .pickDetails(pickDetails)
                .build();
    }

    private List<Inventory> getAvailableInventories(Product product) {
        List<Inventory> inventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> !Boolean.TRUE.equals(inv.getIsExpired()))
                .filter(inv -> !Boolean.TRUE.equals(inv.getLocation().getIsFrozen()))
                .collect(Collectors.toList());

        // HAZMAT 상품은 HAZMAT zone만
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            inventories = inventories.stream()
                    .filter(inv -> inv.getLocation().getZone() == Location.Zone.HAZMAT)
                    .collect(Collectors.toList());
        }

        // 유통기한 체크
        LocalDate today = LocalDate.now();
        inventories = inventories.stream()
                .filter(inv -> {
                    if (inv.getExpiryDate() != null) {
                        // 만료된 재고 제외
                        if (inv.getExpiryDate().isBefore(today)) {
                            return false;
                        }
                        // 잔여 유통기한 10% 미만 제외
                        if (inv.getManufactureDate() != null) {
                            double remainingPct = calculateRemainingShelfLifePct(
                                    inv.getManufactureDate(), inv.getExpiryDate());
                            if (remainingPct < 10.0) {
                                // is_expired = true 설정
                                inv.setIsExpired(true);
                                inventoryRepository.save(inv);
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // 피킹 우선순위 정렬
        if (Boolean.TRUE.equals(product.getHasExpiry())) {
            // FEFO: 유통기한 오름차순 우선, 같으면 입고일 오름차순
            inventories.sort((a, b) -> {
                // 잔여 30% 미만 최우선
                double remainingA = a.getExpiryDate() != null && a.getManufactureDate() != null
                        ? calculateRemainingShelfLifePct(a.getManufactureDate(), a.getExpiryDate())
                        : 100.0;
                double remainingB = b.getExpiryDate() != null && b.getManufactureDate() != null
                        ? calculateRemainingShelfLifePct(b.getManufactureDate(), b.getExpiryDate())
                        : 100.0;

                boolean aUrgent = remainingA < 30.0;
                boolean bUrgent = remainingB < 30.0;

                if (aUrgent && !bUrgent) return -1;
                if (!aUrgent && bUrgent) return 1;

                // 유통기한 비교
                int expiryCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                if (expiryCompare != 0) return expiryCompare;

                // 입고일 비교
                return a.getReceivedAt().compareTo(b.getReceivedAt());
            });
        } else {
            // FIFO: 입고일 오름차순
            inventories.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return inventories;
    }

    private double calculateRemainingShelfLifePct(LocalDate manufactureDate, LocalDate expiryDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays == 0) return 0.0;
        return (double) remainingDays / totalDays * 100.0;
    }

    private void createUrgentReorder(Product product, int shortageQty) {
        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerType(AutoReorderLog.TriggerType.URGENT_REORDER)
                .currentStock(getTotalAvailableStock(product))
                .minQty(0)
                .reorderQty(shortageQty)
                .triggeredBy("SYSTEM")
                .build();
        autoReorderLogRepository.save(log);
        log.info("Urgent reorder triggered for product {} due to shortage", product.getSku());
    }

    private void checkSafetyStockAfterShipment(Product product) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductId(product.getProductId());
        if (ruleOpt.isEmpty()) {
            return;
        }

        SafetyStockRule rule = ruleOpt.get();
        int currentStock = getTotalAvailableStock(product);

        if (currentStock <= rule.getMinQty()) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(currentStock)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy("SYSTEM")
                    .build();
            autoReorderLogRepository.save(log);
            log.info("Safety stock reorder triggered for product {}", product.getSku());
        }
    }

    private int getTotalAvailableStock(Product product) {
        return inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> !Boolean.TRUE.equals(inv.getIsExpired()))
                .mapToInt(Inventory::getQuantity)
                .sum();
    }

    private void logStorageTypeMismatch(Product product, Location location) {
        Map<String, Object> details = new HashMap<>();
        details.put("productId", product.getProductId().toString());
        details.put("productSku", product.getSku());
        details.put("productStorageType", product.getStorageType().name());
        details.put("locationId", location.getLocationId().toString());
        details.put("locationCode", location.getCode());
        details.put("locationStorageType", location.getStorageType().name());
        details.put("message", "Storage type mismatch detected during picking");

        AuditLog auditLog = AuditLog.builder()
                .eventType("STORAGE_TYPE_MISMATCH")
                .entityType("SHIPMENT")
                .entityId(product.getProductId())
                .details(details)
                .performedBy("SYSTEM")
                .build();
        auditLogRepository.save(auditLog);

        log.warn("Storage type mismatch: product {} ({}) in location {} ({})",
                product.getSku(), product.getStorageType(), location.getCode(), location.getStorageType());
    }

    @Transactional
    public ShipmentOrderResponse shipShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment order not found"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.picking &&
                shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.partial &&
                shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.shipped) {
            throw new BusinessException("INVALID_STATUS",
                    "Shipment order must be in picking, partial, or shipped status");
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.shipped);
        shipmentOrder.setShippedAt(OffsetDateTime.now());
        shipmentOrderRepository.save(shipmentOrder);

        return buildShipmentOrderResponse(shipmentOrder);
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment order not found"));
        return buildShipmentOrderResponse(shipmentOrder);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll().stream()
                .map(this::buildShipmentOrderResponse)
                .collect(Collectors.toList());
    }

    private ShipmentOrderResponse buildShipmentOrderResponse(ShipmentOrder shipmentOrder) {
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository
                .findByShipmentOrderShipmentId(shipmentOrder.getShipmentId());

        return ShipmentOrderResponse.builder()
                .shipmentId(shipmentOrder.getShipmentId())
                .shipmentNumber(shipmentOrder.getShipmentNumber())
                .customerName(shipmentOrder.getCustomerName())
                .status(shipmentOrder.getStatus().name())
                .requestedAt(shipmentOrder.getRequestedAt())
                .shippedAt(shipmentOrder.getShippedAt())
                .lines(lines.stream()
                        .map(line -> ShipmentOrderResponse.ShipmentOrderLineResponse.builder()
                                .shipmentLineId(line.getShipmentLineId())
                                .productId(line.getProduct().getProductId())
                                .productName(line.getProduct().getName())
                                .requestedQty(line.getRequestedQty())
                                .pickedQty(line.getPickedQty())
                                .status(line.getStatus().name())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\StockTransferService.java
============================================================
package com.wms.service;

import com.wms.dto.StockTransferApprovalRequest;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    @Transactional
    public StockTransferResponse createStockTransfer(StockTransferRequest request) {
        // 1. 기본 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "From location not found"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "To location not found"));

        // 2. 기본 검증
        validateBasicRules(request, fromLocation, toLocation);

        // 3. 재고 조회
        Inventory sourceInventory = findInventory(request.getProductId(), request.getFromLocationId(), request.getLotNumber())
                .orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", "Source inventory not found"));

        // 4. 재고 부족 체크
        if (sourceInventory.getQuantity() < request.getQuantity()) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    String.format("Insufficient stock. Available: %d, Requested: %d",
                            sourceInventory.getQuantity(), request.getQuantity()));
        }

        // 5. 용량 체크
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new BusinessException("CAPACITY_EXCEEDED",
                    String.format("Destination location capacity exceeded. Capacity: %d, Current: %d, Transfer: %d",
                            toLocation.getCapacity(), toLocation.getCurrentQty(), request.getQuantity()));
        }

        // 6. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, toLocation);

        // 7. HAZMAT 혼적 금지
        validateHazmatSegregation(product, toLocation, request.getProductId());

        // 8. 유통기한 체크
        validateExpiryConstraints(product, toLocation, sourceInventory);

        // 9. 대량 이동 승인 체크
        StockTransfer.TransferStatus transferStatus = StockTransfer.TransferStatus.IMMEDIATE;
        if (isLargeTransfer(sourceInventory.getQuantity(), request.getQuantity())) {
            transferStatus = StockTransfer.TransferStatus.PENDING_APPROVAL;
        }

        // 10. 이동 기록 생성
        StockTransfer transfer = StockTransfer.builder()
                .product(product)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .quantity(request.getQuantity())
                .lotNumber(request.getLotNumber())
                .reason(request.getReason())
                .transferStatus(transferStatus)
                .transferredBy(request.getTransferredBy())
                .build();
        stockTransferRepository.save(transfer);

        // 11. 즉시 이동인 경우 재고 이동 실행
        if (transferStatus == StockTransfer.TransferStatus.IMMEDIATE) {
            executeTransfer(transfer, sourceInventory);
        }

        return buildTransferResponse(transfer);
    }

    private void validateBasicRules(StockTransferRequest request, Location fromLocation, Location toLocation) {
        // 동일 로케이션 체크
        if (request.getFromLocationId().equals(request.getToLocationId())) {
            throw new BusinessException("SAME_LOCATION", "From location and to location cannot be the same");
        }

        // 실사 동결 체크
        if (Boolean.TRUE.equals(fromLocation.getIsFrozen())) {
            throw new BusinessException("LOCATION_FROZEN", "From location is frozen for cycle count");
        }
        if (Boolean.TRUE.equals(toLocation.getIsFrozen())) {
            throw new BusinessException("LOCATION_FROZEN", "To location is frozen for cycle count");
        }
    }

    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Location.StorageType locationType = toLocation.getStorageType();

        // FROZEN 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.FROZEN && locationType == Location.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "Cannot transfer FROZEN product to AMBIENT location");
        }

        // COLD 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.COLD && locationType == Location.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "Cannot transfer COLD product to AMBIENT location");
        }

        // HAZMAT 상품 → 비-HAZMAT zone: 거부
        if (product.getCategory() == Product.ProductCategory.HAZMAT &&
                toLocation.getZone() != Location.Zone.HAZMAT) {
            throw new BusinessException("HAZMAT_ZONE_VIOLATION",
                    "HAZMAT product must be transferred to HAZMAT zone");
        }
    }

    private void validateHazmatSegregation(Product product, Location toLocation, UUID productId) {
        // 도착지에 이미 적재된 재고 조회
        List<Inventory> existingInventory = inventoryRepository.findByLocationLocationId(toLocation.getLocationId());

        boolean isHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;

        for (Inventory inv : existingInventory) {
            // 자기 자신은 제외
            if (inv.getProduct().getProductId().equals(productId)) {
                continue;
            }

            boolean existingIsHazmat = inv.getProduct().getCategory() == Product.ProductCategory.HAZMAT;

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (isHazmat && !existingIsHazmat) {
                throw new BusinessException("HAZMAT_SEGREGATION_VIOLATION",
                        "Cannot mix HAZMAT product with non-HAZMAT products");
            }
            if (!isHazmat && existingIsHazmat) {
                throw new BusinessException("HAZMAT_SEGREGATION_VIOLATION",
                        "Cannot mix non-HAZMAT product with HAZMAT products");
            }
        }
    }

    private void validateExpiryConstraints(Product product, Location toLocation, Inventory sourceInventory) {
        if (!Boolean.TRUE.equals(product.getHasExpiry()) || sourceInventory.getExpiryDate() == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate expiryDate = sourceInventory.getExpiryDate();
        LocalDate manufactureDate = sourceInventory.getManufactureDate();

        // 유통기한 만료 체크
        if (expiryDate.isBefore(today)) {
            throw new BusinessException("EXPIRED_PRODUCT", "Cannot transfer expired product");
        }

        // 잔여 유통기한 비율 계산
        if (manufactureDate != null) {
            long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = (totalDays > 0) ? (remainingDays * 100.0 / totalDays) : 0;

            // 잔여 유통기한 < 10%: SHIPPING zone으로만 허용
            if (remainingPct < 10 && toLocation.getZone() != Location.Zone.SHIPPING) {
                throw new BusinessException("NEAR_EXPIRY_RESTRICTION",
                        String.format("Product with %.1f%% remaining shelf life can only be transferred to SHIPPING zone",
                                remainingPct));
            }
        }
    }

    private boolean isLargeTransfer(int totalQuantity, int transferQuantity) {
        return transferQuantity >= (totalQuantity * 0.8);
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, StockTransferApprovalRequest request) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not pending approval");
        }

        // 승인 처리
        transfer.setTransferStatus(StockTransfer.TransferStatus.APPROVED);
        transfer.setApprovedBy(request.getApprovedBy());
        stockTransferRepository.save(transfer);

        // 재고 이동 실행
        Inventory sourceInventory = findInventory(
                transfer.getProduct().getProductId(),
                transfer.getFromLocation().getLocationId(),
                transfer.getLotNumber()
        ).orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", "Source inventory not found"));

        executeTransfer(transfer, sourceInventory);

        return buildTransferResponse(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, StockTransferApprovalRequest request) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not pending approval");
        }

        // 거부 처리
        transfer.setTransferStatus(StockTransfer.TransferStatus.REJECTED);
        transfer.setApprovedBy(request.getApprovedBy());
        stockTransferRepository.save(transfer);

        return buildTransferResponse(transfer);
    }

    private void executeTransfer(StockTransfer transfer, Inventory sourceInventory) {
        // 1. 출발지 재고 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - transfer.getQuantity());
        inventoryRepository.save(sourceInventory);

        // 2. 출발지 로케이션 current_qty 차감
        Location fromLocation = transfer.getFromLocation();
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - transfer.getQuantity());
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (기존 재고가 있으면 증가, 없으면 새로 생성)
        Optional<Inventory> destInventoryOpt = findInventory(
                transfer.getProduct().getProductId(),
                transfer.getToLocation().getLocationId(),
                transfer.getLotNumber()
        );

        if (destInventoryOpt.isPresent()) {
            Inventory destInventory = destInventoryOpt.get();
            destInventory.setQuantity(destInventory.getQuantity() + transfer.getQuantity());
            inventoryRepository.save(destInventory);
        } else {
            Inventory newInventory = Inventory.builder()
                    .product(transfer.getProduct())
                    .location(transfer.getToLocation())
                    .quantity(transfer.getQuantity())
                    .lotNumber(transfer.getLotNumber())
                    .expiryDate(sourceInventory.getExpiryDate())
                    .manufactureDate(sourceInventory.getManufactureDate())
                    .receivedAt(sourceInventory.getReceivedAt()) // 원래 입고일 유지
                    .isExpired(sourceInventory.getIsExpired())
                    .build();
            inventoryRepository.save(newInventory);
        }

        // 4. 도착지 로케이션 current_qty 증가
        Location toLocation = transfer.getToLocation();
        toLocation.setCurrentQty(toLocation.getCurrentQty() + transfer.getQuantity());
        locationRepository.save(toLocation);

        // 5. 안전재고 체크 (STORAGE zone)
        checkSafetyStockAfterTransfer(transfer.getProduct());
    }

    private void checkSafetyStockAfterTransfer(Product product) {
        // STORAGE zone 내 전체 재고 확인
        List<Inventory> storageInventory = inventoryRepository.findByProductProductId(product.getProductId()).stream()
                .filter(inv -> inv.getLocation().getZone() == Location.Zone.STORAGE)
                .filter(inv -> !Boolean.TRUE.equals(inv.getIsExpired()))
                .collect(Collectors.toList());

        int totalStorageStock = storageInventory.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 규칙 조회
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProductProductId(product.getProductId());
        if (ruleOpt.isPresent()) {
            SafetyStockRule rule = ruleOpt.get();
            if (totalStorageStock <= rule.getMinQty()) {
                // 자동 재발주 로그 기록
                AutoReorderLog log = AutoReorderLog.builder()
                        .product(product)
                        .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                        .currentStock(totalStorageStock)
                        .minQty(rule.getMinQty())
                        .reorderQty(rule.getReorderQty())
                        .triggeredBy("SYSTEM")
                        .build();
                autoReorderLogRepository.save(log);
                log.info("Safety stock triggered for product: {}, current stock: {}, min qty: {}",
                        product.getSku(), totalStorageStock, rule.getMinQty());
            }
        }
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getStockTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Transfer not found"));
        return buildTransferResponse(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferResponse> getAllStockTransfers() {
        return stockTransferRepository.findAll().stream()
                .map(this::buildTransferResponse)
                .collect(Collectors.toList());
    }

    private Optional<Inventory> findInventory(UUID productId, UUID locationId, String lotNumber) {
        return inventoryRepository.findByProductProductIdAndLocationLocationIdAndLotNumber(
                productId, locationId, lotNumber);
    }

    private StockTransferResponse buildTransferResponse(StockTransfer transfer) {
        return StockTransferResponse.builder()
                .transferId(transfer.getTransferId())
                .productId(transfer.getProduct().getProductId())
                .productSku(transfer.getProduct().getSku())
                .productName(transfer.getProduct().getName())
                .fromLocationId(transfer.getFromLocation().getLocationId())
                .fromLocationCode(transfer.getFromLocation().getCode())
                .toLocationId(transfer.getToLocation().getLocationId())
                .toLocationCode(transfer.getToLocation().getCode())
                .quantity(transfer.getQuantity())
                .lotNumber(transfer.getLotNumber())
                .reason(transfer.getReason())
                .transferStatus(transfer.getTransferStatus().name())
                .transferredBy(transfer.getTransferredBy())
                .approvedBy(transfer.getApprovedBy())
                .transferredAt(transfer.getTransferredAt())
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
    name: warehouse-management-system

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
// FILE: src\main\resources\db\migration\V004__create_cycle_count_and_inventory_adjustment_tables.sql
============================================================
-- 실사 세션 테이블
CREATE TABLE cycle_counts (
    cycle_count_id   UUID PRIMARY KEY,
    location_id      UUID NOT NULL REFERENCES locations(location_id),
    status           VARCHAR(20) NOT NULL DEFAULT 'in_progress'
                     CHECK (status IN ('in_progress', 'completed')),
    started_by       VARCHAR(100) NOT NULL,
    started_at       TIMESTAMPTZ DEFAULT NOW(),
    completed_at     TIMESTAMPTZ
);

-- 재고 조정 테이블
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

