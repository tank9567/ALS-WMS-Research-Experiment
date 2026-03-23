# CLI Output

재고 이동 기능 구현이 완료되었습니다.

## 구현 내용

### 1. Entity
- `StockTransfer` (src/main/java/com/wms/entity/StockTransfer.java:1)
  - transfer_status: immediate, pending_approval, approved, rejected

### 2. Repository
- `StockTransferRepository` (src/main/java/com/wms/repository/StockTransferRepository.java:1)
  - 상품별, 로케이션별, 상태별 조회 메서드

### 3. DTO
- `StockTransferRequest` (src/main/java/com/wms/dto/StockTransferRequest.java:1)
- `StockTransferResponse` (src/main/java/com/wms/dto/StockTransferResponse.java:1)
- `ApprovalRequest` (src/main/java/com/wms/dto/ApprovalRequest.java:1)

### 4. Service (비즈니스 규칙)
`StockTransferService` (src/main/java/com/wms/service/StockTransferService.java:1)에서 ALS-WMS-STK-002 규칙을 모두 구현:

- ✅ 단일 트랜잭션 처리 (출발지 차감 + 도착지 증가)
- ✅ 동일 로케이션 거부
- ✅ 재고 부족 체크
- ✅ 용량 초과 체크
- ✅ 실사 동결 로케이션 체크 (is_frozen)
- ✅ 보관유형 호환성 (FROZEN→AMBIENT 거부 등)
- ✅ HAZMAT 혼적 금지
- ✅ 유통기한 <10% → SHIPPING zone만 허용
- ✅ 유통기한 만료 → 이동 불가
- ✅ 대량 이동(≥80%) → 관리자 승인 (pending_approval)
- ✅ 이동 후 STORAGE zone 안전재고 체크

### 5. REST API
`StockTransferController` (src/main/java/com/wms/controller/StockTransferController.java:1):

- `POST /api/v1/stock-transfers` - 재고 이동 실행
- `POST /api/v1/stock-transfers/{id}/approve` - 대량 이동 승인
- `POST /api/v1/stock-transfers/{id}/reject` - 대량 이동 거부
- `GET /api/v1/stock-transfers/{id}` - 이동 상세 조회
- `GET /api/v1/stock-transfers` - 이동 이력 조회 (필터링 지원)

기존 입고/출고 코드와 일관된 구조로 구현하였으며, ALS 문서의 모든 제약사항을 준수합니다.


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
    <name>WMS Inbound Management System</name>
    <description>Warehouse Management System - Inbound Processing</description>

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
// FILE: schema.sql
============================================================
-- ========================================
-- WMS Database Schema (Level 2)
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    /**
     * POST /api/v1/inbound-receipts
     * 입고 등록 (inspecting 상태)
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
        @Valid @RequestBody InboundReceiptRequest request
    ) {
        try {
            log.info("POST /inbound-receipts - 입고 등록 요청");
            InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("입고 등록 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("입고 등록 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("입고 등록 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/confirm
     * 입고 확정 (재고 반영)
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(
        @PathVariable("id") UUID receiptId
    ) {
        try {
            log.info("POST /inbound-receipts/{}/confirm - 입고 확정 요청", receiptId);
            InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("입고 확정 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("입고 확정 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("입고 확정 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/reject
     * 입고 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
        @PathVariable("id") UUID receiptId,
        @RequestParam(required = false) String reason
    ) {
        try {
            log.info("POST /inbound-receipts/{}/reject - 입고 거부 요청", receiptId);
            InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(receiptId, reason);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("입고 거부 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("입고 거부 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("입고 거부 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * POST /api/v1/inbound-receipts/{id}/approve
     * 유통기한 경고 승인 (pending_approval -> confirmed)
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveInboundReceipt(
        @PathVariable("id") UUID receiptId
    ) {
        try {
            log.info("POST /inbound-receipts/{}/approve - 유통기한 경고 승인 요청", receiptId);
            InboundReceiptResponse response = inboundReceiptService.approveInboundReceipt(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("유통기한 경고 승인 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("유통기한 경고 승인 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("유통기한 경고 승인 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * GET /api/v1/inbound-receipts/{id}
     * 입고 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(
        @PathVariable("id") UUID receiptId
    ) {
        try {
            log.info("GET /inbound-receipts/{} - 입고 상세 조회 요청", receiptId);
            InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("입고 조회 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("입고 조회 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * GET /api/v1/inbound-receipts
     * 입고 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InboundReceiptResponse>>> getAllInboundReceipts() {
        try {
            log.info("GET /inbound-receipts - 입고 목록 조회 요청");
            List<InboundReceiptResponse> responses = inboundReceiptService.getAllInboundReceipts();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            log.error("입고 목록 조회 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\ShipmentOrderController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.service.ShipmentOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/shipment-orders")
@RequiredArgsConstructor
public class ShipmentOrderController {

    private final ShipmentOrderService shipmentOrderService;

    /**
     * POST /api/v1/shipment-orders
     * 출고 지시서 생성
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> createShipmentOrder(
        @Valid @RequestBody ShipmentOrderRequest request
    ) {
        try {
            log.info("POST /shipment-orders - 출고 지시서 생성 요청");
            ShipmentOrderResponse response = shipmentOrderService.createShipmentOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("출고 지시서 생성 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("출고 지시서 생성 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("출고 지시서 생성 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * POST /api/v1/shipment-orders/{id}/pick
     * 피킹 실행
     */
    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> executePicking(
        @PathVariable("id") UUID shipmentOrderId
    ) {
        try {
            log.info("POST /shipment-orders/{}/pick - 피킹 실행 요청", shipmentOrderId);
            ShipmentOrderResponse response = shipmentOrderService.executePicking(shipmentOrderId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("피킹 실행 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("피킹 실행 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("피킹 실행 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * POST /api/v1/shipment-orders/{id}/ship
     * 출고 확정
     */
    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> confirmShipment(
        @PathVariable("id") UUID shipmentOrderId
    ) {
        try {
            log.info("POST /shipment-orders/{}/ship - 출고 확정 요청", shipmentOrderId);
            ShipmentOrderResponse response = shipmentOrderService.confirmShipment(shipmentOrderId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("출고 확정 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("출고 확정 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("출고 확정 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * GET /api/v1/shipment-orders/{id}
     * 출고 지시서 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(
        @PathVariable("id") UUID shipmentOrderId
    ) {
        try {
            log.info("GET /shipment-orders/{} - 출고 지시서 상세 조회 요청", shipmentOrderId);
            ShipmentOrderResponse response = shipmentOrderService.getShipmentOrder(shipmentOrderId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("출고 지시서 조회 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("출고 지시서 조회 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * GET /api/v1/shipment-orders
     * 출고 지시서 목록 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentOrderResponse>>> getAllShipmentOrders() {
        try {
            log.info("GET /shipment-orders - 출고 지시서 목록 조회 요청");
            List<ShipmentOrderResponse> responses = shipmentOrderService.getAllShipmentOrders();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            log.error("출고 지시서 목록 조회 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\StockTransferController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.ApprovalRequest;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.service.StockTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService stockTransferService;

    /**
     * POST /api/v1/stock-transfers
     * 재고 이동 실행
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> executeTransfer(
            @Valid @RequestBody StockTransferRequest request
    ) {
        try {
            log.info("POST /api/v1/stock-transfers - 재고 이동 요청");
            StockTransferResponse response = stockTransferService.executeTransfer(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("재고 이동 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("재고 이동 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("재고 이동 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * POST /api/v1/stock-transfers/{id}/approve
     * 대량 이동 승인
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable("id") UUID transferId,
            @Valid @RequestBody ApprovalRequest request
    ) {
        try {
            log.info("POST /api/v1/stock-transfers/{}/approve - 이동 승인 요청", transferId);
            StockTransferResponse response = stockTransferService.approveTransfer(transferId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("이동 승인 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("이동 승인 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("이동 승인 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * POST /api/v1/stock-transfers/{id}/reject
     * 대량 이동 거부
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable("id") UUID transferId,
            @Valid @RequestBody ApprovalRequest request
    ) {
        try {
            log.info("POST /api/v1/stock-transfers/{}/reject - 이동 거부 요청", transferId);
            StockTransferResponse response = stockTransferService.rejectTransfer(transferId, request);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("이동 거부 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("INVALID_REQUEST", e.getMessage()));
        } catch (IllegalStateException e) {
            log.error("이동 거부 실패 (비즈니스 규칙 위반): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("BUSINESS_RULE_VIOLATION", e.getMessage()));
        } catch (Exception e) {
            log.error("이동 거부 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * GET /api/v1/stock-transfers/{id}
     * 이동 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(
            @PathVariable("id") UUID transferId
    ) {
        try {
            log.info("GET /api/v1/stock-transfers/{} - 이동 상세 조회 요청", transferId);
            StockTransferResponse response = stockTransferService.getTransfer(transferId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (IllegalArgumentException e) {
            log.error("이동 조회 실패 (잘못된 요청): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            log.error("이동 조회 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * GET /api/v1/stock-transfers
     * 이동 이력 조회 (필터링 지원)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getTransfers(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID fromLocationId,
            @RequestParam(required = false) UUID toLocationId,
            @RequestParam(required = false) String status
    ) {
        try {
            log.info("GET /api/v1/stock-transfers - 이동 이력 조회 요청 (productId={}, fromLocationId={}, toLocationId={}, status={})",
                    productId, fromLocationId, toLocationId, status);
            List<StockTransferResponse> responses = stockTransferService.getTransfers(productId, fromLocationId, toLocationId, status);
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            log.error("이동 이력 조회 실패 (서버 오류)", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("INTERNAL_ERROR", "서버 오류가 발생했습니다"));
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
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorInfo error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorInfo {
        private String code;
        private String message;
        private Object details;
    }

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .build();
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .error(ErrorInfo.builder()
                .code(code)
                .message(message)
                .build())
            .build();
    }

    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        return ApiResponse.<T>builder()
            .success(false)
            .error(ErrorInfo.builder()
                .code(code)
                .message(message)
                .details(details)
                .build())
            .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ApprovalRequest.java
============================================================
package com.wms.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequest {

    private String approvedBy;
    private String rejectionReason;
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

    @NotNull(message = "PO ID는 필수입니다")
    private UUID poId;

    @NotBlank(message = "입고 담당자는 필수입니다")
    private String receivedBy;

    @NotEmpty(message = "입고 품목은 최소 1개 이상이어야 합니다")
    @Valid
    private List<InboundReceiptLineRequest> lines;

    @Data
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
// FILE: src\main\java\com\wms\dto\ShipmentOrderRequest.java
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
public class ShipmentOrderRequest {
    private String orderNumber;
    private String customerName;
    private OffsetDateTime requestedShipDate;
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

import com.wms.entity.ShipmentOrder;
import com.wms.entity.ShipmentOrderLine;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentOrderResponse {
    private UUID shipmentOrderId;
    private String orderNumber;
    private String customerName;
    private String status;
    private OffsetDateTime requestedShipDate;
    private OffsetDateTime shippedAt;
    private List<ShipmentOrderLineResponse> lines;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShipmentOrderLineResponse {
        private UUID shipmentOrderLineId;
        private UUID productId;
        private String productSku;
        private String productName;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        public static ShipmentOrderLineResponse fromEntity(ShipmentOrderLine line) {
            return ShipmentOrderLineResponse.builder()
                    .shipmentOrderLineId(line.getShipmentOrderLineId())
                    .productId(line.getProduct().getProductId())
                    .productSku(line.getProduct().getSku())
                    .productName(line.getProduct().getName())
                    .requestedQty(line.getRequestedQty())
                    .pickedQty(line.getPickedQty())
                    .status(line.getStatus().name())
                    .createdAt(line.getCreatedAt())
                    .updatedAt(line.getUpdatedAt())
                    .build();
        }
    }

    public static ShipmentOrderResponse fromEntity(ShipmentOrder order) {
        return ShipmentOrderResponse.builder()
                .shipmentOrderId(order.getShipmentOrderId())
                .orderNumber(order.getOrderNumber())
                .customerName(order.getCustomerName())
                .status(order.getStatus().name())
                .requestedShipDate(order.getRequestedShipDate())
                .shippedAt(order.getShippedAt())
                .lines(order.getLines().stream()
                        .map(ShipmentOrderLineResponse::fromEntity)
                        .collect(Collectors.toList()))
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
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

    private UUID productId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
    private String lotNumber;
    private String transferredBy;
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.StockTransfer;
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
    private String transferStatus;
    private String transferredBy;
    private OffsetDateTime transferredAt;
    private String approvedBy;
    private OffsetDateTime approvedAt;
    private String rejectionReason;

    public static StockTransferResponse from(StockTransfer transfer) {
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
                .transferStatus(transfer.getTransferStatus().name())
                .transferredBy(transfer.getTransferredBy())
                .transferredAt(transfer.getTransferredAt())
                .approvedBy(transfer.getApprovedBy())
                .approvedAt(transfer.getApprovedAt())
                .rejectionReason(transfer.getRejectionReason())
                .build();
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\AuditLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
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
    @Column(name = "audit_log_id")
    private UUID auditLogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "user_id")
    private UUID userId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public enum EventType {
        STORAGE_TYPE_MISMATCH,
        HIGH_VALUE_ADJUSTMENT,
        HAZMAT_VIOLATION,
        SYSTEM_WARNING
    }
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
    @Column(name = "auto_reorder_log_id")
    private UUID autoReorderLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false, length = 50)
    private TriggerReason triggerReason;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public enum TriggerReason {
        SAFETY_STOCK_TRIGGER, URGENT_REORDER
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\Backorder.java
============================================================
package com.wms.entity;

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
    @Column(name = "backorder_id")
    private UUID backorderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_order_line_id", nullable = false)
    private ShipmentOrderLine shipmentOrderLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "shortage_qty", nullable = false)
    private Integer shortageQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private BackorderStatus status = BackorderStatus.OPEN;

    @Column(name = "fulfilled_at")
    private OffsetDateTime fulfilledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum BackorderStatus {
        OPEN, FULFILLED, CANCELLED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceipt.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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

    @CreationTimestamp
    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "inboundReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InboundReceiptLine> lines = new ArrayList<>();

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
    @GeneratedValue(strategy = GenerationType.UUID)
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
    @UniqueConstraint(columnNames = {"product_id", "location_id", "lot_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\Location.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

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
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseOrderLine> lines = new ArrayList<>();

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
    @GeneratedValue(strategy = GenerationType.UUID)
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
@Table(name = "safety_stock_rules", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SafetyStockRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "safety_stock_rule_id")
    private UUID safetyStockRuleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "min_qty", nullable = false)
    private Integer minQty;

    @Column(name = "reorder_qty", nullable = false)
    private Integer reorderQty;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\SeasonalConfig.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrder.java
============================================================
package com.wms.entity;

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
    @Column(name = "shipment_order_id")
    private UUID shipmentOrderId;

    @Column(name = "order_number", unique = true, nullable = false, length = 50)
    private String orderNumber;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(name = "requested_ship_date")
    private OffsetDateTime requestedShipDate;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @OneToMany(mappedBy = "shipmentOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShipmentOrderLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum ShipmentStatus {
        PENDING, PICKING, PARTIAL, SHIPPED, CANCELLED
    }

    public void addLine(ShipmentOrderLine line) {
        lines.add(line);
        line.setShipmentOrder(this);
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrderLine.java
============================================================
package com.wms.entity;

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
    @Column(name = "shipment_order_line_id")
    private UUID shipmentOrderLineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_order_id", nullable = false)
    private ShipmentOrder shipmentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "requested_qty", nullable = false)
    private Integer requestedQty;

    @Column(name = "picked_qty", nullable = false)
    private Integer pickedQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private LineStatus status = LineStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public enum LineStatus {
        PENDING, PICKED, PARTIAL, BACKORDERED
    }
}


============================================================
// FILE: src\main\java\com\wms\entity\StockTransfer.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 20)
    private TransferStatus transferStatus = TransferStatus.immediate;

    @Column(name = "transferred_by", nullable = false, length = 100)
    private String transferredBy;

    @CreationTimestamp
    @Column(name = "transferred_at", nullable = false, updatable = false)
    private OffsetDateTime transferredAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    public enum TransferStatus {
        immediate, pending_approval, approved, rejected
    }
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
    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "contact_info", length = 500)
    private String contactInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status = SupplierStatus.active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

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
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public enum PenaltyType {
        OVER_DELIVERY, SHORT_SHELF_LIFE
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

import java.util.List;
import java.util.UUID;

@Repository
public interface InboundReceiptRepository extends JpaRepository<InboundReceipt, UUID> {
    List<InboundReceipt> findByPurchaseOrder_PoId(UUID poId);
}


============================================================
// FILE: src\main\java\com\wms\repository\InventoryRepository.java
============================================================
package com.wms.repository;

import com.wms.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
    Optional<Inventory> findByProduct_ProductIdAndLocation_LocationIdAndLotNumber(
        UUID productId, UUID locationId, String lotNumber
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

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    Optional<PurchaseOrderLine> findByPurchaseOrder_PoIdAndProduct_ProductId(UUID poId, UUID productId);
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

    @Query("SELECT po FROM PurchaseOrder po WHERE po.supplier.supplierId = :supplierId AND po.status = 'pending'")
    List<PurchaseOrder> findPendingOrdersBySupplierId(@Param("supplierId") UUID supplierId);
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
    Optional<SafetyStockRule> findByProduct_ProductIdAndIsActiveTrue(UUID productId);
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

import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    List<StockTransfer> findByProductProductIdOrderByTransferredAtDesc(UUID productId);

    List<StockTransfer> findByFromLocationLocationIdOrderByTransferredAtDesc(UUID locationId);

    List<StockTransfer> findByToLocationLocationIdOrderByTransferredAtDesc(UUID locationId);

    List<StockTransfer> findByTransferStatusOrderByTransferredAtDesc(StockTransfer.TransferStatus status);
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

    @Query("SELECT sp FROM SupplierPenalty sp WHERE sp.supplier.supplierId = :supplierId AND sp.createdAt >= :fromDate")
    List<SupplierPenalty> findBySupplierIdAndCreatedAtAfter(
        @Param("supplierId") UUID supplierId,
        @Param("fromDate") OffsetDateTime fromDate
    );
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
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    /**
     * 입고 등록 (inspecting 상태로 생성)
     */
    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        log.info("입고 등록 시작: PO ID = {}", request.getPoId());

        // 1. PO 조회 및 검증
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
            .orElseThrow(() -> new IllegalArgumentException("발주서를 찾을 수 없습니다: " + request.getPoId()));

        if (po.getStatus() == PurchaseOrder.PoStatus.cancelled) {
            throw new IllegalStateException("취소된 발주서에는 입고할 수 없습니다");
        }

        if (po.getStatus() == PurchaseOrder.PoStatus.hold) {
            throw new IllegalStateException("보류된 발주서에는 입고할 수 없습니다");
        }

        // 2. InboundReceipt 생성
        InboundReceipt receipt = InboundReceipt.builder()
            .purchaseOrder(po)
            .status(InboundReceipt.ReceiptStatus.inspecting)
            .receivedBy(request.getReceivedBy())
            .build();

        // 3. 각 라인별 검증 및 생성
        List<InboundReceiptLine> lines = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (InboundReceiptRequest.InboundReceiptLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineReq.getProductId()));

            Location location = locationRepository.findById(lineReq.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("로케이션을 찾을 수 없습니다: " + lineReq.getLocationId()));

            // 3-1. 실사 동결 체크 (ALS-WMS-INB-002 Constraint)
            if (Boolean.TRUE.equals(location.getIsFrozen())) {
                throw new IllegalStateException("실사 동결 중인 로케이션에는 입고할 수 없습니다: " + location.getCode());
            }

            // 3-2. 보관 유형 호환성 체크 (ALS-WMS-INB-002 Constraint)
            validateStorageTypeCompatibility(product, location);

            // 3-3. 유통기한 관리 상품 체크 (ALS-WMS-INB-002 Constraint)
            if (Boolean.TRUE.equals(product.getHasExpiry())) {
                if (lineReq.getExpiryDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 대상 상품은 유통기한이 필수입니다: " + product.getSku());
                }
                if (Boolean.TRUE.equals(product.getManufactureDateRequired()) && lineReq.getManufactureDate() == null) {
                    throw new IllegalArgumentException("유통기한 관리 대상 상품은 제조일이 필수입니다: " + product.getSku());
                }

                // 3-4. 유통기한 잔여율 체크 (ALS-WMS-INB-002 Constraint)
                double remainingPct = calculateRemainingShelfLife(
                    lineReq.getManufactureDate(),
                    lineReq.getExpiryDate(),
                    today
                );

                log.info("상품 {} 유통기한 잔여율: {}%", product.getSku(), remainingPct);

                if (remainingPct < product.getMinRemainingShelfLifePct()) {
                    // 유통기한 부족 -> 입고 거부 예정 (페널티 기록)
                    recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE, po.getPoId(),
                        String.format("유통기한 부족: %s (잔여율 %.1f%% < %d%%)",
                            product.getSku(), remainingPct, product.getMinRemainingShelfLifePct()));

                    throw new IllegalStateException(
                        String.format("유통기한 잔여율 부족으로 입고 거부: %s (잔여율 %.1f%% < %d%%)",
                            product.getSku(), remainingPct, product.getMinRemainingShelfLifePct()));
                }

                // 30% ~ 50% 구간: 경고 + 관리자 승인 필요
                if (remainingPct >= 30.0 && remainingPct <= 50.0) {
                    log.warn("유통기한 경고: {} (잔여율 {}% - 관리자 승인 필요)", product.getSku(), remainingPct);
                    receipt.setStatus(InboundReceipt.ReceiptStatus.pending_approval);
                }
            } else {
                // 유통기한 관리 비대상은 null로 저장
                if (lineReq.getExpiryDate() != null || lineReq.getManufactureDate() != null) {
                    throw new IllegalArgumentException("유통기한 관리 비대상 상품에는 유통기한을 입력할 수 없습니다: " + product.getSku());
                }
            }

            // 3-5. 초과입고 검증 (ALS-WMS-INB-002 Constraint)
            validateOverReceive(po, product, lineReq.getQuantity(), today);

            // 3-6. InboundReceiptLine 생성
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
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        log.info("입고 등록 완료: Receipt ID = {}, Status = {}", savedReceipt.getReceiptId(), savedReceipt.getStatus());

        return convertToResponse(savedReceipt);
    }

    /**
     * 입고 확정 (재고 반영)
     */
    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        log.info("입고 확정 시작: Receipt ID = {}", receiptId);

        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("검수 중이거나 승인 대기 상태만 확정할 수 있습니다");
        }

        // 1. 재고 반영
        for (InboundReceiptLine line : receipt.getLines()) {
            updateInventory(line);
        }

        // 2. PO 누적 수량 갱신 및 상태 변경
        updatePurchaseOrderStatus(receipt.getPurchaseOrder(), receipt.getLines());

        // 3. Receipt 상태 변경
        receipt.setStatus(InboundReceipt.ReceiptStatus.confirmed);
        receipt.setConfirmedAt(OffsetDateTime.now());

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        log.info("입고 확정 완료: Receipt ID = {}", receiptId);

        return convertToResponse(savedReceipt);
    }

    /**
     * 입고 거부
     */
    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        log.info("입고 거부 시작: Receipt ID = {}, Reason = {}", receiptId, reason);

        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.inspecting &&
            receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("검수 중이거나 승인 대기 상태만 거부할 수 있습니다");
        }

        receipt.setStatus(InboundReceipt.ReceiptStatus.rejected);

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        log.info("입고 거부 완료: Receipt ID = {}", receiptId);

        return convertToResponse(savedReceipt);
    }

    /**
     * 유통기한 경고 승인 (pending_approval -> confirmed)
     */
    @Transactional
    public InboundReceiptResponse approveInboundReceipt(UUID receiptId) {
        log.info("유통기한 경고 승인 시작: Receipt ID = {}", receiptId);

        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        if (receipt.getStatus() != InboundReceipt.ReceiptStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 상태만 승인할 수 있습니다");
        }

        // 승인 후 확정 처리
        return confirmInboundReceipt(receiptId);
    }

    /**
     * 입고 상세 조회
     */
    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
            .orElseThrow(() -> new IllegalArgumentException("입고 전표를 찾을 수 없습니다: " + receiptId));

        return convertToResponse(receipt);
    }

    /**
     * 입고 목록 조회
     */
    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        return inboundReceiptRepository.findAll().stream()
            .map(this::convertToResponse)
            .toList();
    }

    // ========== Private Helper Methods ==========

    /**
     * 보관 유형 호환성 검증 (ALS-WMS-INB-002 Constraint)
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        boolean compatible = switch (productType) {
            case FROZEN -> locationType == Product.StorageType.FROZEN;
            case COLD -> locationType == Product.StorageType.COLD || locationType == Product.StorageType.FROZEN;
            case AMBIENT -> locationType == Product.StorageType.AMBIENT;
        };

        // HAZMAT 상품은 HAZMAT zone만 허용
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new IllegalStateException(
                    String.format("HAZMAT 상품은 HAZMAT zone에만 입고할 수 있습니다: %s -> %s",
                        product.getSku(), location.getCode()));
            }
        }

        if (!compatible) {
            throw new IllegalStateException(
                String.format("보관 유형이 호환되지 않습니다: 상품(%s) %s -> 로케이션(%s) %s",
                    product.getSku(), productType, location.getCode(), locationType));
        }
    }

    /**
     * 유통기한 잔여율 계산
     */
    private double calculateRemainingShelfLife(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        if (manufactureDate == null || expiryDate == null) {
            throw new IllegalArgumentException("제조일과 유통기한은 필수입니다");
        }

        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            throw new IllegalArgumentException("유통기한이 제조일보다 빠릅니다");
        }

        return (double) remainingDays / totalDays * 100.0;
    }

    /**
     * 초과입고 검증 (ALS-WMS-INB-002 Constraint)
     */
    private void validateOverReceive(PurchaseOrder po, Product product, int inboundQty, LocalDate today) {
        // PO Line 조회
        PurchaseOrderLine poLine = purchaseOrderLineRepository
            .findByPurchaseOrder_PoIdAndProduct_ProductId(po.getPoId(), product.getProductId())
            .orElseThrow(() -> new IllegalArgumentException(
                "발주서에 해당 상품이 없습니다: " + product.getSku()));

        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int totalAfterInbound = receivedQty + inboundQty;

        // 카테고리별 기본 허용률
        double baseTolerance = switch (product.getCategory()) {
            case GENERAL -> 0.10;
            case FRESH -> 0.05;
            case HAZMAT -> 0.0;  // HAZMAT은 항상 0%
            case HIGH_VALUE -> 0.03;
        };

        // HAZMAT은 무조건 0%, 가중치 무시
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (totalAfterInbound > orderedQty) {
                recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY, po.getPoId(),
                    String.format("HAZMAT 초과입고: %s (발주 %d, 입고예정 %d)",
                        product.getSku(), orderedQty, totalAfterInbound));

                throw new IllegalStateException(
                    String.format("HAZMAT 상품은 초과입고가 불가능합니다: %s (발주 %d, 입고예정 %d)",
                        product.getSku(), orderedQty, totalAfterInbound));
            }
            return;  // HAZMAT은 여기서 종료
        }

        // PO 유형별 가중치
        double poTypeMultiplier = switch (po.getPoType()) {
            case NORMAL -> 1.0;
            case URGENT -> 2.0;
            case IMPORT -> 1.5;
        };

        // 성수기 가중치
        BigDecimal seasonMultiplier = seasonalConfigRepository.findActiveSeasonByDate(today)
            .map(SeasonalConfig::getMultiplier)
            .orElse(BigDecimal.ONE);

        // 최종 허용률 계산
        double finalTolerance = baseTolerance * poTypeMultiplier * seasonMultiplier.doubleValue();
        int maxAllowedQty = (int) Math.floor(orderedQty * (1.0 + finalTolerance));

        log.info("초과입고 검증: 상품={}, 카테고리={}, 기본허용률={}%, PO유형={}, 성수기배수={}, 최종허용률={}%, 최대허용={}",
            product.getSku(), product.getCategory(), baseTolerance * 100, po.getPoType(),
            seasonMultiplier, finalTolerance * 100, maxAllowedQty);

        if (totalAfterInbound > maxAllowedQty) {
            recordSupplierPenalty(po.getSupplier(), SupplierPenalty.PenaltyType.OVER_DELIVERY, po.getPoId(),
                String.format("초과입고: %s (발주 %d, 최대허용 %d, 입고예정 %d)",
                    product.getSku(), orderedQty, maxAllowedQty, totalAfterInbound));

            throw new IllegalStateException(
                String.format("초과입고 한도를 초과했습니다: %s (발주 %d, 최대허용 %d, 입고예정 %d)",
                    product.getSku(), orderedQty, maxAllowedQty, totalAfterInbound));
        }
    }

    /**
     * 공급업체 페널티 기록 및 자동 보류 처리
     */
    private void recordSupplierPenalty(Supplier supplier, SupplierPenalty.PenaltyType type, UUID poId, String description) {
        log.warn("공급업체 페널티 기록: Supplier={}, Type={}, PO={}, Desc={}",
            supplier.getName(), type, poId, description);

        // 페널티 기록
        SupplierPenalty penalty = SupplierPenalty.builder()
            .supplier(supplier)
            .penaltyType(type)
            .poId(poId)
            .description(description)
            .build();

        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 조회
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        List<SupplierPenalty> recentPenalties = supplierPenaltyRepository
            .findBySupplierIdAndCreatedAtAfter(supplier.getSupplierId(), thirtyDaysAgo);

        // 3회 이상이면 해당 공급업체의 모든 pending PO를 hold로 변경
        if (recentPenalties.size() >= 3) {
            log.warn("공급업체 페널티 3회 이상: Supplier={}, 최근 30일 페널티 {}회 - 모든 pending PO를 hold로 변경",
                supplier.getName(), recentPenalties.size());

            List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findPendingOrdersBySupplierId(supplier.getSupplierId());
            for (PurchaseOrder pendingPO : pendingPOs) {
                pendingPO.setStatus(PurchaseOrder.PoStatus.hold);
                purchaseOrderRepository.save(pendingPO);
                log.info("PO 보류 처리: PO Number={}", pendingPO.getPoNumber());
            }

            // 공급업체 상태도 hold로 변경
            supplier.setStatus(Supplier.SupplierStatus.hold);
        }
    }

    /**
     * 재고 반영 (inventory 테이블 및 location.current_qty 갱신)
     */
    private void updateInventory(InboundReceiptLine line) {
        Product product = line.getProduct();
        Location location = line.getLocation();

        // 로케이션 용량 체크
        if (location.getCurrentQty() + line.getQuantity() > location.getCapacity()) {
            throw new IllegalStateException(
                String.format("로케이션 용량 초과: %s (현재 %d, 추가 %d, 최대 %d)",
                    location.getCode(), location.getCurrentQty(), line.getQuantity(), location.getCapacity()));
        }

        // Inventory 레코드 조회 또는 생성
        Inventory inventory = inventoryRepository
            .findByProduct_ProductIdAndLocation_LocationIdAndLotNumber(
                product.getProductId(),
                location.getLocationId(),
                line.getLotNumber()
            )
            .orElse(Inventory.builder()
                .product(product)
                .location(location)
                .lotNumber(line.getLotNumber())
                .expiryDate(line.getExpiryDate())
                .manufactureDate(line.getManufactureDate())
                .receivedAt(OffsetDateTime.now())
                .quantity(0)
                .build());

        inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
        inventoryRepository.save(inventory);

        // 로케이션 현재 수량 갱신
        location.setCurrentQty(location.getCurrentQty() + line.getQuantity());
        locationRepository.save(location);

        log.info("재고 반영 완료: 상품={}, 로케이션={}, 수량={}", product.getSku(), location.getCode(), line.getQuantity());
    }

    /**
     * PO 상태 갱신 (received_qty 누적 및 상태 변경)
     */
    private void updatePurchaseOrderStatus(PurchaseOrder po, List<InboundReceiptLine> lines) {
        for (InboundReceiptLine line : lines) {
            PurchaseOrderLine poLine = purchaseOrderLineRepository
                .findByPurchaseOrder_PoIdAndProduct_ProductId(po.getPoId(), line.getProduct().getProductId())
                .orElseThrow(() -> new IllegalStateException("PO Line을 찾을 수 없습니다"));

            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }

        // 모든 라인 완납 여부 체크
        boolean allFullyReceived = po.getLines().stream()
            .allMatch(line -> line.getReceivedQty() >= line.getOrderedQty());

        boolean anyPartialReceived = po.getLines().stream()
            .anyMatch(line -> line.getReceivedQty() > 0 && line.getReceivedQty() < line.getOrderedQty());

        if (allFullyReceived) {
            po.setStatus(PurchaseOrder.PoStatus.completed);
            log.info("PO 완료 처리: PO Number={}", po.getPoNumber());
        } else if (anyPartialReceived) {
            po.setStatus(PurchaseOrder.PoStatus.partial);
            log.info("PO 부분입고 처리: PO Number={}", po.getPoNumber());
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * Entity -> DTO 변환
     */
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
            .toList();

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
// FILE: src\main\java\com\wms\service\ShipmentOrderService.java
============================================================
package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
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

    /**
     * 출고 지시서 생성
     */
    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        log.info("출고 지시서 생성 시작: orderNumber = {}", request.getOrderNumber());

        // 1. 중복 체크
        if (shipmentOrderRepository.findByOrderNumber(request.getOrderNumber()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 출고 지시서 번호입니다: " + request.getOrderNumber());
        }

        // 2. HAZMAT + FRESH 분리 출고 체크 (ALS-WMS-OUT-002 Constraint)
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> hazmatLines = new ArrayList<>();
        List<ShipmentOrderRequest.ShipmentOrderLineRequest> nonHazmatLines = new ArrayList<>();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineReq.getProductId()));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hazmatLines.add(lineReq);
            } else {
                nonHazmatLines.add(lineReq);
            }
        }

        // HAZMAT과 FRESH가 공존하는지 확인
        boolean hasFresh = nonHazmatLines.stream().anyMatch(lineReq -> {
            Product p = productRepository.findById(lineReq.getProductId()).orElse(null);
            return p != null && p.getCategory() == Product.ProductCategory.FRESH;
        });

        // HAZMAT + FRESH 분리 출고 처리
        if (!hazmatLines.isEmpty() && hasFresh) {
            log.info("HAZMAT + FRESH 분리 출고 처리");

            // 비-HAZMAT 출고 지시서 생성
            ShipmentOrder mainOrder = createOrder(request, nonHazmatLines);

            // HAZMAT 출고 지시서 별도 생성
            ShipmentOrderRequest hazmatRequest = ShipmentOrderRequest.builder()
                    .orderNumber(request.getOrderNumber() + "-HAZMAT")
                    .customerName(request.getCustomerName())
                    .requestedShipDate(request.getRequestedShipDate())
                    .lines(hazmatLines)
                    .build();
            createOrder(hazmatRequest, hazmatLines);

            return ShipmentOrderResponse.fromEntity(mainOrder);
        } else {
            // 일반 출고 지시서 생성
            ShipmentOrder order = createOrder(request, request.getLines());
            return ShipmentOrderResponse.fromEntity(order);
        }
    }

    private ShipmentOrder createOrder(ShipmentOrderRequest request,
                                      List<ShipmentOrderRequest.ShipmentOrderLineRequest> lines) {
        ShipmentOrder order = ShipmentOrder.builder()
                .orderNumber(request.getOrderNumber())
                .customerName(request.getCustomerName())
                .requestedShipDate(request.getRequestedShipDate())
                .status(ShipmentOrder.ShipmentStatus.PENDING)
                .build();

        for (ShipmentOrderRequest.ShipmentOrderLineRequest lineReq : lines) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + lineReq.getProductId()));

            ShipmentOrderLine line = ShipmentOrderLine.builder()
                    .product(product)
                    .requestedQty(lineReq.getRequestedQty())
                    .pickedQty(0)
                    .status(ShipmentOrderLine.LineStatus.PENDING)
                    .build();

            order.addLine(line);
        }

        return shipmentOrderRepository.save(order);
    }

    /**
     * 피킹 실행
     */
    @Transactional
    public ShipmentOrderResponse executePicking(UUID shipmentOrderId) {
        log.info("피킹 실행 시작: shipmentOrderId = {}", shipmentOrderId);

        ShipmentOrder order = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없습니다: " + shipmentOrderId));

        if (order.getStatus() != ShipmentOrder.ShipmentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 출고 지시서만 피킹할 수 있습니다");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.PICKING);

        for (ShipmentOrderLine line : order.getLines()) {
            pickLine(line);
        }

        // 모든 라인이 picked이면 shipped로 변경
        boolean allPicked = order.getLines().stream()
                .allMatch(line -> line.getStatus() == ShipmentOrderLine.LineStatus.PICKED);

        if (allPicked) {
            order.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
        } else {
            order.setStatus(ShipmentOrder.ShipmentStatus.PARTIAL);
        }

        return ShipmentOrderResponse.fromEntity(order);
    }

    private void pickLine(ShipmentOrderLine line) {
        Product product = line.getProduct();
        Integer requestedQty = line.getRequestedQty();

        log.info("라인 피킹 시작: product = {}, requestedQty = {}", product.getSku(), requestedQty);

        // 1. 피킹 가능한 재고 조회 (FIFO/FEFO 적용, ALS-WMS-OUT-002 Constraint)
        List<Inventory> pickableInventories = getPickableInventories(product);

        // 2. 가용 재고 합산
        int availableQty = pickableInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        log.info("가용 재고: {}, 요청 수량: {}", availableQty, requestedQty);

        // 3. 부분출고 의사결정 트리 (ALS-WMS-OUT-002 Constraint)
        double availableRatio = (double) availableQty / requestedQty;

        if (availableQty == 0) {
            // 전량 백오더
            log.info("재고 없음 -> 전량 백오더");
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);
            createBackorder(line, requestedQty);
            return;
        }

        if (availableRatio < 0.3) {
            // 가용 < 30% -> 전량 백오더 (부분출고 안 함)
            log.info("가용 재고 < 30% -> 전량 백오더");
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);
            createBackorder(line, requestedQty);
            return;
        }

        // 4. 실제 피킹 수행
        int pickedQty = performPicking(pickableInventories, requestedQty, product);
        line.setPickedQty(pickedQty);

        if (pickedQty >= requestedQty) {
            // 완전 피킹
            line.setStatus(ShipmentOrderLine.LineStatus.PICKED);
        } else {
            // 부분 피킹
            line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);
            int shortageQty = requestedQty - pickedQty;
            createBackorder(line, shortageQty);

            // 30% <= 가용 < 70% -> 긴급발주 트리거
            if (availableRatio >= 0.3 && availableRatio < 0.7) {
                log.info("가용 재고 30%~70% -> 긴급발주 트리거");
                createAutoReorderLog(product, AutoReorderLog.TriggerReason.URGENT_REORDER, availableQty);
            }
        }

        // 5. 출고 후 안전재고 체크 (ALS-WMS-OUT-002 Constraint)
        checkSafetyStock(product);
    }

    private List<Inventory> getPickableInventories(Product product) {
        LocalDate today = LocalDate.now();

        // 피킹 가능 조건:
        // - is_expired = false
        // - is_frozen = false (로케이션)
        // - expiry_date >= today (만료되지 않음)
        // - 잔여 유통기한 >= 10% (ALS-WMS-OUT-002 Constraint)
        List<Inventory> inventories = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> !inv.getIsExpired())
                .filter(inv -> !inv.getLocation().getIsFrozen())
                .filter(inv -> inv.getQuantity() > 0)
                .collect(Collectors.toList());

        // 만료 재고 제외 및 잔여율 10% 미만 체크
        inventories = inventories.stream()
                .filter(inv -> {
                    if (Boolean.TRUE.equals(product.getHasExpiry())) {
                        if (inv.getExpiryDate() != null && inv.getExpiryDate().isBefore(today)) {
                            return false; // 만료 제외
                        }

                        // 잔여율 10% 미만 체크
                        if (inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                            double remainingPct = calculateRemainingShelfLife(
                                    inv.getManufactureDate(), inv.getExpiryDate(), today);
                            if (remainingPct < 10.0) {
                                // 피킹 불가 -> is_expired = true로 설정
                                log.warn("잔여 유통기한 < 10% -> 피킹 불가 (폐기 전환): inventory = {}", inv.getInventoryId());
                                inv.setIsExpired(true);
                                inventoryRepository.save(inv);
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // HAZMAT zone 체크
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            inventories = inventories.stream()
                    .filter(inv -> inv.getLocation().getZone() == Location.Zone.HAZMAT)
                    .collect(Collectors.toList());
        }

        // FIFO/FEFO 정렬 (ALS-WMS-OUT-002 Constraint)
        if (Boolean.TRUE.equals(product.getHasExpiry())) {
            // 잔여율 < 30% 최우선 출고
            inventories.sort((inv1, inv2) -> {
                LocalDate exp1 = inv1.getExpiryDate();
                LocalDate exp2 = inv2.getExpiryDate();
                LocalDate mfg1 = inv1.getManufactureDate();
                LocalDate mfg2 = inv2.getManufactureDate();

                if (exp1 == null || mfg1 == null) return 1;
                if (exp2 == null || mfg2 == null) return -1;

                double rem1 = calculateRemainingShelfLife(mfg1, exp1, today);
                double rem2 = calculateRemainingShelfLife(mfg2, exp2, today);

                boolean isPriority1 = rem1 < 30.0;
                boolean isPriority2 = rem2 < 30.0;

                if (isPriority1 && !isPriority2) return -1;
                if (!isPriority1 && isPriority2) return 1;

                // 둘 다 <30% 또는 둘 다 >=30% -> FEFO → FIFO
                int expCompare = exp1.compareTo(exp2);
                if (expCompare != 0) return expCompare;
                return inv1.getReceivedAt().compareTo(inv2.getReceivedAt());
            });
        } else {
            // FIFO
            inventories.sort(Comparator.comparing(Inventory::getReceivedAt));
        }

        return inventories;
    }

    private int performPicking(List<Inventory> inventories, int requestedQty, Product product) {
        int pickedQty = 0;
        Integer maxPickQty = product.getMaxPickQty();

        for (Inventory inventory : inventories) {
            if (pickedQty >= requestedQty) {
                break;
            }

            int needQty = requestedQty - pickedQty;

            // HAZMAT max_pick_qty 체크 (ALS-WMS-OUT-002 Constraint)
            if (product.getCategory() == Product.ProductCategory.HAZMAT && maxPickQty != null) {
                needQty = Math.min(needQty, maxPickQty - pickedQty);
                if (needQty <= 0) {
                    log.warn("HAZMAT max_pick_qty 초과 -> 피킹 중단: product = {}, maxPickQty = {}", product.getSku(), maxPickQty);
                    break;
                }
            }

            int pickQty = Math.min(inventory.getQuantity(), needQty);

            // inventory 차감
            inventory.setQuantity(inventory.getQuantity() - pickQty);
            inventoryRepository.save(inventory);

            // location current_qty 차감
            Location location = inventory.getLocation();
            location.setCurrentQty(location.getCurrentQty() - pickQty);
            locationRepository.save(location);

            // 보관 유형 불일치 경고 (ALS-WMS-OUT-002 Constraint)
            if (inventory.getLocation().getStorageType() != product.getStorageType()) {
                log.warn("보관 유형 불일치: location = {}, product = {}", location.getCode(), product.getSku());
                createAuditLog(AuditLog.EventType.STORAGE_TYPE_MISMATCH,
                        "Inventory", inventory.getInventoryId(),
                        String.format("보관 유형 불일치: location=%s (type=%s), product=%s (type=%s)",
                                location.getCode(), location.getStorageType(),
                                product.getSku(), product.getStorageType()));
            }

            pickedQty += pickQty;
        }

        return pickedQty;
    }

    private void createBackorder(ShipmentOrderLine line, int shortageQty) {
        Backorder backorder = Backorder.builder()
                .shipmentOrderLine(line)
                .product(line.getProduct())
                .shortageQty(shortageQty)
                .status(Backorder.BackorderStatus.OPEN)
                .build();
        backorderRepository.save(backorder);
    }

    private void checkSafetyStock(Product product) {
        Optional<SafetyStockRule> ruleOpt = safetyStockRuleRepository.findByProduct_ProductIdAndIsActiveTrue(product.getProductId());
        if (ruleOpt.isEmpty()) {
            return;
        }

        SafetyStockRule rule = ruleOpt.get();

        // 전체 가용 재고 합산 (is_expired = false)
        int totalAvailableQty = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getProduct().getProductId().equals(product.getProductId()))
                .filter(inv -> !inv.getIsExpired())
                .mapToInt(Inventory::getQuantity)
                .sum();

        log.info("안전재고 체크: product = {}, totalAvailableQty = {}, minQty = {}", product.getSku(), totalAvailableQty, rule.getMinQty());

        if (totalAvailableQty <= rule.getMinQty()) {
            log.warn("안전재고 미달 -> 자동 재발주 트리거: product = {}", product.getSku());
            createAutoReorderLog(product, AutoReorderLog.TriggerReason.SAFETY_STOCK_TRIGGER, totalAvailableQty);
        }
    }

    private void createAutoReorderLog(Product product, AutoReorderLog.TriggerReason reason, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductIdAndIsActiveTrue(product.getProductId())
                .orElse(null);

        int reorderQty = (rule != null) ? rule.getReorderQty() : 0;

        AutoReorderLog log = AutoReorderLog.builder()
                .product(product)
                .triggerReason(reason)
                .reorderQty(reorderQty)
                .currentStock(currentStock)
                .build();
        autoReorderLogRepository.save(log);
    }

    private void createAuditLog(AuditLog.EventType eventType, String entityType, UUID entityId, String description) {
        AuditLog log = AuditLog.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .build();
        auditLogRepository.save(log);
    }

    private double calculateRemainingShelfLife(LocalDate manufactureDate, LocalDate expiryDate, LocalDate today) {
        if (manufactureDate == null || expiryDate == null) {
            return 100.0;
        }
        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
        if (totalDays <= 0) {
            return 0.0;
        }
        return (double) remainingDays / totalDays * 100.0;
    }

    /**
     * 출고 확정
     */
    @Transactional
    public ShipmentOrderResponse confirmShipment(UUID shipmentOrderId) {
        log.info("출고 확정 시작: shipmentOrderId = {}", shipmentOrderId);

        ShipmentOrder order = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없습니다: " + shipmentOrderId));

        if (order.getStatus() == ShipmentOrder.ShipmentStatus.SHIPPED) {
            throw new IllegalStateException("이미 출고 완료된 지시서입니다");
        }

        order.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
        order.setShippedAt(OffsetDateTime.now());

        return ShipmentOrderResponse.fromEntity(order);
    }

    /**
     * 출고 지시서 상세 조회
     */
    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentOrderId) {
        ShipmentOrder order = shipmentOrderRepository.findById(shipmentOrderId)
                .orElseThrow(() -> new IllegalArgumentException("출고 지시서를 찾을 수 없습니다: " + shipmentOrderId));
        return ShipmentOrderResponse.fromEntity(order);
    }

    /**
     * 출고 지시서 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        return shipmentOrderRepository.findAll().stream()
                .map(ShipmentOrderResponse::fromEntity)
                .collect(Collectors.toList());
    }
}


============================================================
// FILE: src\main\java\com\wms\service\StockTransferService.java
============================================================
package com.wms.service;

import com.wms.dto.ApprovalRequest;
import com.wms.dto.StockTransferRequest;
import com.wms.dto.StockTransferResponse;
import com.wms.entity.*;
import com.wms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    /**
     * 재고 이동 실행 (ALS-WMS-STK-002 규칙 준수)
     */
    @Transactional
    public StockTransferResponse executeTransfer(StockTransferRequest request) {
        log.info("재고 이동 시작: product={}, from={}, to={}, qty={}",
                request.getProductId(), request.getFromLocationId(), request.getToLocationId(), request.getQuantity());

        // 1. 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + request.getProductId()));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new IllegalArgumentException("출발지 로케이션을 찾을 수 없습니다: " + request.getFromLocationId()));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new IllegalArgumentException("도착지 로케이션을 찾을 수 없습니다: " + request.getToLocationId()));

        // 2. 기본 검증 (ALS-WMS-STK-002 Constraints - 기본 규칙)
        if (fromLocation.getLocationId().equals(toLocation.getLocationId())) {
            throw new IllegalArgumentException("출발지와 도착지가 동일합니다");
        }

        // 3. 실사 동결 체크 (ALS-WMS-STK-002 Constraints - Level 2)
        if (Boolean.TRUE.equals(fromLocation.getIsFrozen())) {
            throw new IllegalStateException("실사 동결 중인 로케이션에서는 이동할 수 없습니다: " + fromLocation.getCode());
        }

        if (Boolean.TRUE.equals(toLocation.getIsFrozen())) {
            throw new IllegalStateException("실사 동결 중인 로케이션으로는 이동할 수 없습니다: " + toLocation.getCode());
        }

        // 4. 출발지 재고 조회 및 수량 검증
        Inventory sourceInventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(product, fromLocation, request.getLotNumber())
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("출발지에 해당 재고가 없습니다: product=%s, location=%s, lot=%s",
                                product.getSku(), fromLocation.getCode(), request.getLotNumber())));

        if (sourceInventory.getQuantity() < request.getQuantity()) {
            throw new IllegalArgumentException(
                    String.format("출발지 재고 부족: 요청=%d, 가용=%d", request.getQuantity(), sourceInventory.getQuantity()));
        }

        // 5. 보관 유형 호환성 검증 (ALS-WMS-STK-002 Constraints - Level 2)
        validateStorageTypeCompatibility(product, toLocation);

        // 6. 위험물 혼적 금지 검증 (ALS-WMS-STK-002 Constraints - Level 2)
        validateHazmatMixing(product, toLocation);

        // 7. 유통기한 이동 제한 검증 (ALS-WMS-STK-002 Constraints - Level 2)
        if (Boolean.TRUE.equals(product.getHasExpiry()) && sourceInventory.getExpiryDate() != null) {
            validateExpiryDateRestriction(sourceInventory, toLocation);
        }

        // 8. 대량 이동 승인 체크 (ALS-WMS-STK-002 Constraints - Level 2)
        boolean isLargeTransfer = (request.getQuantity() * 100.0 / sourceInventory.getQuantity()) >= 80.0;

        StockTransfer transfer = StockTransfer.builder()
                .product(product)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .quantity(request.getQuantity())
                .lotNumber(request.getLotNumber())
                .transferredBy(request.getTransferredBy())
                .transferStatus(isLargeTransfer ? StockTransfer.TransferStatus.pending_approval : StockTransfer.TransferStatus.immediate)
                .build();

        stockTransferRepository.save(transfer);

        // 9. 즉시 이동 또는 승인 대기
        if (!isLargeTransfer) {
            executeActualTransfer(transfer, sourceInventory, toLocation);
            log.info("즉시 이동 완료: transferId={}", transfer.getTransferId());
        } else {
            log.info("대량 이동 승인 대기: transferId={}, 비율={}%",
                    transfer.getTransferId(), request.getQuantity() * 100.0 / sourceInventory.getQuantity());
        }

        return StockTransferResponse.from(transfer);
    }

    /**
     * 대량 이동 승인
     */
    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, ApprovalRequest request) {
        log.info("재고 이동 승인 시작: transferId={}", transferId);

        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("이동 이력을 찾을 수 없습니다: " + transferId));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 중인 이동이 아닙니다: " + transfer.getTransferStatus());
        }

        // 출발지 재고 조회
        Inventory sourceInventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(
                        transfer.getProduct(),
                        transfer.getFromLocation(),
                        transfer.getLotNumber())
                .orElseThrow(() -> new IllegalStateException("출발지 재고가 존재하지 않습니다"));

        // 재검증
        if (sourceInventory.getQuantity() < transfer.getQuantity()) {
            throw new IllegalStateException("출발지 재고가 부족하여 승인할 수 없습니다");
        }

        // 실제 이동 실행
        executeActualTransfer(transfer, sourceInventory, transfer.getToLocation());

        // 승인 정보 업데이트
        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(request.getApprovedBy());
        transfer.setApprovedAt(OffsetDateTime.now());

        stockTransferRepository.save(transfer);
        log.info("재고 이동 승인 완료: transferId={}", transferId);

        return StockTransferResponse.from(transfer);
    }

    /**
     * 대량 이동 거부
     */
    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, ApprovalRequest request) {
        log.info("재고 이동 거부 시작: transferId={}", transferId);

        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("이동 이력을 찾을 수 없습니다: " + transferId));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new IllegalStateException("승인 대기 중인 이동이 아닙니다: " + transfer.getTransferStatus());
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(request.getApprovedBy());
        transfer.setApprovedAt(OffsetDateTime.now());
        transfer.setRejectionReason(request.getRejectionReason());

        stockTransferRepository.save(transfer);
        log.info("재고 이동 거부 완료: transferId={}", transferId);

        return StockTransferResponse.from(transfer);
    }

    /**
     * 이동 상세 조회
     */
    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("이동 이력을 찾을 수 없습니다: " + transferId));

        return StockTransferResponse.from(transfer);
    }

    /**
     * 이동 이력 조회
     */
    @Transactional(readOnly = true)
    public List<StockTransferResponse> getTransfers(UUID productId, UUID fromLocationId, UUID toLocationId, String status) {
        List<StockTransfer> transfers;

        if (productId != null) {
            transfers = stockTransferRepository.findByProductProductIdOrderByTransferredAtDesc(productId);
        } else if (fromLocationId != null) {
            transfers = stockTransferRepository.findByFromLocationLocationIdOrderByTransferredAtDesc(fromLocationId);
        } else if (toLocationId != null) {
            transfers = stockTransferRepository.findByToLocationLocationIdOrderByTransferredAtDesc(toLocationId);
        } else if (status != null) {
            StockTransfer.TransferStatus transferStatus = StockTransfer.TransferStatus.valueOf(status);
            transfers = stockTransferRepository.findByTransferStatusOrderByTransferredAtDesc(transferStatus);
        } else {
            transfers = stockTransferRepository.findAll();
        }

        return transfers.stream()
                .map(StockTransferResponse::from)
                .collect(Collectors.toList());
    }

    // ========== 내부 메서드 ==========

    /**
     * 실제 이동 실행 (트랜잭션 내)
     * ALS-WMS-STK-002 Rule: 출발지 차감 + 도착지 증가를 단일 트랜잭션으로 처리
     */
    private void executeActualTransfer(StockTransfer transfer, Inventory sourceInventory, Location toLocation) {
        Product product = transfer.getProduct();
        Integer transferQty = transfer.getQuantity();

        // 1. 출발지 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - transferQty);
        inventoryRepository.save(sourceInventory);

        Location fromLocation = transfer.getFromLocation();
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - transferQty);
        locationRepository.save(fromLocation);

        log.info("출발지 차감 완료: location={}, qty={}", fromLocation.getCode(), transferQty);

        // 2. 도착지 증가
        // 2-1. 도착지 용량 체크 (ALS-WMS-STK-002 Constraints)
        if (toLocation.getCurrentQty() + transferQty > toLocation.getCapacity()) {
            throw new IllegalStateException(
                    String.format("도착지 용량 초과: 현재=%d, 이동=%d, 용량=%d",
                            toLocation.getCurrentQty(), transferQty, toLocation.getCapacity()));
        }

        // 2-2. 도착지에 동일 상품+lot 조합 확인
        Inventory destInventory = inventoryRepository
                .findByProductAndLocationAndLotNumber(product, toLocation, transfer.getLotNumber())
                .orElse(null);

        if (destInventory != null) {
            // 기존 재고 증가
            destInventory.setQuantity(destInventory.getQuantity() + transferQty);
            inventoryRepository.save(destInventory);
            log.info("도착지 기존 재고 증가: location={}, qty={}", toLocation.getCode(), transferQty);
        } else {
            // 새 재고 생성 (received_at은 원래 값 유지)
            Inventory newInventory = Inventory.builder()
                    .product(product)
                    .location(toLocation)
                    .quantity(transferQty)
                    .lotNumber(transfer.getLotNumber())
                    .expiryDate(sourceInventory.getExpiryDate())
                    .manufactureDate(sourceInventory.getManufactureDate())
                    .receivedAt(sourceInventory.getReceivedAt())
                    .isExpired(sourceInventory.getIsExpired())
                    .build();
            inventoryRepository.save(newInventory);
            log.info("도착지 신규 재고 생성: location={}, qty={}", toLocation.getCode(), transferQty);
        }

        toLocation.setCurrentQty(toLocation.getCurrentQty() + transferQty);
        locationRepository.save(toLocation);

        // 3. 안전재고 체크 (ALS-WMS-STK-002 Constraints - Level 2)
        checkSafetyStock(product);
    }

    /**
     * 보관 유형 호환성 검증 (ALS-WMS-STK-002 Constraints - Level 2)
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Product.StorageType locationType = location.getStorageType();

        // FROZEN 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.FROZEN && locationType == Product.StorageType.AMBIENT) {
            throw new IllegalArgumentException("FROZEN 상품은 AMBIENT 로케이션으로 이동할 수 없습니다");
        }

        // COLD 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.COLD && locationType == Product.StorageType.AMBIENT) {
            throw new IllegalArgumentException("COLD 상품은 AMBIENT 로케이션으로 이동할 수 없습니다");
        }

        // HAZMAT 상품 → 비-HAZMAT zone 로케이션: 거부
        if (product.getCategory() == Product.ProductCategory.HAZMAT && location.getZone() != Location.Zone.HAZMAT) {
            throw new IllegalArgumentException("HAZMAT 상품은 HAZMAT zone으로만 이동할 수 있습니다");
        }

        // AMBIENT 상품 → COLD/FROZEN 로케이션: 허용 (상위 호환)
        log.info("보관 유형 호환성 검증 통과: product={}, location={}", productType, locationType);
    }

    /**
     * 위험물 혼적 금지 검증 (ALS-WMS-STK-002 Constraints - Level 2)
     */
    private void validateHazmatMixing(Product product, Location toLocation) {
        List<Inventory> existingInventories = inventoryRepository.findByLocation(toLocation);

        if (existingInventories.isEmpty()) {
            return; // 도착지가 비어있으면 OK
        }

        boolean isHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;

        for (Inventory inv : existingInventories) {
            boolean existingIsHazmat = inv.getProduct().getCategory() == Product.ProductCategory.HAZMAT;

            if (isHazmat && !existingIsHazmat) {
                throw new IllegalArgumentException(
                        "비-HAZMAT 상품이 이미 적재된 로케이션에 HAZMAT 상품을 이동할 수 없습니다: " + toLocation.getCode());
            }

            if (!isHazmat && existingIsHazmat) {
                throw new IllegalArgumentException(
                        "HAZMAT 상품이 이미 적재된 로케이션에 비-HAZMAT 상품을 이동할 수 없습니다: " + toLocation.getCode());
            }
        }

        log.info("위험물 혼적 검증 통과: product={}, location={}", product.getSku(), toLocation.getCode());
    }

    /**
     * 유통기한 이동 제한 검증 (ALS-WMS-STK-002 Constraints - Level 2)
     */
    private void validateExpiryDateRestriction(Inventory sourceInventory, Location toLocation) {
        LocalDate expiryDate = sourceInventory.getExpiryDate();
        LocalDate manufactureDate = sourceInventory.getManufactureDate();
        LocalDate today = LocalDate.now();

        // 유통기한 만료: 이동 불가
        if (expiryDate.isBefore(today)) {
            throw new IllegalArgumentException("유통기한이 만료된 재고는 이동할 수 없습니다");
        }

        // 잔여 유통기한 < 10%: SHIPPING zone으로만 허용
        if (manufactureDate != null) {
            long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);
            double remainingPct = (remainingDays * 100.0) / totalDays;

            log.info("유통기한 잔여율: {}%", remainingPct);

            if (remainingPct < 10.0) {
                if (toLocation.getZone() != Location.Zone.SHIPPING) {
                    throw new IllegalArgumentException(
                            "잔여 유통기한이 10% 미만인 상품은 SHIPPING zone으로만 이동할 수 있습니다");
                }
            }
        }

        log.info("유통기한 제한 검증 통과");
    }

    /**
     * 안전재고 체크 (ALS-WMS-STK-002 Constraints - Level 2)
     * 이동 후 STORAGE zone 내 전체 재고 확인
     */
    private void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct(product).orElse(null);

        if (rule == null) {
            return; // 안전재고 규칙 없으면 체크 안 함
        }

        // STORAGE zone 내 전체 재고 합산
        List<Location> storageLocations = locationRepository.findByZone(Location.Zone.STORAGE);
        int totalQty = 0;

        for (Location loc : storageLocations) {
            List<Inventory> inventories = inventoryRepository.findByProductAndLocation(product, loc);
            totalQty += inventories.stream()
                    .filter(inv -> !Boolean.TRUE.equals(inv.getIsExpired()))
                    .mapToInt(Inventory::getQuantity)
                    .sum();
        }

        log.info("STORAGE zone 전체 재고: product={}, qty={}, min_qty={}", product.getSku(), totalQty, rule.getMinQty());

        if (totalQty <= rule.getMinQty()) {
            // 안전재고 미달 -> 자동 재발주 요청 기록
            AutoReorderLog reorderLog = AutoReorderLog.builder()
                    .product(product)
                    .triggerReason("SAFETY_STOCK_TRIGGER")
                    .currentQty(totalQty)
                    .safetyStockQty(rule.getMinQty())
                    .reorderQty(rule.getReorderQty())
                    .build();
            autoReorderLogRepository.save(reorderLog);

            log.warn("안전재고 미달 감지 -> 자동 재발주 요청 기록: product={}, reorder_qty={}",
                    product.getSku(), rule.getReorderQty());
        }
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
    name: wms-inbound

  datasource:
    url: jdbc:postgresql://localhost:5432/wms_db
    username: wms_user
    password: wms_pass
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        use_sql_comments: true
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

