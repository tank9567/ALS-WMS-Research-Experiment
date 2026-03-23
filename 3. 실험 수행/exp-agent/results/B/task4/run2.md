# CLI Output

재고 실사 및 조정 기능 구현이 완료되었습니다.

## 구현 완료된 내용

### 1. Entity 클래스
- `CycleCount`: 실사 세션 관리
- `InventoryAdjustment`: 재고 조정 이력 관리

### 2. Repository
- `CycleCountRepository`: 실사 세션 조회
- `InventoryAdjustmentRepository`: 조정 이력 조회 및 연속 조정 감시
- `InventoryRepository`: 필요한 메서드 추가 (상품+로케이션별 재고 합산 등)

### 3. DTO
- `CycleCountRequest/Response`
- `InventoryAdjustmentRequest/Response`
- `ApprovalRequest`

### 4. Service (비즈니스 로직)
- **실사 시작**: 로케이션 동결 (is_frozen=true)
- **실사 완료**: 로케이션 동결 해제
- **재고 조정 생성**:
  - reason 필수 검증
  - 카테고리별 자동승인 임계치 (GENERAL 5%, FRESH 3%, HAZMAT 1%, HIGH_VALUE 2%)
  - system_qty=0 시 승인 필요
  - HIGH_VALUE는 차이가 0이 아니면 무조건 승인 필요
  - 연속 조정 감시 (7일 내 2회 이상 → 승인 격상, [연속조정감시] 태그)
  - 자동승인 시 즉시 재고 반영
- **승인/거부**: 승인 시 재고 반영, 거부 시 재고 변동 없음
- **HIGH_VALUE 전수 검증**: audit_logs 기록, 재실사 권고
- **안전재고 체크**: 조정 후 안전재고 미달 시 자동 재발주

### 5. Controller (REST API)
- `POST /api/v1/cycle-counts` - 실사 시작
- `POST /api/v1/cycle-counts/{id}/complete` - 실사 완료
- `POST /api/v1/inventory-adjustments` - 조정 생성
- `POST /api/v1/inventory-adjustments/{id}/approve` - 승인
- `POST /api/v1/inventory-adjustments/{id}/reject` - 거부
- `GET /api/v1/inventory-adjustments/{id}` - 상세 조회
- `GET /api/v1/inventory-adjustments` - 목록 조회

기존 입고/출고/이동 기능과 일관된 코드 스타일을 유지하며, 모든 요구사항을 구현했습니다.


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
// FILE: schema.sql
============================================================
-- WMS 데이터베이스 스키마 (PostgreSQL 15+)
-- UUID, TIMESTAMPTZ, NUMERIC 사용

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

-- 2. 로케이션
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

-- 3. 재고
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

-- 4. 공급업체
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

-- 6. 발주서
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

-- 7. 입고
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

-- 8. 출고 지시서
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

-- 9. 백오더
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

-- 10. 재고 이동
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

-- 11. 재고 조정
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

-- 12. 감사 로그
CREATE TABLE audit_logs (
    log_id           UUID PRIMARY KEY,
    event_type       VARCHAR(50) NOT NULL,
    entity_type      VARCHAR(50) NOT NULL,
    entity_id        UUID NOT NULL,
    details          JSONB NOT NULL,
    performed_by     VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 13. 안전재고 기준
CREATE TABLE safety_stock_rules (
    rule_id          UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products(product_id) UNIQUE,
    min_qty          INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty      INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 14. 자동 재발주 이력
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

-- 15. 계절 설정
CREATE TABLE seasonal_config (
    season_id        UUID PRIMARY KEY,
    season_name      VARCHAR(100) NOT NULL,
    start_date       DATE NOT NULL,
    end_date         DATE NOT NULL,
    multiplier       NUMERIC(3,2) NOT NULL DEFAULT 1.50,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ DEFAULT NOW()
);

-- 16. 실사 세션
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
CREATE INDEX idx_inventory_product_location ON inventory(product_id, location_id);
CREATE INDEX idx_inventory_expiry ON inventory(expiry_date) WHERE expiry_date IS NOT NULL;
CREATE INDEX idx_supplier_penalties_supplier_created ON supplier_penalties(supplier_id, created_at);
CREATE INDEX idx_po_lines_po ON purchase_order_lines(po_id);
CREATE INDEX idx_receipt_lines_receipt ON inbound_receipt_lines(receipt_id);
CREATE INDEX idx_seasonal_config_dates ON seasonal_config(start_date, end_date) WHERE is_active = true;


============================================================
// FILE: src\main\java\com\wms\controller\InboundReceiptController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.service.InboundReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
            @Valid @RequestBody InboundReceiptRequest request) {
        log.info("Creating inbound receipt for PO: {}", request.getPoId());
        InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        log.info("Confirming inbound receipt: {}", receiptId);
        InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(receiptId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
            @PathVariable("id") UUID receiptId,
            @RequestParam(required = false) String reason) {
        log.info("Rejecting inbound receipt: {}", receiptId);
        InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(receiptId, reason);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        log.info("Approving inbound receipt: {}", receiptId);
        InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(receiptId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(
            @PathVariable("id") UUID receiptId) {
        log.info("Getting inbound receipt: {}", receiptId);
        InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(receiptId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<InboundReceiptResponse>>> getAllInboundReceipts(
            Pageable pageable) {
        log.info("Getting all inbound receipts with pagination: {}", pageable);
        Page<InboundReceiptResponse> response = inboundReceiptService.getAllInboundReceipts(pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\InventoryAdjustmentController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.ApprovalRequest;
import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.service.InventoryAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    @PostMapping("/cycle-counts")
    public ResponseEntity<ApiResponse<CycleCountResponse>> startCycleCount(
            @Valid @RequestBody CycleCountRequest request) {
        log.info("Starting cycle count for location: {}", request.getLocationId());
        CycleCountResponse response = inventoryAdjustmentService.startCycleCount(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/cycle-counts/{id}/complete")
    public ResponseEntity<ApiResponse<CycleCountResponse>> completeCycleCount(
            @PathVariable("id") UUID cycleCountId) {
        log.info("Completing cycle count: {}", cycleCountId);
        CycleCountResponse response = inventoryAdjustmentService.completeCycleCount(cycleCountId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> createInventoryAdjustment(
            @Valid @RequestBody InventoryAdjustmentRequest request) {
        log.info("Creating inventory adjustment: product={}, location={}, actualQty={}",
                request.getProductId(), request.getLocationId(), request.getActualQty());
        InventoryAdjustmentResponse response = inventoryAdjustmentService.createInventoryAdjustment(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/inventory-adjustments/{id}/approve")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> approveInventoryAdjustment(
            @PathVariable("id") UUID adjustmentId,
            @Valid @RequestBody ApprovalRequest request) {
        log.info("Approving inventory adjustment: {}", adjustmentId);
        InventoryAdjustmentResponse response = inventoryAdjustmentService.approveInventoryAdjustment(adjustmentId, request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/inventory-adjustments/{id}/reject")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> rejectInventoryAdjustment(
            @PathVariable("id") UUID adjustmentId,
            @Valid @RequestBody ApprovalRequest request) {
        log.info("Rejecting inventory adjustment: {}", adjustmentId);
        InventoryAdjustmentResponse response = inventoryAdjustmentService.rejectInventoryAdjustment(adjustmentId, request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/inventory-adjustments/{id}")
    public ResponseEntity<ApiResponse<InventoryAdjustmentResponse>> getInventoryAdjustment(
            @PathVariable("id") UUID adjustmentId) {
        log.info("Getting inventory adjustment: {}", adjustmentId);
        InventoryAdjustmentResponse response = inventoryAdjustmentService.getInventoryAdjustment(adjustmentId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/inventory-adjustments")
    public ResponseEntity<ApiResponse<Page<InventoryAdjustmentResponse>>> getAllInventoryAdjustments(
            Pageable pageable) {
        log.info("Getting all inventory adjustments with pagination: {}", pageable);
        Page<InventoryAdjustmentResponse> response = inventoryAdjustmentService.getAllInventoryAdjustments(pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\ShipmentOrderController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.PickingResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.service.ShipmentOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipment-orders")
@RequiredArgsConstructor
public class ShipmentOrderController {

    private final ShipmentOrderService shipmentOrderService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> createShipmentOrder(
            @RequestBody ShipmentOrderRequest request
    ) {
        ShipmentOrderResponse response = shipmentOrderService.createShipmentOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{shipmentId}/pick")
    public ResponseEntity<ApiResponse<PickingResponse>> pickShipmentOrder(
            @PathVariable UUID shipmentId
    ) {
        PickingResponse response = shipmentOrderService.pickShipmentOrder(shipmentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{shipmentId}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> shipShipmentOrder(
            @PathVariable UUID shipmentId
    ) {
        ShipmentOrderResponse response = shipmentOrderService.shipShipmentOrder(shipmentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{shipmentId}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(
            @PathVariable UUID shipmentId
    ) {
        ShipmentOrderResponse response = shipmentOrderService.getShipmentOrder(shipmentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ShipmentOrderResponse>>> getShipmentOrders(
            Pageable pageable
    ) {
        Page<ShipmentOrderResponse> response = shipmentOrderService.getShipmentOrders(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> createStockTransfer(
            @Valid @RequestBody StockTransferRequest request) {
        log.info("Creating stock transfer: product={}, from={}, to={}, qty={}",
                request.getProductId(), request.getFromLocationId(), request.getToLocationId(), request.getQuantity());
        StockTransferResponse response = stockTransferService.createStockTransfer(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveStockTransfer(
            @PathVariable("id") UUID transferId,
            @RequestParam String approvedBy) {
        log.info("Approving stock transfer: {}", transferId);
        StockTransferResponse response = stockTransferService.approveStockTransfer(transferId, approvedBy);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectStockTransfer(
            @PathVariable("id") UUID transferId,
            @RequestParam String rejectedBy,
            @RequestParam(required = false) String reason) {
        log.info("Rejecting stock transfer: {}", transferId);
        StockTransferResponse response = stockTransferService.rejectStockTransfer(transferId, rejectedBy, reason);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getStockTransfer(
            @PathVariable("id") UUID transferId) {
        log.info("Getting stock transfer: {}", transferId);
        StockTransferResponse response = stockTransferService.getStockTransfer(transferId);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<StockTransferResponse>>> getAllStockTransfers(
            Pageable pageable) {
        log.info("Getting all stock transfers with pagination: {}", pageable);
        Page<StockTransferResponse> response = stockTransferService.getAllStockTransfers(pageable);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(response));
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
    private ErrorDetail error;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String code;
        private String message;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ApprovalRequest.java
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
public class ApprovalRequest {
    private String approvedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountRequest.java
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
public class CycleCountRequest {
    private UUID locationId;
    private String startedBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\CycleCountResponse.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
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
    private Instant startedAt;
    private Instant completedAt;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptRequest {

    @NotNull(message = "PO ID is required")
    private UUID poId;

    @NotBlank(message = "Received by is required")
    private String receivedBy;

    @NotEmpty(message = "Receipt lines are required")
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundReceiptResponse {

    private UUID receiptId;
    private UUID poId;
    private String poNumber;
    private String status;
    private String receivedBy;
    private Instant receivedAt;
    private Instant confirmedAt;
    private List<InboundReceiptLineResponse> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
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
    private Instant createdAt;
    private Instant approvedAt;
}


============================================================
// FILE: src\main\java\com\wms\dto\PickingResponse.java
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
public class PickingResponse {

    private UUID shipmentId;
    private String shipmentNumber;
    private String status;
    private List<LinePickingDetail> lineDetails;
    private List<BackorderDetail> backorders;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinePickingDetail {
        private UUID shipmentLineId;
        private UUID productId;
        private String productSku;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
        private List<LocationPickDetail> pickDetails;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationPickDetail {
        private UUID locationId;
        private String locationCode;
        private Integer pickedQty;
        private String lotNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackorderDetail {
        private UUID backorderId;
        private UUID productId;
        private String productSku;
        private Integer shortageQty;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderRequest.java
============================================================
package com.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentOrderRequest {

    private String shipmentNumber;
    private String customerName;
    private Instant requestedAt;
    private List<ShipmentOrderLineRequest> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipmentOrderLineRequest {
        private UUID productId;
        private Integer requestedQty;
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.ShipmentOrder;
import com.wms.entity.ShipmentOrderLine;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentOrderResponse {

    private UUID shipmentId;
    private String shipmentNumber;
    private String customerName;
    private String status;
    private Instant requestedAt;
    private Instant shippedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ShipmentOrderLineResponse> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipmentOrderLineResponse {
        private UUID shipmentLineId;
        private UUID productId;
        private String productSku;
        private String productName;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
    }

    public static ShipmentOrderResponse from(ShipmentOrder shipmentOrder) {
        return ShipmentOrderResponse.builder()
                .shipmentId(shipmentOrder.getShipmentId())
                .shipmentNumber(shipmentOrder.getShipmentNumber())
                .customerName(shipmentOrder.getCustomerName())
                .status(shipmentOrder.getStatus().name())
                .requestedAt(shipmentOrder.getRequestedAt())
                .shippedAt(shipmentOrder.getShippedAt())
                .createdAt(shipmentOrder.getCreatedAt())
                .updatedAt(shipmentOrder.getUpdatedAt())
                .lines(shipmentOrder.getLines().stream()
                        .map(ShipmentOrderResponse::fromLine)
                        .collect(Collectors.toList()))
                .build();
    }

    private static ShipmentOrderLineResponse fromLine(ShipmentOrderLine line) {
        return ShipmentOrderLineResponse.builder()
                .shipmentLineId(line.getShipmentLineId())
                .productId(line.getProduct().getProductId())
                .productSku(line.getProduct().getSku())
                .productName(line.getProduct().getName())
                .requestedQty(line.getRequestedQty())
                .pickedQty(line.getPickedQty())
                .status(line.getStatus().name())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferRequest.java
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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private Instant transferredAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\AuditLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (logId == null) {
            logId = UUID.randomUUID();
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\AutoReorderLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auto_reorder_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (reorderLogId == null) {
            reorderLogId = UUID.randomUUID();
        }
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backorders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private BackorderStatus status = BackorderStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @PrePersist
    public void prePersist() {
        if (backorderId == null) {
            backorderId = UUID.randomUUID();
        }
    }

    public enum BackorderStatus {
        OPEN, FULFILLED, CANCELLED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\CycleCount.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cycle_counts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @CreationTimestamp
    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    public void prePersist() {
        if (cycleCountId == null) {
            cycleCountId = UUID.randomUUID();
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private ReceiptStatus status = ReceiptStatus.INSPECTING;

    @Column(name = "received_by", nullable = false, length = 100)
    private String receivedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @OneToMany(mappedBy = "inboundReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InboundReceiptLine> lines = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (receiptId == null) {
            receiptId = UUID.randomUUID();
        }
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    public enum ReceiptStatus {
        INSPECTING, PENDING_APPROVAL, CONFIRMED, REJECTED
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory")
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
    private Instant receivedAt;

    @Column(name = "is_expired", nullable = false)
    private Boolean isExpired = false;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (inventoryId == null) {
            inventoryId = UUID.randomUUID();
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InventoryAdjustment.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_adjustments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @PrePersist
    public void prePersist() {
        if (adjustmentId == null) {
            adjustmentId = UUID.randomUUID();
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
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

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (locationId == null) {
            locationId = UUID.randomUUID();
        }
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
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

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (productId == null) {
            productId = UUID.randomUUID();
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private PoStatus status = PoStatus.PENDING;

    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (poId == null) {
            poId = UUID.randomUUID();
        }
    }

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines")
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
    public void prePersist() {
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "safety_stock_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyStockRule {

    @Id
    @Column(name = "rule_id")
    private UUID ruleId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (ruleId == null) {
            ruleId = UUID.randomUUID();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
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
import java.time.Instant;
import java.time.LocalDate;
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
    private BigDecimal multiplier = BigDecimal.valueOf(1.50);

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (seasonId == null) {
            seasonId = UUID.randomUUID();
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shipment_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentOrder {

    @Id
    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "shipment_number", nullable = false, unique = true, length = 30)
    private String shipmentNumber;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "shipmentOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShipmentOrderLine> lines = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (shipmentId == null) {
            shipmentId = UUID.randomUUID();
        }
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public enum ShipmentStatus {
        PENDING, PICKING, PARTIAL, SHIPPED, CANCELLED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrderLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "shipment_order_lines")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private LineStatus status = LineStatus.PENDING;

    @PrePersist
    public void prePersist() {
        if (shipmentLineId == null) {
            shipmentLineId = UUID.randomUUID();
        }
    }

    public enum LineStatus {
        PENDING, PICKED, PARTIAL, BACKORDERED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\StockTransfer.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @CreationTimestamp
    @Column(name = "transferred_at")
    private Instant transferredAt;

    @PrePersist
    public void prePersist() {
        if (transferId == null) {
            transferId = UUID.randomUUID();
        }
        if (transferredAt == null) {
            transferredAt = Instant.now();
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
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
    private SupplierStatus status = SupplierStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (supplierId == null) {
            supplierId = UUID.randomUUID();
        }
    }

    public enum SupplierStatus {
        ACTIVE, HOLD, INACTIVE
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

import java.time.Instant;
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

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

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
// FILE: src\main\java\com\wms\exception\GlobalExceptionHandler.java
============================================================
package com.wms.exception;

import com.wms.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        HttpStatus status = determineHttpStatus(e.getCode());
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred: " + e.getMessage()));
    }

    private HttpStatus determineHttpStatus(String code) {
        return switch (code) {
            case "PO_NOT_FOUND", "RECEIPT_NOT_FOUND", "PRODUCT_NOT_FOUND", "LOCATION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "MISSING_EXPIRY_DATE", "MISSING_MANUFACTURE_DATE", "INVALID_EXPIRY_DATE" -> HttpStatus.BAD_REQUEST;
            case "OVER_DELIVERY", "SHORT_SHELF_LIFE", "STORAGE_TYPE_INCOMPATIBLE", "LOCATION_FROZEN" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
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
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {

    @Query("SELECT cc FROM CycleCount cc WHERE cc.location.locationId = :locationId AND cc.status = 'IN_PROGRESS'")
    Optional<CycleCount> findInProgressByLocationId(@Param("locationId") UUID locationId);
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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID> {

    @Query("SELECT ir FROM InboundReceipt ir LEFT JOIN FETCH ir.lines WHERE ir.receiptId = :receiptId")
    Optional<InboundReceipt> findByIdWithLines(@Param("receiptId") UUID receiptId);
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.product.productId = :productId AND ia.location.locationId = :locationId AND ia.createdAt >= :since")
    List<InventoryAdjustment> findRecentAdjustments(@Param("productId") UUID productId, @Param("locationId") UUID locationId, @Param("since") Instant since);

    @Query("SELECT ia FROM InventoryAdjustment ia WHERE ia.approvalStatus = 'PENDING'")
    List<InventoryAdjustment> findAllPendingAdjustments();
}


============================================================
// FILE: src\main\java\com\wms\repository\InventoryRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Inventory;
import com.wms.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByProductProductIdAndLocationLocationIdAndLotNumber(
        UUID productId, UUID locationId, String lotNumber);

    List<Inventory> findByLocationLocationId(UUID locationId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i " +
            "WHERE i.product.productId = :productId " +
            "AND i.location.zone = :zone " +
            "AND i.isExpired = false")
    int sumQuantityByProductAndZone(@Param("productId") UUID productId, @Param("zone") Location.Zone zone);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i " +
            "WHERE i.product.productId = :productId " +
            "AND i.location.locationId = :locationId")
    int sumQuantityByProductAndLocation(@Param("productId") UUID productId, @Param("locationId") UUID locationId);

    @Query("SELECT i FROM Inventory i " +
            "WHERE i.product.productId = :productId " +
            "AND i.location.locationId = :locationId")
    List<Inventory> findByProductAndLocation(@Param("productId") UUID productId, @Param("locationId") UUID locationId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM Inventory i " +
            "WHERE i.product.productId = :productId " +
            "AND i.isExpired = false")
    int sumAvailableQuantityByProduct(@Param("productId") UUID productId);
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

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    Optional<PurchaseOrderLine> findByPurchaseOrderPoIdAndProductProductId(UUID poId, UUID productId);
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

    @Query("SELECT po FROM PurchaseOrder po LEFT JOIN FETCH po.lines WHERE po.poId = :poId")
    Optional<PurchaseOrder> findByIdWithLines(@Param("poId") UUID poId);

    List<PurchaseOrder> findBySupplierSupplierIdAndStatus(UUID supplierId, PurchaseOrder.PoStatus status);
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
    Optional<SafetyStockRule> findByProductProductId(UUID productId);
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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipmentOrderRepository extends JpaRepository<ShipmentOrder, UUID> {

    @Query("SELECT s FROM ShipmentOrder s LEFT JOIN FETCH s.lines WHERE s.shipmentId = :shipmentId")
    Optional<ShipmentOrder> findByIdWithLines(@Param("shipmentId") UUID shipmentId);

    Optional<ShipmentOrder> findByShipmentNumber(String shipmentNumber);
}


============================================================
// FILE: src\main\java\com\wms\repository\StockTransferRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    @Query("SELECT st FROM StockTransfer st " +
            "LEFT JOIN FETCH st.product p " +
            "LEFT JOIN FETCH st.fromLocation fl " +
            "LEFT JOIN FETCH st.toLocation tl " +
            "WHERE st.transferId = :transferId")
    Optional<StockTransfer> findByIdWithDetails(@Param("transferId") UUID transferId);

    @Query("SELECT st FROM StockTransfer st " +
            "LEFT JOIN FETCH st.product p " +
            "LEFT JOIN FETCH st.fromLocation fl " +
            "LEFT JOIN FETCH st.toLocation tl " +
            "WHERE st.transferStatus = :status")
    List<StockTransfer> findByTransferStatusWithDetails(@Param("status") StockTransfer.TransferStatus status);
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

import java.time.Instant;
import java.util.UUID;

@Repository
public interface SupplierPenaltyRepository extends JpaRepository<SupplierPenalty, UUID> {

    @Query("SELECT COUNT(sp) FROM SupplierPenalty sp WHERE sp.supplier.supplierId = :supplierId AND sp.createdAt >= :since")
    long countBySupplierAndCreatedAtAfter(@Param("supplierId") UUID supplierId, @Param("since") Instant since);
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SupplierRepository supplierRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회 및 검증
        PurchaseOrder po = purchaseOrderRepository.findByIdWithLines(request.getPoId())
                .orElseThrow(() -> new BusinessException("PO_NOT_FOUND", "Purchase order not found"));

        // 2. 각 라인별 검증
        List<InboundReceiptLine> receiptLines = new ArrayList<>();
        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            Location location = locationRepository.findById(lineReq.getLocationId())
                    .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

            // 실사 동결 체크
            if (location.getIsFrozen()) {
                throw new BusinessException("LOCATION_FROZEN", "Location is frozen for cycle count");
            }

            // 보관 유형 호환성 체크
            validateStorageTypeCompatibility(product, location);

            // 유통기한 관리 상품 검증
            if (product.getHasExpiry()) {
                validateExpiryDateRequirements(product, lineReq);
            }

            InboundReceiptLine line = InboundReceiptLine.builder()
                    .product(product)
                    .location(location)
                    .quantity(lineReq.getQuantity())
                    .lotNumber(lineReq.getLotNumber())
                    .expiryDate(lineReq.getExpiryDate())
                    .manufactureDate(lineReq.getManufactureDate())
                    .build();
            receiptLines.add(line);
        }

        // 3. 입고 생성 (inspecting 상태)
        InboundReceipt receipt = InboundReceipt.builder()
                .purchaseOrder(po)
                .status(InboundReceipt.ReceiptStatus.INSPECTING)
                .receivedBy(request.getReceivedBy())
                .receivedAt(Instant.now())
                .lines(receiptLines)
                .build();

        receiptLines.forEach(line -> line.setInboundReceipt(receipt));

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);
        log.info("Inbound receipt created with ID: {}", savedReceipt.getReceiptId());

        return convertToResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findByIdWithLines(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.INSPECTING &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Receipt is not in inspecting or pending_approval status");
        }

        PurchaseOrder po = receipt.getPurchaseOrder();

        // 1. 각 라인별 초과입고 검증 및 유통기한 검증
        boolean requiresApproval = false;
        for (InboundReceiptLine line : receipt.getLines()) {
            Product product = line.getProduct();

            // 초과입고 검증
            PurchaseOrderLine poLine = purchaseOrderLineRepository
                    .findByPurchaseOrderPoIdAndProductProductId(po.getPoId(), product.getProductId())
                    .orElseThrow(() -> new BusinessException("PO_LINE_NOT_FOUND", "PO line not found for product"));

            int totalReceivedQty = poLine.getReceivedQty() + line.getQuantity();
            double allowedOverdeliveryRate = calculateAllowedOverdeliveryRate(product.getCategory(), po.getPoType());
            int maxAllowedQty = (int) (poLine.getOrderedQty() * (1 + allowedOverdeliveryRate / 100.0));

            if (totalReceivedQty > maxAllowedQty) {
                // 초과입고 거부 및 페널티 부과
                recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY,
                        "Over delivery: " + totalReceivedQty + " > " + maxAllowedQty, po.getPoId());
                throw new BusinessException("OVER_DELIVERY",
                        String.format("Over delivery detected. Allowed: %d, Received: %d", maxAllowedQty, totalReceivedQty));
            }

            // 유통기한 잔여율 검증
            if (product.getHasExpiry() && line.getExpiryDate() != null && line.getManufactureDate() != null) {
                double remainingShelfLifePct = calculateRemainingShelfLife(line.getExpiryDate(), line.getManufactureDate());

                if (remainingShelfLifePct < product.getMinRemainingShelfLifePct()) {
                    // 유통기한 부족 거부 및 페널티 부과
                    recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                            "Remaining shelf life too short: " + remainingShelfLifePct + "%", po.getPoId());
                    throw new BusinessException("SHORT_SHELF_LIFE",
                            String.format("Remaining shelf life too short: %.2f%% < %d%%",
                                    remainingShelfLifePct, product.getMinRemainingShelfLifePct()));
                }

                if (remainingShelfLifePct >= 30 && remainingShelfLifePct < 50) {
                    requiresApproval = true;
                }
            }
        }

        // 2. 승인 필요 여부 확인
        if (requiresApproval && receipt.getStatus() == InboundReceipt.ReceiptStatus.INSPECTING) {
            receipt.setStatus(InboundReceipt.ReceiptStatus.PENDING_APPROVAL);
            InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);
            log.info("Inbound receipt {} requires approval due to shelf life warning", receiptId);
            return convertToResponse(savedReceipt);
        }

        // 3. 재고 반영
        for (InboundReceiptLine line : receipt.getLines()) {
            updateInventory(line);
            updatePurchaseOrderLine(po.getPoId(), line.getProduct().getProductId(), line.getQuantity());
        }

        // 4. PO 상태 업데이트
        updatePurchaseOrderStatus(po);

        // 5. 입고 확정
        receipt.setStatus(InboundReceipt.ReceiptStatus.CONFIRMED);
        receipt.setConfirmedAt(Instant.now());
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        log.info("Inbound receipt {} confirmed", receiptId);
        return convertToResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findByIdWithLines(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.INSPECTING &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Receipt is not in inspecting or pending_approval status");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.REJECTED);
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        log.info("Inbound receipt {} rejected. Reason: {}", receiptId, reason);
        return convertToResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findByIdWithLines(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Receipt is not in pending_approval status");
        }

        // 재고 반영
        PurchaseOrder po = receipt.getPurchaseOrder();
        for (InboundReceiptLine line : receipt.getLines()) {
            updateInventory(line);
            updatePurchaseOrderLine(po.getPoId(), line.getProduct().getProductId(), line.getQuantity());
        }

        // PO 상태 업데이트
        updatePurchaseOrderStatus(po);

        // 입고 확정
        receipt.setStatus(InboundReceipt.ReceiptStatus.CONFIRMED);
        receipt.setConfirmedAt(Instant.now());
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        log.info("Inbound receipt {} approved and confirmed", receiptId);
        return convertToResponse(savedReceipt);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findByIdWithLines(receiptId)
                .orElseThrow(() -> new BusinessException("RECEIPT_NOT_FOUND", "Inbound receipt not found"));
        return convertToResponse(receipt);
    }

    @Transactional(readOnly = true)
    public Page<InboundReceiptResponse> getAllInboundReceipts(Pageable pageable) {
        return inboundReceiptRepository.findAll(pageable)
                .map(this::convertToResponse);
    }

    // ===== Helper Methods =====

    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // HAZMAT은 HAZMAT zone만
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                        "HAZMAT products can only be stored in HAZMAT zone");
            }
        }

        // FROZEN은 FROZEN만
        if (productType == Product.StorageType.FROZEN && locationType != Product.StorageType.FROZEN) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "FROZEN products can only be stored in FROZEN locations");
        }

        // COLD는 COLD 또는 FROZEN
        if (productType == Product.StorageType.COLD &&
            locationType != Product.StorageType.COLD && locationType != Product.StorageType.FROZEN) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "COLD products can only be stored in COLD or FROZEN locations");
        }

        // AMBIENT는 AMBIENT만
        if (productType == Product.StorageType.AMBIENT && locationType != Product.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "AMBIENT products can only be stored in AMBIENT locations");
        }
    }

    private void validateExpiryDateRequirements(Product product, InboundReceiptRequest.InboundReceiptLineRequest lineReq) {
        if (lineReq.getExpiryDate() == null) {
            throw new BusinessException("MISSING_EXPIRY_DATE", "Expiry date is required for products with expiry management");
        }

        if (product.getManufactureDateRequired() && lineReq.getManufactureDate() == null) {
            throw new BusinessException("MISSING_MANUFACTURE_DATE", "Manufacture date is required for this product");
        }

        if (lineReq.getExpiryDate() != null && lineReq.getManufactureDate() != null) {
            if (lineReq.getExpiryDate().isBefore(lineReq.getManufactureDate())) {
                throw new BusinessException("INVALID_EXPIRY_DATE", "Expiry date cannot be before manufacture date");
            }
        }
    }

    private double calculateAllowedOverdeliveryRate(Product.ProductCategory category, PurchaseOrder.PoType poType) {
        // 카테고리별 기본 허용률
        double baseRate = switch (category) {
            case GENERAL -> 10.0;
            case FRESH -> 5.0;
            case HAZMAT -> 0.0;
            case HIGH_VALUE -> 3.0;
        };

        // HAZMAT은 항상 0%
        if (category == Product.ProductCategory.HAZMAT) {
            return 0.0;
        }

        // 발주 유형별 가중치
        double poTypeMultiplier = switch (poType) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };

        double rateWithPoType = baseRate * poTypeMultiplier;

        // 성수기 가중치
        LocalDate today = LocalDate.now();
        SeasonalConfig seasonalConfig = seasonalConfigRepository.findActiveSeasonByDate(today).orElse(null);

        if (seasonalConfig != null) {
            double seasonalMultiplier = seasonalConfig.getMultiplier().doubleValue();
            rateWithPoType *= seasonalMultiplier;
        }

        return rateWithPoType;
    }

    private double calculateRemainingShelfLife(LocalDate expiryDate, LocalDate manufactureDate) {
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays == 0) {
            return 0.0;
        }

        return (remainingDays * 100.0) / totalDays;
    }

    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType penaltyType,
                                       String description, UUID poId) {
        SupplierPenalty penalty = SupplierPenalty.builder()
                .supplier(supplier)
                .penaltyType(penaltyType)
                .description(description)
                .poId(poId)
                .build();
        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 3회 이상 체크
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long penaltyCount = supplierPenaltyRepository.countBySupplierAndCreatedAtAfter(
                supplier.getSupplierId(), thirtyDaysAgo);

        if (penaltyCount >= 3) {
            // 해당 공급업체의 모든 pending PO를 hold로 변경
            List<PurchaseOrder> pendingPOs = purchaseOrderRepository
                    .findBySupplierSupplierIdAndStatus(supplier.getSupplierId(), PurchaseOrder.PoStatus.PENDING);

            for (PurchaseOrder po : pendingPOs) {
                po.setStatus(PurchaseOrder.PoStatus.HOLD);
            }
            purchaseOrderRepository.saveAll(pendingPOs);

            // 공급업체 상태도 hold로 변경
            supplier.setStatus(Supplier.SupplierStatus.HOLD);
            supplierRepository.save(supplier);

            log.warn("Supplier {} has been put on hold due to 3+ penalties in 30 days", supplier.getSupplierId());
        }
    }

    private void updateInventory(InboundReceiptLine line) {
        Product product = line.getProduct();
        Location location = line.getLocation();

        // 동일 product + location + lot_number 조합 찾기
        Inventory inventory = inventoryRepository
                .findByProductProductIdAndLocationLocationIdAndLotNumber(
                        product.getProductId(), location.getLocationId(), line.getLotNumber())
                .orElse(null);

        if (inventory != null) {
            // 기존 재고 증가
            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
            inventoryRepository.save(inventory);
        } else {
            // 새 재고 생성
            Inventory newInventory = Inventory.builder()
                    .product(product)
                    .location(location)
                    .quantity(line.getQuantity())
                    .lotNumber(line.getLotNumber())
                    .expiryDate(line.getExpiryDate())
                    .manufactureDate(line.getManufactureDate())
                    .receivedAt(Instant.now())
                    .isExpired(false)
                    .build();
            inventoryRepository.save(newInventory);
        }

        // Location의 current_qty 증가
        location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
        locationRepository.save(location);
    }

    private void updatePurchaseOrderLine(UUID poId, UUID productId, int receivedQty) {
        PurchaseOrderLine poLine = purchaseOrderLineRepository
                .findByPurchaseOrderPoIdAndProductProductId(poId, productId)
                .orElseThrow(() -> new BusinessException("PO_LINE_NOT_FOUND", "PO line not found"));

        poLine.setReceivedQty(poLine.getReceivedQty() + receivedQty);
        purchaseOrderLineRepository.save(poLine);
    }

    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        boolean allFulfilled = true;
        boolean anyFulfilled = false;

        for (PurchaseOrderLine line : po.getLines()) {
            if (line.getReceivedQty() < line.getOrderedQty()) {
                allFulfilled = false;
            }
            if (line.getReceivedQty() > 0) {
                anyFulfilled = true;
            }
        }

        if (allFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.COMPLETED);
        } else if (anyFulfilled) {
            po.setStatus(PurchaseOrder.PoStatus.PARTIAL);
        }

        purchaseOrderRepository.save(po);
    }

    private InboundReceiptResponse convertToResponse(InboundReceipt receipt) {
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
                .lines(lineResponses)
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\service\InventoryAdjustmentService.java
============================================================
package com.wms.service;

import com.wms.dto.ApprovalRequest;
import com.wms.dto.CycleCountRequest;
import com.wms.dto.CycleCountResponse;
import com.wms.dto.InventoryAdjustmentRequest;
import com.wms.dto.InventoryAdjustmentResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final CycleCountRepository cycleCountRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;
    private final AuditLogRepository auditLogRepository;

    // 카테고리별 자동승인 임계치 (%)
    private static final Map<Product.ProductCategory, Double> AUTO_APPROVAL_THRESHOLDS = new HashMap<>();

    static {
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.GENERAL, 5.0);
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.FRESH, 3.0);
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.HAZMAT, 1.0);
        AUTO_APPROVAL_THRESHOLDS.put(Product.ProductCategory.HIGH_VALUE, 2.0);
    }

    @Transactional
    public CycleCountResponse startCycleCount(CycleCountRequest request) {
        // 1. 로케이션 조회
        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        // 2. 이미 진행 중인 실사가 있는지 확인
        cycleCountRepository.findInProgressByLocationId(location.getLocationId())
                .ifPresent(cc -> {
                    throw new BusinessException("CYCLE_COUNT_IN_PROGRESS",
                            "Cycle count is already in progress for this location");
                });

        // 3. 로케이션 동결
        location.setIsFrozen(true);
        locationRepository.save(location);

        // 4. 실사 세션 생성
        CycleCount cycleCount = CycleCount.builder()
                .location(location)
                .status(CycleCount.CycleCountStatus.IN_PROGRESS)
                .startedBy(request.getStartedBy())
                .startedAt(Instant.now())
                .build();

        CycleCount savedCycleCount = cycleCountRepository.save(cycleCount);
        log.info("Cycle count started for location {} by {}", location.getCode(), request.getStartedBy());

        return convertToCycleCountResponse(savedCycleCount);
    }

    @Transactional
    public CycleCountResponse completeCycleCount(UUID cycleCountId) {
        // 1. 실사 세션 조회
        CycleCount cycleCount = cycleCountRepository.findById(cycleCountId)
                .orElseThrow(() -> new BusinessException("CYCLE_COUNT_NOT_FOUND", "Cycle count not found"));

        if (cycleCount.getStatus() != CycleCount.CycleCountStatus.IN_PROGRESS) {
            throw new BusinessException("INVALID_STATUS", "Cycle count is not in progress");
        }

        // 2. 로케이션 동결 해제
        Location location = cycleCount.getLocation();
        location.setIsFrozen(false);
        locationRepository.save(location);

        // 3. 실사 완료
        cycleCount.setStatus(CycleCount.CycleCountStatus.COMPLETED);
        cycleCount.setCompletedAt(Instant.now());
        CycleCount savedCycleCount = cycleCountRepository.save(cycleCount);

        log.info("Cycle count completed for location {}", location.getCode());
        return convertToCycleCountResponse(savedCycleCount);
    }

    @Transactional
    public InventoryAdjustmentResponse createInventoryAdjustment(InventoryAdjustmentRequest request) {
        // 1. 필수 필드 검증
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new BusinessException("REASON_REQUIRED", "Adjustment reason is required");
        }

        // 2. 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found"));

        // 3. 시스템 재고 조회
        int systemQty = inventoryRepository.sumQuantityByProductAndLocation(
                product.getProductId(), location.getLocationId());

        int actualQty = request.getActualQty();
        int difference = actualQty - systemQty;

        // 4. 재고가 음수가 되는지 체크
        if (actualQty < 0) {
            throw new BusinessException("INVALID_QUANTITY", "Actual quantity cannot be negative");
        }

        // 5. 차이 비율 계산
        double diffRatio = 0.0;
        if (systemQty > 0) {
            diffRatio = Math.abs(difference) * 100.0 / systemQty;
        }

        // 6. 자동 승인 여부 결정
        boolean requiresApproval = false;
        InventoryAdjustment.ApprovalStatus approvalStatus = InventoryAdjustment.ApprovalStatus.AUTO_APPROVED;
        String reason = request.getReason();

        // (a) system_qty가 0인 경우 무조건 승인 필요
        if (systemQty == 0 && actualQty > 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // (b) HIGH_VALUE는 차이가 0이 아니면 무조건 승인 필요
        else if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            requiresApproval = true;
            approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
        }
        // (c) 연속 조정 감시 (최근 7일 내 2회 이상)
        else {
            Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
            List<InventoryAdjustment> recentAdjustments = inventoryAdjustmentRepository
                    .findRecentAdjustments(product.getProductId(), location.getLocationId(), sevenDaysAgo);

            if (recentAdjustments.size() >= 2) {
                requiresApproval = true;
                approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
                reason = "[연속조정감시] " + reason;
                log.warn("Consecutive adjustments detected for product {} at location {}",
                        product.getSku(), location.getCode());
            }
            // (d) 카테고리별 임계치 체크
            else {
                double threshold = AUTO_APPROVAL_THRESHOLDS.getOrDefault(product.getCategory(), 5.0);
                if (diffRatio > threshold) {
                    requiresApproval = true;
                    approvalStatus = InventoryAdjustment.ApprovalStatus.PENDING;
                }
            }
        }

        // 7. 재고 조정 생성
        InventoryAdjustment adjustment = InventoryAdjustment.builder()
                .product(product)
                .location(location)
                .systemQty(systemQty)
                .actualQty(actualQty)
                .difference(difference)
                .reason(reason)
                .requiresApproval(requiresApproval)
                .approvalStatus(approvalStatus)
                .adjustedBy(request.getAdjustedBy())
                .build();

        InventoryAdjustment savedAdjustment = inventoryAdjustmentRepository.save(adjustment);

        // 8. 자동 승인인 경우 즉시 재고 반영
        if (approvalStatus == InventoryAdjustment.ApprovalStatus.AUTO_APPROVED) {
            applyAdjustment(savedAdjustment);
        }

        log.info("Inventory adjustment created: {} (status: {})", savedAdjustment.getAdjustmentId(), approvalStatus);
        return convertToResponse(savedAdjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse approveInventoryAdjustment(UUID adjustmentId, ApprovalRequest request) {
        // 1. 조정 조회
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Inventory adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS", "Adjustment is not in pending status");
        }

        // 2. 승인 처리
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.APPROVED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());
        InventoryAdjustment savedAdjustment = inventoryAdjustmentRepository.save(adjustment);

        // 3. 재고 반영
        applyAdjustment(savedAdjustment);

        log.info("Inventory adjustment {} approved by {}", adjustmentId, request.getApprovedBy());
        return convertToResponse(savedAdjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse rejectInventoryAdjustment(UUID adjustmentId, ApprovalRequest request) {
        // 1. 조정 조회
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Inventory adjustment not found"));

        if (adjustment.getApprovalStatus() != InventoryAdjustment.ApprovalStatus.PENDING) {
            throw new BusinessException("INVALID_STATUS", "Adjustment is not in pending status");
        }

        // 2. 거부 처리 (재고 변동 없음)
        adjustment.setApprovalStatus(InventoryAdjustment.ApprovalStatus.REJECTED);
        adjustment.setApprovedBy(request.getApprovedBy());
        adjustment.setApprovedAt(Instant.now());
        InventoryAdjustment savedAdjustment = inventoryAdjustmentRepository.save(adjustment);

        log.info("Inventory adjustment {} rejected by {}", adjustmentId, request.getApprovedBy());
        return convertToResponse(savedAdjustment);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getInventoryAdjustment(UUID adjustmentId) {
        InventoryAdjustment adjustment = inventoryAdjustmentRepository.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("ADJUSTMENT_NOT_FOUND", "Inventory adjustment not found"));
        return convertToResponse(adjustment);
    }

    @Transactional(readOnly = true)
    public Page<InventoryAdjustmentResponse> getAllInventoryAdjustments(Pageable pageable) {
        return inventoryAdjustmentRepository.findAll(pageable)
                .map(this::convertToResponse);
    }

    // ===== Helper Methods =====

    private void applyAdjustment(InventoryAdjustment adjustment) {
        Product product = adjustment.getProduct();
        Location location = adjustment.getLocation();
        int difference = adjustment.getDifference();

        if (difference == 0) {
            return; // 차이가 없으면 아무것도 하지 않음
        }

        // 1. 재고 조정 반영
        // 기존 재고를 조회하거나 새로 생성
        List<Inventory> inventories = inventoryRepository.findByProductAndLocation(
                product.getProductId(), location.getLocationId());

        if (inventories.isEmpty() && difference > 0) {
            // 시스템에 없는 재고가 발견된 경우 (신규 생성)
            Inventory newInventory = Inventory.builder()
                    .product(product)
                    .location(location)
                    .quantity(adjustment.getActualQty())
                    .lotNumber(null)
                    .expiryDate(null)
                    .manufactureDate(null)
                    .receivedAt(Instant.now())
                    .isExpired(false)
                    .build();
            inventoryRepository.save(newInventory);

            // 로케이션 current_qty 갱신
            location.setCurrentQty(location.getCurrentQty() + adjustment.getActualQty());
            locationRepository.save(location);
        } else if (!inventories.isEmpty()) {
            // 기존 재고가 있는 경우
            Inventory inventory = inventories.get(0); // 첫 번째 재고에 조정 반영
            int newQty = inventory.getQuantity() + difference;

            if (newQty < 0) {
                throw new BusinessException("INVALID_ADJUSTMENT", "Adjustment would result in negative inventory");
            }

            if (newQty == 0) {
                inventoryRepository.delete(inventory);
            } else {
                inventory.setQuantity(newQty);
                inventoryRepository.save(inventory);
            }

            // 로케이션 current_qty 갱신
            location.setCurrentQty(location.getCurrentQty() + difference);
            locationRepository.save(location);
        }

        // 2. HIGH_VALUE 감사 로그 기록
        if (product.getCategory() == Product.ProductCategory.HIGH_VALUE && difference != 0) {
            Map<String, Object> details = new HashMap<>();
            details.put("adjustmentId", adjustment.getAdjustmentId().toString());
            details.put("productId", product.getProductId().toString());
            details.put("productSku", product.getSku());
            details.put("locationId", location.getLocationId().toString());
            details.put("locationCode", location.getCode());
            details.put("systemQty", adjustment.getSystemQty());
            details.put("actualQty", adjustment.getActualQty());
            details.put("difference", difference);
            details.put("reason", adjustment.getReason());
            details.put("approvedBy", adjustment.getApprovedBy());

            AuditLog auditLog = AuditLog.builder()
                    .eventType("HIGH_VALUE_ADJUSTMENT")
                    .entityType("InventoryAdjustment")
                    .entityId(adjustment.getAdjustmentId())
                    .details(details)
                    .performedBy(adjustment.getApprovedBy() != null ?
                            adjustment.getApprovedBy() : adjustment.getAdjustedBy())
                    .build();
            auditLogRepository.save(auditLog);

            log.warn("HIGH_VALUE adjustment recorded for product {} at location {}. Full location re-count recommended.",
                    product.getSku(), location.getCode());
        }

        // 3. 안전재고 체크
        checkSafetyStockAfterAdjustment(product);

        log.info("Adjustment applied: {} units for product {} at location {}",
                difference, product.getSku(), location.getCode());
    }

    private void checkSafetyStockAfterAdjustment(Product product) {
        // 전체 가용 재고 확인 (is_expired=false인 것만)
        int totalStock = inventoryRepository.sumAvailableQuantityByProduct(product.getProductId());

        SafetyStockRule rule = safetyStockRuleRepository.findByProductProductId(product.getProductId())
                .orElse(null);

        if (rule != null && totalStock <= rule.getMinQty()) {
            // 자동 재발주 로그 생성
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .product(product)
                    .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(totalStock)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy("SYSTEM")
                    .build();
            autoReorderLogRepository.save(reorderLog);

            log.warn("Safety stock triggered for product {}: current={}, min={}",
                    product.getSku(), totalStock, rule.getMinQty());
        }
    }

    private CycleCountResponse convertToCycleCountResponse(CycleCount cycleCount) {
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

    private InventoryAdjustmentResponse convertToResponse(InventoryAdjustment adjustment) {
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
// FILE: src\main\java\com\wms\service\ShipmentOrderService.java
============================================================
package com.wms.service;

import com.wms.dto.PickingResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
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
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // 1. 중복 shipmentNumber 체크
        if (shipmentOrderRepository.findByShipmentNumber(request.getShipmentNumber()).isPresent()) {
            throw new BusinessException("DUPLICATE_SHIPMENT_NUMBER", "Shipment number already exists");
        }

        // 2. 상품 검증 및 HAZMAT/FRESH 분리 확인
        List<ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();
        boolean hasFresh = false;

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
                if (product.getCategory() == Product.ProductCategory.FRESH) {
                    hasFresh = true;
                }
            }
        }

        // 3. HAZMAT + FRESH 혼재 시 HAZMAT 분리
        if (!hazmatLines.isEmpty() && hasFresh) {
            // HAZMAT 별도 출고 지시서 생성
            ShipmentOrder hazmatShipment = createShipmentOrderInternal(
                    request.getShipmentNumber() + "-HAZMAT",
                    request.getCustomerName(),
                    request.getRequestedAt(),
                    hazmatLines
            );
            log.info("HAZMAT shipment created separately: {}", hazmatShipment.getShipmentNumber());

            // 비-HAZMAT 출고 지시서 생성
            ShipmentOrder mainShipment = createShipmentOrderInternal(
                    request.getShipmentNumber(),
                    request.getCustomerName(),
                    request.getRequestedAt(),
                    nonHazmatLines
            );
            return ShipmentOrderResponse.from(mainShipment);
        } else {
            // 분리 필요 없음
            ShipmentOrder shipment = createShipmentOrderInternal(
                    request.getShipmentNumber(),
                    request.getCustomerName(),
                    request.getRequestedAt(),
                    request.getLines()
            );
            return ShipmentOrderResponse.from(shipment);
        }
    }

    private ShipmentOrder createShipmentOrderInternal(
            String shipmentNumber,
            String customerName,
            Instant requestedAt,
            List<ShipmentOrderRequest.ShipmentOrderLineRequest> lineRequests
    ) {
        ShipmentOrder shipment = ShipmentOrder.builder()
                .shipmentNumber(shipmentNumber)
                .customerName(customerName)
                .requestedAt(requestedAt != null ? requestedAt : Instant.now())
                .status(ShipmentOrder.ShipmentStatus.PENDING)
                .build();

        List<ShipmentOrderLine> lines = new ArrayList<>();
        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : lineRequests) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .shipmentOrder(shipment)
                    .product(product)
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.PENDING)
                    .build();
            lines.add(line);
        }

        shipment.setLines(lines);
        return shipmentOrderRepository.save(shipment);
    }

    @Transactional
    public PickingResponse pickShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findByIdWithLines(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment order not found"));

        if (shipment.getStatus() != ShipmentOrder.ShipmentStatus.PENDING &&
                shipment.getStatus() != ShipmentOrder.ShipmentStatus.PICKING) {
            throw new BusinessException("INVALID_STATUS", "Shipment order cannot be picked");
        }

        shipment.setStatus(ShipmentOrder.ShipmentStatus.PICKING);

        List<PickingResponse.LinePickingDetail> lineDetails = new ArrayList<>();
        List<PickingResponse.BackorderDetail> backorders = new ArrayList<>();

        for (ShipmentOrderLine line : shipment.getLines()) {
            PickingResult result = pickLine(line);
            lineDetails.add(result.lineDetail);
            backorders.addAll(result.backorders);
        }

        // 모든 라인 상태 확인 후 shipment 상태 갱신
        updateShipmentStatus(shipment);

        // 안전재고 체크
        checkSafetyStock(shipment);

        return PickingResponse.builder()
                .shipmentId(shipment.getShipmentId())
                .shipmentNumber(shipment.getShipmentNumber())
                .status(shipment.getStatus().name())
                .lineDetails(lineDetails)
                .backorders(backorders)
                .build();
    }

    private PickingResult pickLine(ShipmentOrderLine line) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQty();

        // 1. 가용 재고 조회 (피킹 가능한 재고만)
        List<Inventory> availableInventories = getAvailableInventories(product);

        // 2. 전체 가용 재고 계산
        int totalAvailableQty = availableInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 3. 부분출고 의사결정
        if (totalAvailableQty == 0) {
            // 전량 백오더
            return createFullBackorder(line, requestedQty);
        } else if (totalAvailableQty < requestedQty) {
            double fulfillmentRate = (double) totalAvailableQty / requestedQty;

            if (fulfillmentRate < 0.3) {
                // 가용 재고 < 30%: 전량 백오더
                return createFullBackorder(line, requestedQty);
            } else if (fulfillmentRate < 0.7) {
                // 30% <= 가용 재고 < 70%: 부분출고 + 백오더 + 긴급발주
                PickingResult result = performPartialPicking(line, availableInventories, totalAvailableQty);
                triggerUrgentReorder(product, totalAvailableQty);
                return result;
            } else {
                // 가용 재고 >= 70%: 부분출고 + 백오더
                return performPartialPicking(line, availableInventories, totalAvailableQty);
            }
        } else {
            // 전량 출고 가능
            return performFullPicking(line, availableInventories, requestedQty);
        }
    }

    private List<Inventory> getAvailableInventories(Product product) {
        // 1. 모든 재고 조회 (is_expired=false, quantity>0, is_frozen=false)
        List<Inventory> inventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> !inv.getIsExpired())
                .filter(inv -> inv.getQuantity() > 0)
                .filter(inv -> !inv.getLocation().getIsFrozen())
                .collect(Collectors.toList());

        // 2. HAZMAT 상품은 HAZMAT zone만
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            inventories = inventories.stream()
                    .filter(inv -> inv.getLocation().getZone() == Location.LocationZone.HAZMAT)
                    .collect(Collectors.toList());
        }

        // 3. 유통기한 관리 상품 처리
        if (product.getHasExpiry()) {
            LocalDate today = LocalDate.now();
            List<Inventory> validInventories = new ArrayList<>();

            for (Inventory inv : inventories) {
                // 만료된 재고 제외
                if (inv.getExpiryDate() != null && inv.getExpiryDate().isBefore(today)) {
                    continue;
                }

                // 잔여율 계산
                double remainingPct = calculateRemainingShelfLifePct(
                        inv.getExpiryDate(),
                        inv.getManufactureDate(),
                        today
                );

                // 잔여율 < 10%: 피킹 불가 (is_expired=true 설정)
                if (remainingPct < 10.0) {
                    inv.setIsExpired(true);
                    inventoryRepository.save(inv);
                    continue;
                }

                validInventories.add(inv);
            }

            inventories = validInventories;

            // 4. FEFO 정렬 (유통기한 관리 상품)
            inventories.sort((a, b) -> {
                // 잔여율 < 30% 우선
                double aPct = calculateRemainingShelfLifePct(a.getExpiryDate(), a.getManufactureDate(), today);
                double bPct = calculateRemainingShelfLifePct(b.getExpiryDate(), b.getManufactureDate(), today);

                if (aPct < 30.0 && bPct >= 30.0) return -1;
                if (aPct >= 30.0 && bPct < 30.0) return 1;

                // 유통기한 오름차순
                int expiryCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                if (expiryCompare != 0) return expiryCompare;

                // 입고일 오름차순
                return a.getReceivedAt().compareTo(b.getReceivedAt());
            });
        } else {
            // 5. FIFO 정렬 (비관리 상품)
            inventories.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return inventories;
    }

    private double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate, LocalDate today) {
        if (expiryDate == null || manufactureDate == null) {
            return 100.0;
        }
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays <= 0) return 0.0;
        return (double) remainingDays / totalDays * 100.0;
    }

    private PickingResult performFullPicking(ShipmentOrderLine line, List<Inventory> inventories, int qtyToPick) {
        List<PickingResponse.LocationPickDetail> pickDetails = new ArrayList<>();
        int remainingQty = qtyToPick;

        for (Inventory inv : inventories) {
            if (remainingQty <= 0) break;

            // max_pick_qty 제한 (HAZMAT)
            int maxPickQty = line.getProduct().getMaxPickQty() != null
                    ? line.getProduct().getMaxPickQty()
                    : Integer.MAX_VALUE;

            int pickQty = Math.min(Math.min(remainingQty, inv.getQuantity()), maxPickQty);

            // 재고 차감
            inv.setQuantity(inv.getQuantity() - pickQty);
            inv.getLocation().setCurrentQty(inv.getLocation().getCurrentQty() - pickQty);

            // 보관 유형 불일치 경고
            if (inv.getLocation().getStorageType() != line.getProduct().getStorageType()) {
                logStorageTypeMismatch(inv, line.getProduct());
            }

            pickDetails.add(PickingResponse.LocationPickDetail.builder()
                    .locationId(inv.getLocation().getLocationId())
                    .locationCode(inv.getLocation().getCode())
                    .pickedQty(pickQty)
                    .lotNumber(inv.getLotNumber())
                    .build());

            remainingQty -= pickQty;
        }

        line.setPickedQty(qtyToPick);
        line.setStatus(ShipmentOrderLine.LineStatus.PICKED);

        return new PickingResult(
                PickingResponse.LinePickingDetail.builder()
                        .shipmentLineId(line.getShipmentLineId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .requestedQty(line.getRequestedQty())
                        .pickedQty(line.getPickedQty())
                        .status(line.getStatus().name())
                        .pickDetails(pickDetails)
                        .build(),
                Collections.emptyList()
        );
    }

    private PickingResult performPartialPicking(ShipmentOrderLine line, List<Inventory> inventories, int availableQty) {
        List<PickingResponse.LocationPickDetail> pickDetails = new ArrayList<>();
        int remainingQty = availableQty;

        for (Inventory inv : inventories) {
            if (remainingQty <= 0) break;

            int maxPickQty = line.getProduct().getMaxPickQty() != null
                    ? line.getProduct().getMaxPickQty()
                    : Integer.MAX_VALUE;

            int pickQty = Math.min(Math.min(remainingQty, inv.getQuantity()), maxPickQty);

            inv.setQuantity(inv.getQuantity() - pickQty);
            inv.getLocation().setCurrentQty(inv.getLocation().getCurrentQty() - pickQty);

            if (inv.getLocation().getStorageType() != line.getProduct().getStorageType()) {
                logStorageTypeMismatch(inv, line.getProduct());
            }

            pickDetails.add(PickingResponse.LocationPickDetail.builder()
                    .locationId(inv.getLocation().getLocationId())
                    .locationCode(inv.getLocation().getCode())
                    .pickedQty(pickQty)
                    .lotNumber(inv.getLotNumber())
                    .build());

            remainingQty -= pickQty;
        }

        line.setPickedQty(availableQty);
        line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);

        // 백오더 생성
        int shortageQty = line.getRequestedQty() - availableQty;
        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(Backorder.BackorderStatus.OPEN)
                .build();
        backorderRepository.save(backorder);

        return new PickingResult(
                PickingResponse.LinePickingDetail.builder()
                        .shipmentLineId(line.getShipmentLineId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .requestedQty(line.getRequestedQty())
                        .pickedQty(line.getPickedQty())
                        .status(line.getStatus().name())
                        .pickDetails(pickDetails)
                        .build(),
                List.of(PickingResponse.BackorderDetail.builder()
                        .backorderId(backorder.getBackorderId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .shortageQty(shortageQty)
                        .build())
        );
    }

    private PickingResult createFullBackorder(ShipmentOrderLine line, int shortageQty) {
        line.setPickedQty(0);
        line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);

        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(Backorder.BackorderStatus.OPEN)
                .build();
        backorderRepository.save(backorder);

        return new PickingResult(
                PickingResponse.LinePickingDetail.builder()
                        .shipmentLineId(line.getShipmentLineId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .requestedQty(line.getRequestedQty())
                        .pickedQty(0)
                        .status(line.getStatus().name())
                        .pickDetails(Collections.emptyList())
                        .build(),
                List.of(PickingResponse.BackorderDetail.builder()
                        .backorderId(backorder.getBackorderId())
                        .productId(line.getProduct().getProductId())
                        .productSku(line.getProduct().getSku())
                        .shortageQty(shortageQty)
                        .build())
        );
    }

    private void triggerUrgentReorder(Product product, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProductProductId(product.getProductId())
                .orElse(null);

        if (rule != null) {
            AutoReorderLog log = AutoReorderLog.builder()
                    .product(product)
                    .triggerType(AutoReorderLog.TriggerType.URGENT_REORDER)
                    .currentStock(currentStock)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy("SYSTEM")
                    .build();
            autoReorderLogRepository.save(log);
            log.info("Urgent reorder triggered for product: {}", product.getSku());
        }
    }

    private void logStorageTypeMismatch(Inventory inventory, Product product) {
        Map<String, Object> details = new HashMap<>();
        details.put("locationStorageType", inventory.getLocation().getStorageType().name());
        details.put("productStorageType", product.getStorageType().name());
        details.put("locationCode", inventory.getLocation().getCode());
        details.put("productSku", product.getSku());

        AuditLog auditLog = AuditLog.builder()
                .eventType("STORAGE_TYPE_MISMATCH")
                .entityType("INVENTORY")
                .entityId(inventory.getInventoryId())
                .details(details)
                .performedBy("SYSTEM")
                .build();
        auditLogRepository.save(auditLog);

        log.warn("Storage type mismatch: location {} has {}, but product {} requires {}",
                inventory.getLocation().getCode(),
                inventory.getLocation().getStorageType(),
                product.getSku(),
                product.getStorageType());
    }

    private void updateShipmentStatus(ShipmentOrder shipment) {
        boolean allPicked = shipment.getLines().stream()
                .allMatch(line -> line.getStatus() == ShipmentOrderLine.LineStatus.PICKED);
        boolean anyPicked = shipment.getLines().stream()
                .anyMatch(line -> line.getStatus() == ShipmentOrderLine.LineStatus.PICKED ||
                        line.getStatus() == ShipmentOrderLine.LineStatus.PARTIAL);

        if (allPicked) {
            shipment.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
            shipment.setShippedAt(Instant.now());
        } else if (anyPicked) {
            shipment.setStatus(ShipmentOrder.ShipmentStatus.PARTIAL);
        }
    }

    private void checkSafetyStock(ShipmentOrder shipment) {
        Set<UUID> checkedProducts = new HashSet<>();

        for (ShipmentOrderLine line : shipment.getLines()) {
            UUID productId = line.getProduct().getProductId();
            if (checkedProducts.contains(productId)) continue;
            checkedProducts.add(productId);

            SafetyStockRule rule = safetyStockRuleRepository.findByProductProductId(productId)
                    .orElse(null);

            if (rule != null) {
                int totalStock = inventoryRepository.findAll().stream()
                        .filter(inv -> inv.getProduct().getProductId().equals(productId))
                        .filter(inv -> !inv.getIsExpired())
                        .mapToInt(Inventory::getQuantity)
                        .sum();

                if (totalStock <= rule.getMinQty()) {
                    AutoReorderLog log = AutoReorderLog.builder()
                            .product(line.getProduct())
                            .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                            .currentStock(totalStock)
                            .minQty(rule.getMinQty())
                            .reorderQty(rule.getReorderQty())
                            .triggeredBy("SYSTEM")
                            .build();
                    autoReorderLogRepository.save(log);
                    log.info("Safety stock reorder triggered for product: {}", line.getProduct().getSku());
                }
            }
        }
    }

    @Transactional
    public ShipmentOrderResponse shipShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findByIdWithLines(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment order not found"));

        if (shipment.getStatus() != ShipmentOrder.ShipmentStatus.PICKING &&
                shipment.getStatus() != ShipmentOrder.ShipmentStatus.PARTIAL) {
            throw new BusinessException("INVALID_STATUS", "Shipment order cannot be shipped");
        }

        shipment.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
        shipment.setShippedAt(Instant.now());

        return ShipmentOrderResponse.from(shipment);
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipment = shipmentOrderRepository.findByIdWithLines(shipmentId)
                .orElseThrow(() -> new BusinessException("SHIPMENT_NOT_FOUND", "Shipment order not found"));
        return ShipmentOrderResponse.from(shipment);
    }

    @Transactional(readOnly = true)
    public Page<ShipmentOrderResponse> getShipmentOrders(Pageable pageable) {
        return shipmentOrderRepository.findAll(pageable)
                .map(ShipmentOrderResponse::from);
    }

    private static class PickingResult {
        PickingResponse.LinePickingDetail lineDetail;
        List<PickingResponse.BackorderDetail> backorders;

        PickingResult(PickingResponse.LinePickingDetail lineDetail, List<PickingResponse.BackorderDetail> backorders) {
            this.lineDetail = lineDetail;
            this.backorders = backorders;
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\service\StockTransferService.java
============================================================
package com.wms.service;

import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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
        // 1. 엔티티 조회 및 기본 검증
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("PRODUCT_NOT_FOUND", "Product not found"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "From location not found"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "To location not found"));

        // 2. 동일 로케이션 체크
        if (request.getFromLocationId().equals(request.getToLocationId())) {
            throw new BusinessException("SAME_LOCATION", "From and to locations must be different");
        }

        // 3. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new BusinessException("LOCATION_FROZEN", "From location is frozen for cycle count");
        }
        if (toLocation.getIsFrozen()) {
            throw new BusinessException("LOCATION_FROZEN", "To location is frozen for cycle count");
        }

        // 4. 출발지 재고 조회 및 수량 체크
        Inventory sourceInventory = inventoryRepository
                .findByProductProductIdAndLocationLocationIdAndLotNumber(
                        product.getProductId(), fromLocation.getLocationId(), request.getLotNumber())
                .orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", "Source inventory not found"));

        if (sourceInventory.getQuantity() < request.getQuantity()) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    String.format("Insufficient stock. Available: %d, Requested: %d",
                            sourceInventory.getQuantity(), request.getQuantity()));
        }

        // 5. 도착지 용량 체크
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new BusinessException("CAPACITY_EXCEEDED",
                    String.format("Destination location capacity exceeded. Available: %d, Required: %d",
                            toLocation.getCapacity() - toLocation.getCurrentQty(), request.getQuantity()));
        }

        // 6. 보관 유형 호환성 체크
        validateStorageTypeCompatibility(product, toLocation);

        // 7. HAZMAT 혼적 금지 체크
        validateHazmatSegregation(product, toLocation);

        // 8. 유통기한 임박 상품 이동 제한
        if (product.getHasExpiry() && sourceInventory.getExpiryDate() != null) {
            validateExpiryDateRestrictions(product, sourceInventory, toLocation);
        }

        // 9. 대량 이동 승인 체크 (80% 이상)
        boolean requiresApproval = false;
        double transferRatio = (double) request.getQuantity() / sourceInventory.getQuantity();
        if (transferRatio >= 0.8) {
            requiresApproval = true;
        }

        // 10. 재고 이동 이력 생성
        StockTransfer.TransferStatus status = requiresApproval ?
                StockTransfer.TransferStatus.PENDING_APPROVAL : StockTransfer.TransferStatus.IMMEDIATE;

        StockTransfer transfer = StockTransfer.builder()
                .product(product)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .quantity(request.getQuantity())
                .lotNumber(request.getLotNumber())
                .reason(request.getReason())
                .transferStatus(status)
                .transferredBy(request.getTransferredBy())
                .transferredAt(Instant.now())
                .build();

        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        // 11. 승인이 필요하지 않으면 즉시 재고 이동 실행
        if (!requiresApproval) {
            executeTransfer(savedTransfer, sourceInventory);
        }

        log.info("Stock transfer created with ID: {} (status: {})", savedTransfer.getTransferId(), status);
        return convertToResponse(savedTransfer);
    }

    @Transactional
    public StockTransferResponse approveStockTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findByIdWithDetails(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Stock transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not in pending_approval status");
        }

        // 재고 조회 및 이동 실행
        Inventory sourceInventory = inventoryRepository
                .findByProductProductIdAndLocationLocationIdAndLotNumber(
                        transfer.getProduct().getProductId(),
                        transfer.getFromLocation().getLocationId(),
                        transfer.getLotNumber())
                .orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND", "Source inventory not found"));

        executeTransfer(transfer, sourceInventory);

        transfer.setTransferStatus(StockTransfer.TransferStatus.APPROVED);
        transfer.setApprovedBy(approvedBy);
        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        log.info("Stock transfer {} approved by {}", transferId, approvedBy);
        return convertToResponse(savedTransfer);
    }

    @Transactional
    public StockTransferResponse rejectStockTransfer(UUID transferId, String rejectedBy, String reason) {
        StockTransfer transfer = stockTransferRepository.findByIdWithDetails(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Stock transfer not found"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.PENDING_APPROVAL) {
            throw new BusinessException("INVALID_STATUS", "Transfer is not in pending_approval status");
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.REJECTED);
        transfer.setApprovedBy(rejectedBy);
        StockTransfer savedTransfer = stockTransferRepository.save(transfer);

        log.info("Stock transfer {} rejected by {}. Reason: {}", transferId, rejectedBy, reason);
        return convertToResponse(savedTransfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getStockTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findByIdWithDetails(transferId)
                .orElseThrow(() -> new BusinessException("TRANSFER_NOT_FOUND", "Stock transfer not found"));
        return convertToResponse(transfer);
    }

    @Transactional(readOnly = true)
    public Page<StockTransferResponse> getAllStockTransfers(Pageable pageable) {
        return stockTransferRepository.findAll(pageable)
                .map(this::convertToResponse);
    }

    // ===== Helper Methods =====

    private void executeTransfer(StockTransfer transfer, Inventory sourceInventory) {
        Product product = transfer.getProduct();
        Location fromLocation = transfer.getFromLocation();
        Location toLocation = transfer.getToLocation();
        int quantity = transfer.getQuantity();

        // 1. 출발지 재고 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - quantity);
        if (sourceInventory.getQuantity() == 0) {
            inventoryRepository.delete(sourceInventory);
        } else {
            inventoryRepository.save(sourceInventory);
        }

        // 2. 출발지 로케이션 current_qty 차감
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - quantity);
        locationRepository.save(fromLocation);

        // 3. 도착지 재고 증가 (동일 product + lot_number 조합이 있으면 증가, 없으면 신규 생성)
        Inventory destInventory = inventoryRepository
                .findByProductProductIdAndLocationLocationIdAndLotNumber(
                        product.getProductId(), toLocation.getLocationId(), transfer.getLotNumber())
                .orElse(null);

        if (destInventory != null) {
            destInventory.setQuantity(destInventory.getQuantity() + quantity);
            inventoryRepository.save(destInventory);
        } else {
            Inventory newInventory = Inventory.builder()
                    .product(product)
                    .location(toLocation)
                    .quantity(quantity)
                    .lotNumber(transfer.getLotNumber())
                    .expiryDate(sourceInventory.getExpiryDate())
                    .manufactureDate(sourceInventory.getManufactureDate())
                    .receivedAt(sourceInventory.getReceivedAt()) // 원래 입고일 유지 (FIFO)
                    .isExpired(sourceInventory.getIsExpired())
                    .build();
            inventoryRepository.save(newInventory);
        }

        // 4. 도착지 로케이션 current_qty 증가
        toLocation.setCurrentQty(toLocation.getCurrentQty() + quantity);
        locationRepository.save(toLocation);

        // 5. 안전재고 체크 (STORAGE zone 기준)
        checkSafetyStockAfterTransfer(product);

        log.info("Transfer executed: {} units of product {} from {} to {}",
                quantity, product.getSku(), fromLocation.getCode(), toLocation.getCode());
    }

    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = toLocation.getStorageType();

        // HAZMAT은 HAZMAT zone만
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (toLocation.getZone() != Location.Zone.HAZMAT) {
                throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                        "HAZMAT products can only be moved to HAZMAT zone");
            }
        }

        // FROZEN → AMBIENT: 거부
        if (productType == Product.StorageType.FROZEN && locationType == Product.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "FROZEN products cannot be moved to AMBIENT locations");
        }

        // COLD → AMBIENT: 거부
        if (productType == Product.StorageType.COLD && locationType == Product.StorageType.AMBIENT) {
            throw new BusinessException("STORAGE_TYPE_INCOMPATIBLE",
                    "COLD products cannot be moved to AMBIENT locations");
        }

        // AMBIENT → COLD/FROZEN: 허용 (상위 호환)
    }

    private void validateHazmatSegregation(Product product, Location toLocation) {
        // 도착지에 이미 적재된 재고 조회
        List<Inventory> existingInventories = inventoryRepository
                .findByLocationLocationId(toLocation.getLocationId());

        boolean isHazmat = (product.getCategory() == Product.ProductCategory.HAZMAT);

        for (Inventory inv : existingInventories) {
            Product existingProduct = inv.getProduct();
            boolean isExistingHazmat = (existingProduct.getCategory() == Product.ProductCategory.HAZMAT);

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (isHazmat && !isExistingHazmat) {
                throw new BusinessException("HAZMAT_SEGREGATION_VIOLATION",
                        "Cannot move HAZMAT product to location with non-HAZMAT products");
            }
            if (!isHazmat && isExistingHazmat) {
                throw new BusinessException("HAZMAT_SEGREGATION_VIOLATION",
                        "Cannot move non-HAZMAT product to location with HAZMAT products");
            }
        }
    }

    private void validateExpiryDateRestrictions(Product product, Inventory sourceInventory, Location toLocation) {
        LocalDate expiryDate = sourceInventory.getExpiryDate();
        LocalDate manufactureDate = sourceInventory.getManufactureDate();
        LocalDate today = LocalDate.now();

        // 유통기한 만료 체크
        if (expiryDate.isBefore(today)) {
            throw new BusinessException("EXPIRED_PRODUCT", "Cannot move expired products");
        }

        // 잔여 유통기한 비율 계산
        if (manufactureDate != null) {
            long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = (totalDays > 0) ? (remainingDays * 100.0 / totalDays) : 0.0;

            // 잔여 유통기한 < 10%: SHIPPING zone으로만 이동 허용
            if (remainingPct < 10.0 && toLocation.getZone() != Location.Zone.SHIPPING) {
                throw new BusinessException("EXPIRY_RESTRICTION",
                        String.format("Products with less than 10%% shelf life can only be moved to SHIPPING zone. Remaining: %.2f%%", remainingPct));
            }
        }
    }

    private void checkSafetyStockAfterTransfer(Product product) {
        // STORAGE zone 내 전체 재고 확인
        int storageStock = inventoryRepository.sumQuantityByProductAndZone(
                product.getProductId(), Location.Zone.STORAGE);

        SafetyStockRule rule = safetyStockRuleRepository.findByProductProductId(product.getProductId())
                .orElse(null);

        if (rule != null && storageStock <= rule.getMinQty()) {
            // 자동 재발주 로그 생성
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .product(product)
                    .triggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER)
                    .currentStock(storageStock)
                    .minQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .triggeredBy("SYSTEM")
                    .build();
            autoReorderLogRepository.save(reorderLog);

            log.warn("Safety stock triggered for product {}: current={}, min={}",
                    product.getSku(), storageStock, rule.getMinQty());
        }
    }

    private StockTransferResponse convertToResponse(StockTransfer transfer) {
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
// FILE: src\main\resources\application.properties
============================================================
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/wms_db
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA Configuration
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

# Server Configuration
server.port=8080
server.servlet.context-path=/api/v1

# Logging
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
logging.level.com.wms=DEBUG

