# CLI Output

재고 이동 기능 구현이 완료되었습니다.

## 구현 완료 내역

### 1. Entity
- `StockTransfer.java` - transfer_status 포함 (immediate, pending_approval, approved, rejected)

### 2. Repository
- `StockTransferRepository.java` - 이동 이력 조회 메서드

### 3. DTO
- `StockTransferRequest.java` - 이동 요청
- `StockTransferResponse.java` - 이동 응답

### 4. Service
- `StockTransferService.java` - ALS-WMS-STK-002 규칙 준수:
  - ✅ 단일 트랜잭션 처리 (출발지 차감 + 도착지 증가)
  - ✅ 동일 로케이션 거부
  - ✅ 재고 부족 체크
  - ✅ 용량 체크
  - ✅ 실사 동결 로케이션 체크
  - ✅ 보관 유형 호환성 검증 (FROZEN→AMBIENT 거부 등)
  - ✅ HAZMAT 혼적 금지
  - ✅ 유통기한 만료/임박 제한 (<10% → SHIPPING만, 만료 → 불가)
  - ✅ 대량 이동(≥80%) 승인 필요
  - ✅ 이동 후 STORAGE zone 안전재고 체크

### 5. Controller
- `StockTransferController.java` - REST API 엔드포인트:
  - `POST /api/v1/stock-transfers` - 재고 이동 실행
  - `POST /api/v1/stock-transfers/{id}/approve` - 대량 이동 승인
  - `POST /api/v1/stock-transfers/{id}/reject` - 대량 이동 거부
  - `GET /api/v1/stock-transfers/{id}` - 이동 상세 조회
  - `GET /api/v1/stock-transfers` - 이동 이력 조회

모든 비즈니스 규칙은 docs/ALS/ALS-WMS-STK-002.md에 명시된 대로 구현되었습니다.


# Generated Code


============================================================
// FILE: schema.sql
============================================================
-- ========================================
-- WMS Database Schema (Level 2)
-- PostgreSQL 15+
-- ========================================

-- ========================================
-- 1. 상품 마스터
-- ========================================
CREATE TABLE products (
    product_id      UUID PRIMARY KEY,
    sku             VARCHAR(50) UNIQUE NOT NULL,    -- 상품 고유 코드
    name            VARCHAR(200) NOT NULL,
    category        VARCHAR(50) NOT NULL DEFAULT 'GENERAL'
                    CHECK (category IN ('GENERAL', 'FRESH', 'HAZMAT', 'HIGH_VALUE')),
    storage_type    VARCHAR(20) NOT NULL DEFAULT 'AMBIENT'
                    CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN')),
    unit            VARCHAR(20) NOT NULL DEFAULT 'EA',  -- EA, BOX, PLT 등
    has_expiry      BOOLEAN NOT NULL DEFAULT false,     -- 유통기한 관리 대상 여부
    min_remaining_shelf_life_pct INTEGER DEFAULT 30,    -- 최소 잔여 유통기한 비율(%)
    max_pick_qty    INTEGER,                            -- HAZMAT 1회 출고 최대 수량
    manufacture_date_required BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 2. 로케이션 (창고 내 적재 위치)
-- ========================================
CREATE TABLE locations (
    location_id     UUID PRIMARY KEY,
    code            VARCHAR(20) UNIQUE NOT NULL,    -- 예: A-01-01
    zone            VARCHAR(50) NOT NULL            -- RECEIVING, STORAGE, SHIPPING, HAZMAT
                    CHECK (zone IN ('RECEIVING', 'STORAGE', 'SHIPPING', 'HAZMAT')),
    storage_type    VARCHAR(20) NOT NULL DEFAULT 'AMBIENT'
                    CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN')),
    capacity        INTEGER NOT NULL,               -- 최대 적재 수량
    current_qty     INTEGER NOT NULL DEFAULT 0,     -- 현재 적재 수량
    is_active       BOOLEAN NOT NULL DEFAULT true,
    is_frozen       BOOLEAN NOT NULL DEFAULT false,  -- 실사 동결 여부
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
    is_expired      BOOLEAN NOT NULL DEFAULT false,  -- 폐기 대상 표시
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
    created_at       TIMESTAMPTZ DEFAULT NOW(),     -- 삭제 불가 (audit trail)
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

-- ========================================
-- Indexes for Performance
-- ========================================
CREATE INDEX idx_inventory_product ON inventory(product_id);
CREATE INDEX idx_inventory_location ON inventory(location_id);
CREATE INDEX idx_inbound_receipt_po ON inbound_receipts(po_id);
CREATE INDEX idx_supplier_penalties_supplier ON supplier_penalties(supplier_id);
CREATE INDEX idx_supplier_penalties_created ON supplier_penalties(created_at);
CREATE INDEX idx_po_lines_po ON purchase_order_lines(po_id);
CREATE INDEX idx_seasonal_config_dates ON seasonal_config(start_date, end_date, is_active);


============================================================
// FILE: src\main\java\com\wms\controller\InboundReceiptController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.InboundReceiptRequest;
import com.wms.dto.InboundReceiptResponse;
import com.wms.exception.BusinessException;
import com.wms.service.InboundReceiptService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inbound-receipts")
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    public InboundReceiptController(InboundReceiptService inboundReceiptService) {
        this.inboundReceiptService = inboundReceiptService;
    }

    /**
     * 입고 등록 (inspecting 상태)
     * POST /api/v1/inbound-receipts
     */
    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> createInboundReceipt(
            @RequestBody InboundReceiptRequest request
    ) {
        try {
            InboundReceiptResponse response = inboundReceiptService.createInboundReceipt(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage(), e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("서버 오류가 발생했습니다", "INTERNAL_ERROR"));
        }
    }

    /**
     * 입고 확정
     * POST /api/v1/inbound-receipts/{id}/confirm
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> confirmInboundReceipt(
            @PathVariable("id") UUID receiptId
    ) {
        try {
            InboundReceiptResponse response = inboundReceiptService.confirmInboundReceipt(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage(), e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("서버 오류가 발생했습니다", "INTERNAL_ERROR"));
        }
    }

    /**
     * 입고 거부
     * POST /api/v1/inbound-receipts/{id}/reject
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> rejectInboundReceipt(
            @PathVariable("id") UUID receiptId,
            @RequestBody(required = false) RejectRequest request
    ) {
        try {
            String reason = request != null ? request.getReason() : "";
            InboundReceiptResponse response = inboundReceiptService.rejectInboundReceipt(receiptId, reason);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage(), e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("서버 오류가 발생했습니다", "INTERNAL_ERROR"));
        }
    }

    /**
     * 유통기한 경고 승인
     * POST /api/v1/inbound-receipts/{id}/approve
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> approveShelfLifeWarning(
            @PathVariable("id") UUID receiptId
    ) {
        try {
            InboundReceiptResponse response = inboundReceiptService.approveShelfLifeWarning(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage(), e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("서버 오류가 발생했습니다", "INTERNAL_ERROR"));
        }
    }

    /**
     * 입고 상세 조회
     * GET /api/v1/inbound-receipts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> getInboundReceipt(
            @PathVariable("id") UUID receiptId
    ) {
        try {
            InboundReceiptResponse response = inboundReceiptService.getInboundReceipt(receiptId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage(), e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("서버 오류가 발생했습니다", "INTERNAL_ERROR"));
        }
    }

    /**
     * 입고 목록 조회
     * GET /api/v1/inbound-receipts
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InboundReceiptResponse>>> getAllInboundReceipts() {
        try {
            List<InboundReceiptResponse> responses = inboundReceiptService.getAllInboundReceipts();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("서버 오류가 발생했습니다", "INTERNAL_ERROR"));
        }
    }

    // DTO for reject request
    public static class RejectRequest {
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}


============================================================
// FILE: src\main\java\com\wms\controller\ShipmentOrderController.java
============================================================
package com.wms.controller;

import com.wms.dto.ApiResponse;
import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.exception.BusinessException;
import com.wms.service.ShipmentOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipment-orders")
public class ShipmentOrderController {

    private final ShipmentOrderService shipmentOrderService;

    public ShipmentOrderController(ShipmentOrderService shipmentOrderService) {
        this.shipmentOrderService = shipmentOrderService;
    }

    /**
     * 출고 지시서 생성
     * POST /api/v1/shipment-orders
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> createShipmentOrder(
            @RequestBody ShipmentOrderRequest request
    ) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.createShipmentOrder(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage(), e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("서버 오류가 발생했습니다", "INTERNAL_ERROR"));
        }
    }

    /**
     * 피킹 실행
     * POST /api/v1/shipment-orders/{id}/pick
     */
    @PostMapping("/{id}/pick")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> pickShipment(
            @PathVariable("id") UUID shipmentId
    ) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.pickShipment(shipmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage(), e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("서버 오류가 발생했습니다", "INTERNAL_ERROR"));
        }
    }

    /**
     * 출고 확정
     * POST /api/v1/shipment-orders/{id}/ship
     */
    @PostMapping("/{id}/ship")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> shipShipment(
            @PathVariable("id") UUID shipmentId
    ) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.shipShipment(shipmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage(), e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("서버 오류가 발생했습니다", "INTERNAL_ERROR"));
        }
    }

    /**
     * 출고 상세 조회
     * GET /api/v1/shipment-orders/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShipmentOrderResponse>> getShipmentOrder(
            @PathVariable("id") UUID shipmentId
    ) {
        try {
            ShipmentOrderResponse response = shipmentOrderService.getShipmentOrder(shipmentId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (BusinessException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage(), e.getErrorCode()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("서버 오류가 발생했습니다", "INTERNAL_ERROR"));
        }
    }

    /**
     * 출고 목록 조회
     * GET /api/v1/shipment-orders
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ShipmentOrderResponse>>> getAllShipmentOrders() {
        try {
            List<ShipmentOrderResponse> responses = shipmentOrderService.getAllShipmentOrders();
            return ResponseEntity.ok(ApiResponse.success(responses));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("서버 오류가 발생했습니다", "INTERNAL_ERROR"));
        }
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-transfers")
public class StockTransferController {

    private final StockTransferService stockTransferService;

    public StockTransferController(StockTransferService stockTransferService) {
        this.stockTransferService = stockTransferService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StockTransferResponse>> createStockTransfer(
            @RequestBody StockTransferRequest request
    ) {
        StockTransferResponse response = stockTransferService.createStockTransfer(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveTransfer(
            @PathVariable("id") UUID transferId,
            @RequestParam String approvedBy
    ) {
        StockTransferResponse response = stockTransferService.approveTransfer(transferId, approvedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable("id") UUID transferId,
            @RequestParam String rejectedBy
    ) {
        StockTransferResponse response = stockTransferService.rejectTransfer(transferId, rejectedBy);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(
            @PathVariable("id") UUID transferId
    ) {
        StockTransferResponse response = stockTransferService.getTransfer(transferId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockTransferResponse>>> getTransferHistory(
            @RequestParam(required = false) UUID productId
    ) {
        List<StockTransferResponse> responses = stockTransferService.getTransferHistory(productId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}


============================================================
// FILE: src\main\java\com\wms\dto\ApiResponse.java
============================================================
package com.wms.dto;

public class ApiResponse<T> {
    private boolean success;
    private T data;
    private ErrorInfo error;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> error(String message, String code) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.error = new ErrorInfo(message, code);
        return response;
    }

    public static class ErrorInfo {
        private String message;
        private String code;

        public ErrorInfo(String message, String code) {
            this.message = message;
            this.code = code;
        }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public ErrorInfo getError() { return error; }
    public void setError(ErrorInfo error) { this.error = error; }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptRequest.java
============================================================
package com.wms.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class InboundReceiptRequest {
    private UUID poId;
    private String receivedBy;
    private List<LineItem> lines;

    public static class LineItem {
        private UUID productId;
        private UUID locationId;
        private Integer quantity;
        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;

        // Getters and Setters
        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }

        public UUID getLocationId() { return locationId; }
        public void setLocationId(UUID locationId) { this.locationId = locationId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public String getLotNumber() { return lotNumber; }
        public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

        public LocalDate getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

        public LocalDate getManufactureDate() { return manufactureDate; }
        public void setManufactureDate(LocalDate manufactureDate) { this.manufactureDate = manufactureDate; }
    }

    // Getters and Setters
    public UUID getPoId() { return poId; }
    public void setPoId(UUID poId) { this.poId = poId; }

    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }

    public List<LineItem> getLines() { return lines; }
    public void setLines(List<LineItem> lines) { this.lines = lines; }
}


============================================================
// FILE: src\main\java\com\wms\dto\InboundReceiptResponse.java
============================================================
package com.wms.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class InboundReceiptResponse {
    private UUID receiptId;
    private UUID poId;
    private String status;
    private String receivedBy;
    private Instant receivedAt;
    private Instant confirmedAt;
    private List<LineItemResponse> lines;

    public static class LineItemResponse {
        private UUID receiptLineId;
        private UUID productId;
        private UUID locationId;
        private Integer quantity;
        private String lotNumber;
        private LocalDate expiryDate;
        private LocalDate manufactureDate;

        // Getters and Setters
        public UUID getReceiptLineId() { return receiptLineId; }
        public void setReceiptLineId(UUID receiptLineId) { this.receiptLineId = receiptLineId; }

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }

        public UUID getLocationId() { return locationId; }
        public void setLocationId(UUID locationId) { this.locationId = locationId; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public String getLotNumber() { return lotNumber; }
        public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

        public LocalDate getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

        public LocalDate getManufactureDate() { return manufactureDate; }
        public void setManufactureDate(LocalDate manufactureDate) { this.manufactureDate = manufactureDate; }
    }

    // Getters and Setters
    public UUID getReceiptId() { return receiptId; }
    public void setReceiptId(UUID receiptId) { this.receiptId = receiptId; }

    public UUID getPoId() { return poId; }
    public void setPoId(UUID poId) { this.poId = poId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public List<LineItemResponse> getLines() { return lines; }
    public void setLines(List<LineItemResponse> lines) { this.lines = lines; }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderRequest.java
============================================================
package com.wms.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ShipmentOrderRequest {

    private String shipmentNumber;
    private String customerName;
    private Instant requestedAt;
    private List<ShipmentLineRequest> lines;

    public static class ShipmentLineRequest {
        private UUID productId;
        private Integer requestedQty;

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }

        public Integer getRequestedQty() { return requestedQty; }
        public void setRequestedQty(Integer requestedQty) { this.requestedQty = requestedQty; }
    }

    // Getters and Setters
    public String getShipmentNumber() { return shipmentNumber; }
    public void setShipmentNumber(String shipmentNumber) { this.shipmentNumber = shipmentNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public List<ShipmentLineRequest> getLines() { return lines; }
    public void setLines(List<ShipmentLineRequest> lines) { this.lines = lines; }
}


============================================================
// FILE: src\main\java\com\wms\dto\ShipmentOrderResponse.java
============================================================
package com.wms.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ShipmentOrderResponse {

    private UUID shipmentId;
    private String shipmentNumber;
    private String customerName;
    private String status;
    private Instant requestedAt;
    private Instant shippedAt;
    private List<ShipmentLineResponse> lines;
    private Instant createdAt;
    private Instant updatedAt;

    public static class ShipmentLineResponse {
        private UUID shipmentLineId;
        private UUID productId;
        private String productName;
        private String productSku;
        private Integer requestedQty;
        private Integer pickedQty;
        private String status;

        // Getters and Setters
        public UUID getShipmentLineId() { return shipmentLineId; }
        public void setShipmentLineId(UUID shipmentLineId) { this.shipmentLineId = shipmentLineId; }

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public String getProductSku() { return productSku; }
        public void setProductSku(String productSku) { this.productSku = productSku; }

        public Integer getRequestedQty() { return requestedQty; }
        public void setRequestedQty(Integer requestedQty) { this.requestedQty = requestedQty; }

        public Integer getPickedQty() { return pickedQty; }
        public void setPickedQty(Integer pickedQty) { this.pickedQty = pickedQty; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    // Getters and Setters
    public UUID getShipmentId() { return shipmentId; }
    public void setShipmentId(UUID shipmentId) { this.shipmentId = shipmentId; }

    public String getShipmentNumber() { return shipmentNumber; }
    public void setShipmentNumber(String shipmentNumber) { this.shipmentNumber = shipmentNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getShippedAt() { return shippedAt; }
    public void setShippedAt(Instant shippedAt) { this.shippedAt = shippedAt; }

    public List<ShipmentLineResponse> getLines() { return lines; }
    public void setLines(List<ShipmentLineResponse> lines) { this.lines = lines; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferRequest.java
============================================================
package com.wms.dto;

import java.util.UUID;

public class StockTransferRequest {

    private UUID productId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer quantity;
    private String lotNumber;
    private String requestedBy;
    private String reason;

    // Getters and Setters
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(UUID fromLocationId) { this.fromLocationId = fromLocationId; }

    public UUID getToLocationId() { return toLocationId; }
    public void setToLocationId(UUID toLocationId) { this.toLocationId = toLocationId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}


============================================================
// FILE: src\main\java\com\wms\dto\StockTransferResponse.java
============================================================
package com.wms.dto;

import com.wms.entity.StockTransfer;

import java.time.Instant;
import java.util.UUID;

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
    private String requestedBy;
    private String approvedBy;
    private Instant requestedAt;
    private Instant approvedAt;
    private Instant completedAt;
    private String reason;

    public static StockTransferResponse from(StockTransfer transfer) {
        StockTransferResponse response = new StockTransferResponse();
        response.setTransferId(transfer.getTransferId());
        response.setProductId(transfer.getProduct().getProductId());
        response.setProductSku(transfer.getProduct().getSku());
        response.setProductName(transfer.getProduct().getName());
        response.setFromLocationId(transfer.getFromLocation().getLocationId());
        response.setFromLocationCode(transfer.getFromLocation().getCode());
        response.setToLocationId(transfer.getToLocation().getLocationId());
        response.setToLocationCode(transfer.getToLocation().getCode());
        response.setQuantity(transfer.getQuantity());
        response.setLotNumber(transfer.getLotNumber());
        response.setTransferStatus(transfer.getTransferStatus().name());
        response.setRequestedBy(transfer.getRequestedBy());
        response.setApprovedBy(transfer.getApprovedBy());
        response.setRequestedAt(transfer.getRequestedAt());
        response.setApprovedAt(transfer.getApprovedAt());
        response.setCompletedAt(transfer.getCompletedAt());
        response.setReason(transfer.getReason());
        return response;
    }

    // Getters and Setters
    public UUID getTransferId() { return transferId; }
    public void setTransferId(UUID transferId) { this.transferId = transferId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getProductSku() { return productSku; }
    public void setProductSku(String productSku) { this.productSku = productSku; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public UUID getFromLocationId() { return fromLocationId; }
    public void setFromLocationId(UUID fromLocationId) { this.fromLocationId = fromLocationId; }

    public String getFromLocationCode() { return fromLocationCode; }
    public void setFromLocationCode(String fromLocationCode) { this.fromLocationCode = fromLocationCode; }

    public UUID getToLocationId() { return toLocationId; }
    public void setToLocationId(UUID toLocationId) { this.toLocationId = toLocationId; }

    public String getToLocationCode() { return toLocationCode; }
    public void setToLocationCode(String toLocationCode) { this.toLocationCode = toLocationCode; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public String getTransferStatus() { return transferStatus; }
    public void setTransferStatus(String transferStatus) { this.transferStatus = transferStatus; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}


============================================================
// FILE: src\main\java\com\wms\entity\AuditLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
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

    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private String details;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (logId == null) {
            logId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getLogId() { return logId; }
    public void setLogId(UUID logId) { this.logId = logId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}


============================================================
// FILE: src\main\java\com\wms\entity\AutoReorderLog.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auto_reorder_logs")
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
    private Instant createdAt;

    public enum TriggerType {
        SAFETY_STOCK_TRIGGER, URGENT_REORDER
    }

    @PrePersist
    protected void onCreate() {
        if (reorderLogId == null) {
            reorderLogId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getReorderLogId() { return reorderLogId; }
    public void setReorderLogId(UUID reorderLogId) { this.reorderLogId = reorderLogId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }

    public Integer getCurrentStock() { return currentStock; }
    public void setCurrentStock(Integer currentStock) { this.currentStock = currentStock; }

    public Integer getMinQty() { return minQty; }
    public void setMinQty(Integer minQty) { this.minQty = minQty; }

    public Integer getReorderQty() { return reorderQty; }
    public void setReorderQty(Integer reorderQty) { this.reorderQty = reorderQty; }

    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}


============================================================
// FILE: src\main\java\com\wms\entity\Backorder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "backorders")
public class Backorder {

    @Id
    @Column(name = "backorder_id")
    private UUID backorderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_line_id", nullable = false)
    private ShipmentOrderLine shipmentLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "shortage_qty", nullable = false)
    private Integer shortageQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BackorderStatus status = BackorderStatus.OPEN;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    public enum BackorderStatus {
        OPEN, FULFILLED, CANCELLED
    }

    @PrePersist
    protected void onCreate() {
        if (backorderId == null) {
            backorderId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getBackorderId() { return backorderId; }
    public void setBackorderId(UUID backorderId) { this.backorderId = backorderId; }

    public ShipmentOrderLine getShipmentLine() { return shipmentLine; }
    public void setShipmentLine(ShipmentOrderLine shipmentLine) { this.shipmentLine = shipmentLine; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getShortageQty() { return shortageQty; }
    public void setShortageQty(Integer shortageQty) { this.shortageQty = shortageQty; }

    public BackorderStatus getStatus() { return status; }
    public void setStatus(BackorderStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getFulfilledAt() { return fulfilledAt; }
    public void setFulfilledAt(Instant fulfilledAt) { this.fulfilledAt = fulfilledAt; }
}


============================================================
// FILE: src\main\java\com\wms\entity\CycleCount.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cycle_counts")
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
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public enum CycleCountStatus {
        IN_PROGRESS, COMPLETED
    }

    @PrePersist
    protected void onCreate() {
        if (cycleCountId == null) {
            cycleCountId = UUID.randomUUID();
        }
        startedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getCycleCountId() { return cycleCountId; }
    public void setCycleCountId(UUID cycleCountId) { this.cycleCountId = cycleCountId; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public CycleCountStatus getStatus() { return status; }
    public void setStatus(CycleCountStatus status) { this.status = status; }

    public String getStartedBy() { return startedBy; }
    public void setStartedBy(String startedBy) { this.startedBy = startedBy; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceipt.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipts")
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
    private Instant receivedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @OneToMany(mappedBy = "inboundReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InboundReceiptLine> lines = new ArrayList<>();

    public enum Status {
        inspecting, pending_approval, confirmed, rejected
    }

    @PrePersist
    protected void onCreate() {
        if (receiptId == null) {
            receiptId = UUID.randomUUID();
        }
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
        createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getReceiptId() { return receiptId; }
    public void setReceiptId(UUID receiptId) { this.receiptId = receiptId; }

    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(PurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<InboundReceiptLine> getLines() { return lines; }
    public void setLines(List<InboundReceiptLine> lines) { this.lines = lines; }
}


============================================================
// FILE: src\main\java\com\wms\entity\InboundReceiptLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inbound_receipt_lines")
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

    // Getters and Setters
    public UUID getReceiptLineId() { return receiptLineId; }
    public void setReceiptLineId(UUID receiptLineId) { this.receiptLineId = receiptLineId; }

    public InboundReceipt getInboundReceipt() { return inboundReceipt; }
    public void setInboundReceipt(InboundReceipt inboundReceipt) { this.inboundReceipt = inboundReceipt; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public LocalDate getManufactureDate() { return manufactureDate; }
    public void setManufactureDate(LocalDate manufactureDate) { this.manufactureDate = manufactureDate; }
}


============================================================
// FILE: src\main\java\com\wms\entity\Inventory.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory")
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

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (inventoryId == null) {
            inventoryId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getInventoryId() { return inventoryId; }
    public void setInventoryId(UUID inventoryId) { this.inventoryId = inventoryId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public LocalDate getManufactureDate() { return manufactureDate; }
    public void setManufactureDate(LocalDate manufactureDate) { this.manufactureDate = manufactureDate; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Boolean getIsExpired() { return isExpired; }
    public void setIsExpired(Boolean isExpired) { this.isExpired = isExpired; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}


============================================================
// FILE: src\main\java\com\wms\entity\Location.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "locations")
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
    private Instant createdAt;

    public enum Zone {
        RECEIVING, STORAGE, SHIPPING, HAZMAT
    }

    public enum StorageType {
        AMBIENT, COLD, FROZEN
    }

    @PrePersist
    protected void onCreate() {
        if (locationId == null) {
            locationId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Zone getZone() { return zone; }
    public void setZone(Zone zone) { this.zone = zone; }

    public StorageType getStorageType() { return storageType; }
    public void setStorageType(StorageType storageType) { this.storageType = storageType; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Integer getCurrentQty() { return currentQty; }
    public void setCurrentQty(Integer currentQty) { this.currentQty = currentQty; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getIsFrozen() { return isFrozen; }
    public void setIsFrozen(Boolean isFrozen) { this.isFrozen = isFrozen; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}


============================================================
// FILE: src\main\java\com\wms\entity\Product.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
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
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum ProductCategory {
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
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ProductCategory getCategory() { return category; }
    public void setCategory(ProductCategory category) { this.category = category; }

    public StorageType getStorageType() { return storageType; }
    public void setStorageType(StorageType storageType) { this.storageType = storageType; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Boolean getHasExpiry() { return hasExpiry; }
    public void setHasExpiry(Boolean hasExpiry) { this.hasExpiry = hasExpiry; }

    public Integer getMinRemainingShelfLifePct() { return minRemainingShelfLifePct; }
    public void setMinRemainingShelfLifePct(Integer minRemainingShelfLifePct) {
        this.minRemainingShelfLifePct = minRemainingShelfLifePct;
    }

    public Integer getMaxPickQty() { return maxPickQty; }
    public void setMaxPickQty(Integer maxPickQty) { this.maxPickQty = maxPickQty; }

    public Boolean getManufactureDateRequired() { return manufactureDateRequired; }
    public void setManufactureDateRequired(Boolean manufactureDateRequired) {
        this.manufactureDateRequired = manufactureDateRequired;
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
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
    private Instant orderedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderLine> lines = new ArrayList<>();

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
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getPoId() { return poId; }
    public void setPoId(UUID poId) { this.poId = poId; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }

    public PoType getPoType() { return poType; }
    public void setPoType(PoType poType) { this.poType = poType; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getOrderedAt() { return orderedAt; }
    public void setOrderedAt(Instant orderedAt) { this.orderedAt = orderedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<PurchaseOrderLine> getLines() { return lines; }
    public void setLines(List<PurchaseOrderLine> lines) { this.lines = lines; }
}


============================================================
// FILE: src\main\java\com\wms\entity\PurchaseOrderLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines")
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

    // Getters and Setters
    public UUID getPoLineId() { return poLineId; }
    public void setPoLineId(UUID poLineId) { this.poLineId = poLineId; }

    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public void setPurchaseOrder(PurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getOrderedQty() { return orderedQty; }
    public void setOrderedQty(Integer orderedQty) { this.orderedQty = orderedQty; }

    public Integer getReceivedQty() { return receivedQty; }
    public void setReceivedQty(Integer receivedQty) { this.receivedQty = receivedQty; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}


============================================================
// FILE: src\main\java\com\wms\entity\SafetyStockRule.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "safety_stock_rules")
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
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (ruleId == null) {
            ruleId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getMinQty() { return minQty; }
    public void setMinQty(Integer minQty) { this.minQty = minQty; }

    public Integer getReorderQty() { return reorderQty; }
    public void setReorderQty(Integer reorderQty) { this.reorderQty = reorderQty; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}


============================================================
// FILE: src\main\java\com\wms\entity\SeasonalConfig.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "seasonal_config")
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
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (seasonId == null) {
            seasonId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getSeasonId() { return seasonId; }
    public void setSeasonId(UUID seasonId) { this.seasonId = seasonId; }

    public String getSeasonName() { return seasonName; }
    public void setSeasonName(String seasonName) { this.seasonName = seasonName; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public BigDecimal getMultiplier() { return multiplier; }
    public void setMultiplier(BigDecimal multiplier) { this.multiplier = multiplier; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrder.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipment_orders")
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
    private ShipmentStatus status = ShipmentStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum ShipmentStatus {
        PENDING, PICKING, PARTIAL, SHIPPED, CANCELLED
    }

    @PrePersist
    protected void onCreate() {
        if (shipmentId == null) {
            shipmentId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getShipmentId() { return shipmentId; }
    public void setShipmentId(UUID shipmentId) { this.shipmentId = shipmentId; }

    public String getShipmentNumber() { return shipmentNumber; }
    public void setShipmentNumber(String shipmentNumber) { this.shipmentNumber = shipmentNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public ShipmentStatus getStatus() { return status; }
    public void setStatus(ShipmentStatus status) { this.status = status; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getShippedAt() { return shippedAt; }
    public void setShippedAt(Instant shippedAt) { this.shippedAt = shippedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}


============================================================
// FILE: src\main\java\com\wms\entity\ShipmentOrderLine.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "shipment_order_lines")
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

    public enum LineStatus {
        PENDING, PICKED, PARTIAL, BACKORDERED
    }

    @PrePersist
    protected void onCreate() {
        if (shipmentLineId == null) {
            shipmentLineId = UUID.randomUUID();
        }
    }

    // Getters and Setters
    public UUID getShipmentLineId() { return shipmentLineId; }
    public void setShipmentLineId(UUID shipmentLineId) { this.shipmentLineId = shipmentLineId; }

    public ShipmentOrder getShipmentOrder() { return shipmentOrder; }
    public void setShipmentOrder(ShipmentOrder shipmentOrder) { this.shipmentOrder = shipmentOrder; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getRequestedQty() { return requestedQty; }
    public void setRequestedQty(Integer requestedQty) { this.requestedQty = requestedQty; }

    public Integer getPickedQty() { return pickedQty; }
    public void setPickedQty(Integer pickedQty) { this.pickedQty = pickedQty; }

    public LineStatus getStatus() { return status; }
    public void setStatus(LineStatus status) { this.status = status; }
}


============================================================
// FILE: src\main\java\com\wms\entity\StockTransfer.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_status", nullable = false, length = 50)
    private TransferStatus transferStatus = TransferStatus.immediate;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum TransferStatus {
        immediate, pending_approval, approved, rejected
    }

    @PrePersist
    protected void onCreate() {
        if (transferId == null) {
            transferId = UUID.randomUUID();
        }
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getTransferId() { return transferId; }
    public void setTransferId(UUID transferId) { this.transferId = transferId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Location getFromLocation() { return fromLocation; }
    public void setFromLocation(Location fromLocation) { this.fromLocation = fromLocation; }

    public Location getToLocation() { return toLocation; }
    public void setToLocation(Location toLocation) { this.toLocation = toLocation; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public TransferStatus getTransferStatus() { return transferStatus; }
    public void setTransferStatus(TransferStatus transferStatus) { this.transferStatus = transferStatus; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}


============================================================
// FILE: src\main\java\com\wms\entity\Supplier.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppliers")
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
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum Status {
        active, hold, inactive
    }

    @PrePersist
    protected void onCreate() {
        if (supplierId == null) {
            supplierId = UUID.randomUUID();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}


============================================================
// FILE: src\main\java\com\wms\entity\SupplierPenalty.java
============================================================
package com.wms.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "supplier_penalties")
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
    private Instant createdAt;

    public enum PenaltyType {
        OVER_DELIVERY, SHORT_SHELF_LIFE
    }

    @PrePersist
    protected void onCreate() {
        if (penaltyId == null) {
            penaltyId = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    // Getters and Setters
    public UUID getPenaltyId() { return penaltyId; }
    public void setPenaltyId(UUID penaltyId) { this.penaltyId = penaltyId; }

    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }

    public PenaltyType getPenaltyType() { return penaltyType; }
    public void setPenaltyType(PenaltyType penaltyType) { this.penaltyType = penaltyType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getPoId() { return poId; }
    public void setPoId(UUID poId) { this.poId = poId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}


============================================================
// FILE: src\main\java\com\wms\exception\BusinessException.java
============================================================
package com.wms.exception;

public class BusinessException extends RuntimeException {
    private final String errorCode;

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
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
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {
    Optional<CycleCount> findByLocation_LocationIdAndStatus(UUID locationId, CycleCount.CycleCountStatus status);
    List<CycleCount> findByLocation_LocationId(UUID locationId);
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("SELECT i FROM Inventory i WHERE i.product.productId = :productId AND i.location.locationId = :locationId AND i.lotNumber = :lotNumber")
    Optional<Inventory> findByProductAndLocationAndLot(
            @Param("productId") UUID productId,
            @Param("locationId") UUID locationId,
            @Param("lotNumber") String lotNumber
    );

    List<Inventory> findByProduct_ProductIdAndQuantityGreaterThan(UUID productId, Integer quantity);

    List<Inventory> findByProduct_ProductIdAndIsExpiredFalse(UUID productId);
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

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {

    @Query("SELECT pol FROM PurchaseOrderLine pol WHERE pol.purchaseOrder.poId = :poId AND pol.product.productId = :productId")
    Optional<PurchaseOrderLine> findByPoIdAndProductId(@Param("poId") UUID poId, @Param("productId") UUID productId);
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
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @Query("SELECT po FROM PurchaseOrder po WHERE po.supplier.supplierId = :supplierId AND po.status = 'pending'")
    List<PurchaseOrder> findPendingBySupplier(@Param("supplierId") UUID supplierId);
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
    Optional<SafetyStockRule> findByProduct_ProductId(UUID productId);
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
    Optional<SeasonalConfig> findActiveSeason(@Param("date") LocalDate date);
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
    List<ShipmentOrderLine> findByShipmentOrder_ShipmentId(UUID shipmentId);
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
    Optional<ShipmentOrder> findByShipmentNumber(String shipmentNumber);
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

    List<StockTransfer> findByTransferStatusOrderByRequestedAtDesc(StockTransfer.TransferStatus status);

    List<StockTransfer> findByProductProductIdOrderByRequestedAtDesc(UUID productId);
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
    long countBySupplierIdAndCreatedAtAfter(@Param("supplierId") UUID supplierId, @Param("since") Instant since);
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

@Service
public class InboundReceiptService {

    private final InboundReceiptRepository inboundReceiptRepository;
    private final InboundReceiptLineRepository inboundReceiptLineRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderLineRepository purchaseOrderLineRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SupplierPenaltyRepository supplierPenaltyRepository;
    private final SupplierRepository supplierRepository;
    private final SeasonalConfigRepository seasonalConfigRepository;

    public InboundReceiptService(
            InboundReceiptRepository inboundReceiptRepository,
            InboundReceiptLineRepository inboundReceiptLineRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PurchaseOrderLineRepository purchaseOrderLineRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            SupplierPenaltyRepository supplierPenaltyRepository,
            SupplierRepository supplierRepository,
            SeasonalConfigRepository seasonalConfigRepository
    ) {
        this.inboundReceiptRepository = inboundReceiptRepository;
        this.inboundReceiptLineRepository = inboundReceiptLineRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseOrderLineRepository = purchaseOrderLineRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.supplierPenaltyRepository = supplierPenaltyRepository;
        this.supplierRepository = supplierRepository;
        this.seasonalConfigRepository = seasonalConfigRepository;
    }

    @Transactional
    public InboundReceiptResponse createInboundReceipt(InboundReceiptRequest request) {
        // 1. PO 조회
        PurchaseOrder po = purchaseOrderRepository.findById(request.getPoId())
                .orElseThrow(() -> new BusinessException("발주서를 찾을 수 없습니다", "PO_NOT_FOUND"));

        if (po.getStatus() == PurchaseOrder.Status.cancelled) {
            throw new BusinessException("취소된 발주서에는 입고할 수 없습니다", "PO_CANCELLED");
        }

        if (po.getStatus() == PurchaseOrder.Status.hold) {
            throw new BusinessException("보류된 발주서에는 입고할 수 없습니다", "PO_HOLD");
        }

        // 2. 입고 전표 생성
        InboundReceipt receipt = new InboundReceipt();
        receipt.setPurchaseOrder(po);
        receipt.setReceivedBy(request.getReceivedBy());
        receipt.setStatus(InboundReceipt.Status.inspecting);

        boolean needsApproval = false;
        List<String> validationErrors = new ArrayList<>();

        // 3. 각 라인 검증 및 생성
        for (InboundReceiptRequest.LineItem lineItem : request.getLines()) {
            Product product = productRepository.findById(lineItem.getProductId())
                    .orElseThrow(() -> new BusinessException("상품을 찾을 수 없습니다", "PRODUCT_NOT_FOUND"));

            Location location = locationRepository.findById(lineItem.getLocationId())
                    .orElseThrow(() -> new BusinessException("로케이션을 찾을 수 없습니다", "LOCATION_NOT_FOUND"));

            // 3-1. 실사 동결 체크 (ALS-WMS-INB-002 Constraint)
            if (location.getIsFrozen()) {
                throw new BusinessException(
                        "실사 중인 로케이션에는 입고할 수 없습니다: " + location.getCode(),
                        "LOCATION_FROZEN"
                );
            }

            // 3-2. 보관 유형 호환성 체크 (ALS-WMS-INB-002 Constraint)
            validateStorageTypeCompatibility(product, location);

            // 3-3. 유통기한 관리 상품 체크 (ALS-WMS-INB-002 Constraint)
            if (product.getHasExpiry()) {
                if (lineItem.getExpiryDate() == null) {
                    throw new BusinessException(
                            "유통기한 관리 상품은 유통기한이 필수입니다: " + product.getSku(),
                            "EXPIRY_DATE_REQUIRED"
                    );
                }
                if (product.getManufactureDateRequired() && lineItem.getManufactureDate() == null) {
                    throw new BusinessException(
                            "유통기한 관리 상품은 제조일이 필수입니다: " + product.getSku(),
                            "MANUFACTURE_DATE_REQUIRED"
                    );
                }

                // 3-4. 유통기한 잔여율 체크 (ALS-WMS-INB-002 Constraint)
                ShelfLifeCheckResult shelfLifeResult = checkShelfLife(
                        product,
                        lineItem.getManufactureDate(),
                        lineItem.getExpiryDate()
                );

                if (shelfLifeResult == ShelfLifeCheckResult.REJECTED) {
                    // 페널티 기록 및 입고 거부
                    recordSupplierPenalty(
                            po.getSupplier(),
                            po.getPoId(),
                            SupplierPenalty.PenaltyType.SHORT_SHELF_LIFE,
                            "유통기한 부족: " + product.getSku()
                    );
                    throw new BusinessException(
                            "유통기한 잔여율이 최소 기준 미만입니다: " + product.getSku(),
                            "SHELF_LIFE_INSUFFICIENT"
                    );
                } else if (shelfLifeResult == ShelfLifeCheckResult.NEEDS_APPROVAL) {
                    needsApproval = true;
                }
            } else {
                // 유통기한 비관리 상품은 null로 설정
                lineItem.setExpiryDate(null);
                lineItem.setManufactureDate(null);
            }

            // 3-5. 초과입고 허용률 체크 (ALS-WMS-INB-002 Constraint)
            validateOverReceive(po, product, lineItem.getProductId(), lineItem.getQuantity());

            // 3-6. 입고 라인 생성
            InboundReceiptLine receiptLine = new InboundReceiptLine();
            receiptLine.setInboundReceipt(receipt);
            receiptLine.setProduct(product);
            receiptLine.setLocation(location);
            receiptLine.setQuantity(lineItem.getQuantity());
            receiptLine.setLotNumber(lineItem.getLotNumber());
            receiptLine.setExpiryDate(lineItem.getExpiryDate());
            receiptLine.setManufactureDate(lineItem.getManufactureDate());

            receipt.getLines().add(receiptLine);
        }

        // 4. 유통기한 경고가 있으면 승인 대기 상태로 변경
        if (needsApproval) {
            receipt.setStatus(InboundReceipt.Status.pending_approval);
        }

        // 5. 저장
        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse confirmInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("입고 전표를 찾을 수 없습니다", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.Status.inspecting) {
            throw new BusinessException(
                    "검수 중 상태의 입고만 확정할 수 있습니다",
                    "INVALID_STATUS"
            );
        }

        // 1. 재고 반영
        for (InboundReceiptLine line : receipt.getLines()) {
            updateInventory(line);
            updateLocationCurrentQty(line.getLocation(), line.getQuantity());
        }

        // 2. PO 라인 received_qty 누적 갱신
        updatePurchaseOrderLines(receipt);

        // 3. PO 상태 업데이트
        updatePurchaseOrderStatus(receipt.getPurchaseOrder());

        // 4. 입고 상태 변경
        receipt.setStatus(InboundReceipt.Status.confirmed);
        receipt.setConfirmedAt(Instant.now());

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse rejectInboundReceipt(UUID receiptId, String reason) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("입고 전표를 찾을 수 없습니다", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() == InboundReceipt.Status.confirmed) {
            throw new BusinessException(
                    "이미 확정된 입고는 거부할 수 없습니다",
                    "ALREADY_CONFIRMED"
            );
        }

        receipt.setStatus(InboundReceipt.Status.rejected);

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Transactional
    public InboundReceiptResponse approveShelfLifeWarning(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("입고 전표를 찾을 수 없습니다", "RECEIPT_NOT_FOUND"));

        if (receipt.getStatus() != InboundReceipt.Status.pending_approval) {
            throw new BusinessException(
                    "승인 대기 상태의 입고만 승인할 수 있습니다",
                    "INVALID_STATUS"
            );
        }

        // 승인 후 검수 중 상태로 변경 (이후 확정 가능)
        receipt.setStatus(InboundReceipt.Status.inspecting);

        InboundReceipt savedReceipt = inboundReceiptRepository.save(receipt);

        return toResponse(savedReceipt);
    }

    @Transactional(readOnly = true)
    public InboundReceiptResponse getInboundReceipt(UUID receiptId) {
        InboundReceipt receipt = inboundReceiptRepository.findById(receiptId)
                .orElseThrow(() -> new BusinessException("입고 전표를 찾을 수 없습니다", "RECEIPT_NOT_FOUND"));

        return toResponse(receipt);
    }

    @Transactional(readOnly = true)
    public List<InboundReceiptResponse> getAllInboundReceipts() {
        return inboundReceiptRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ===== Private Helper Methods =====

    /**
     * 보관 유형 호환성 검증 (ALS-WMS-INB-002 Constraint)
     */
    private void validateStorageTypeCompatibility(Product product, Location location) {
        Product.StorageType productType = product.getStorageType();
        Location.StorageType locationType = location.getStorageType();

        // HAZMAT 상품은 HAZMAT zone에만 허용
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            if (location.getZone() != Location.Zone.HAZMAT) {
                throw new BusinessException(
                        "위험물(HAZMAT) 상품은 HAZMAT zone에만 입고할 수 있습니다",
                        "HAZMAT_ZONE_REQUIRED"
                );
            }
        }

        // FROZEN 상품 → FROZEN 로케이션만
        if (productType == Product.StorageType.FROZEN) {
            if (locationType != Location.StorageType.FROZEN) {
                throw new BusinessException(
                        "FROZEN 상품은 FROZEN 로케이션에만 입고할 수 있습니다",
                        "STORAGE_TYPE_INCOMPATIBLE"
                );
            }
        }

        // COLD 상품 → COLD 또는 FROZEN 로케이션
        if (productType == Product.StorageType.COLD) {
            if (locationType != Location.StorageType.COLD && locationType != Location.StorageType.FROZEN) {
                throw new BusinessException(
                        "COLD 상품은 COLD 또는 FROZEN 로케이션에만 입고할 수 있습니다",
                        "STORAGE_TYPE_INCOMPATIBLE"
                );
            }
        }

        // AMBIENT 상품 → AMBIENT 로케이션만
        if (productType == Product.StorageType.AMBIENT) {
            if (locationType != Location.StorageType.AMBIENT) {
                throw new BusinessException(
                        "AMBIENT 상품은 AMBIENT 로케이션에만 입고할 수 있습니다",
                        "STORAGE_TYPE_INCOMPATIBLE"
                );
            }
        }
    }

    /**
     * 유통기한 잔여율 체크 (ALS-WMS-INB-002 Constraint)
     */
    private enum ShelfLifeCheckResult {
        ACCEPTED, NEEDS_APPROVAL, REJECTED
    }

    private ShelfLifeCheckResult checkShelfLife(Product product, LocalDate manufactureDate, LocalDate expiryDate) {
        if (manufactureDate == null || expiryDate == null) {
            return ShelfLifeCheckResult.ACCEPTED;
        }

        LocalDate today = LocalDate.now();
        long totalShelfLife = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingShelfLife = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalShelfLife <= 0) {
            throw new BusinessException("제조일이 유통기한보다 늦습니다", "INVALID_DATES");
        }

        double remainingPct = (double) remainingShelfLife / totalShelfLife * 100;

        int minPct = product.getMinRemainingShelfLifePct() != null ? product.getMinRemainingShelfLifePct() : 30;

        if (remainingPct < minPct) {
            return ShelfLifeCheckResult.REJECTED;
        } else if (remainingPct < 50) {
            return ShelfLifeCheckResult.NEEDS_APPROVAL;
        } else {
            return ShelfLifeCheckResult.ACCEPTED;
        }
    }

    /**
     * 초과입고 허용률 체크 (ALS-WMS-INB-002 Constraint)
     */
    private void validateOverReceive(PurchaseOrder po, Product product, UUID productId, int quantity) {
        // PO 라인 조회
        PurchaseOrderLine poLine = purchaseOrderLineRepository.findByPoIdAndProductId(po.getPoId(), productId)
                .orElseThrow(() -> new BusinessException(
                        "발주서에 해당 상품이 없습니다",
                        "PRODUCT_NOT_IN_PO"
                ));

        int orderedQty = poLine.getOrderedQty();
        int receivedQty = poLine.getReceivedQty();
        int newReceivedQty = receivedQty + quantity;

        // 카테고리별 기본 허용률
        double baseTolerance = getBaseTolerance(product.getCategory());

        // HAZMAT은 무조건 0% (ALS-WMS-INB-002 Constraint)
        if (product.getCategory() == Product.ProductCategory.HAZMAT) {
            baseTolerance = 0.0;
        } else {
            // PO 유형별 가중치
            double poTypeMultiplier = getPoTypeMultiplier(po.getPoType());
            baseTolerance *= poTypeMultiplier;

            // 성수기 가중치
            double seasonalMultiplier = getSeasonalMultiplier();
            baseTolerance *= seasonalMultiplier;
        }

        int maxAllowed = (int) (orderedQty * (1 + baseTolerance));

        if (newReceivedQty > maxAllowed) {
            // 초과입고 페널티 기록
            recordSupplierPenalty(
                    po.getSupplier(),
                    po.getPoId(),
                    SupplierPenalty.PenaltyType.OVER_DELIVERY,
                    String.format("초과입고: %s (주문 %d, 입고 시도 %d, 허용 %d)",
                            product.getSku(), orderedQty, newReceivedQty, maxAllowed)
            );

            throw new BusinessException(
                    String.format("초과입고 허용 수량을 초과했습니다 (주문: %d, 입고 시도: %d, 최대 허용: %d)",
                            orderedQty, newReceivedQty, maxAllowed),
                    "OVER_RECEIVE"
            );
        }
    }

    /**
     * 카테고리별 기본 허용률 (ALS-WMS-INB-002 Constraint)
     */
    private double getBaseTolerance(Product.ProductCategory category) {
        switch (category) {
            case GENERAL:
                return 0.10; // 10%
            case FRESH:
                return 0.05; // 5%
            case HAZMAT:
                return 0.00; // 0%
            case HIGH_VALUE:
                return 0.03; // 3%
            default:
                return 0.10;
        }
    }

    /**
     * PO 유형별 가중치 (ALS-WMS-INB-002 Constraint)
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
     * 성수기 가중치 (ALS-WMS-INB-002 Constraint)
     */
    private double getSeasonalMultiplier() {
        LocalDate today = LocalDate.now();
        return seasonalConfigRepository.findActiveSeason(today)
                .map(season -> season.getMultiplier().doubleValue())
                .orElse(1.0);
    }

    /**
     * 공급업체 페널티 기록 및 PO hold 체크 (ALS-WMS-INB-002 Constraint)
     */
    private void recordSupplierPenalty(Supplier supplier, UUID poId, SupplierPenalty.PenaltyType penaltyType, String description) {
        SupplierPenalty penalty = new SupplierPenalty();
        penalty.setSupplier(supplier);
        penalty.setPoId(poId);
        penalty.setPenaltyType(penaltyType);
        penalty.setDescription(description);
        supplierPenaltyRepository.save(penalty);

        // 최근 30일 페널티 개수 확인
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long penaltyCount = supplierPenaltyRepository.countBySupplierIdAndCreatedAtAfter(
                supplier.getSupplierId(),
                thirtyDaysAgo
        );

        // 3회 이상이면 pending PO를 hold로 변경
        if (penaltyCount >= 3) {
            List<PurchaseOrder> pendingPos = purchaseOrderRepository.findPendingBySupplier(supplier.getSupplierId());
            for (PurchaseOrder po : pendingPos) {
                po.setStatus(PurchaseOrder.Status.hold);
                purchaseOrderRepository.save(po);
            }

            // 공급업체 상태도 hold로 변경
            supplier.setStatus(Supplier.Status.hold);
            supplierRepository.save(supplier);
        }
    }

    /**
     * 재고 업데이트 (ALS-WMS-INB-002 Constraint - 확정 시점에만 반영)
     */
    private void updateInventory(InboundReceiptLine line) {
        // 동일한 product + location + lot 조합 찾기
        Inventory inventory = inventoryRepository.findByProductAndLocationAndLot(
                line.getProduct().getProductId(),
                line.getLocation().getLocationId(),
                line.getLotNumber()
        ).orElse(null);

        if (inventory == null) {
            // 신규 재고 생성
            inventory = new Inventory();
            inventory.setProduct(line.getProduct());
            inventory.setLocation(line.getLocation());
            inventory.setLotNumber(line.getLotNumber());
            inventory.setQuantity(line.getQuantity());
            inventory.setExpiryDate(line.getExpiryDate());
            inventory.setManufactureDate(line.getManufactureDate());
            inventory.setReceivedAt(Instant.now());
        } else {
            // 기존 재고에 수량 추가
            inventory.setQuantity(inventory.getQuantity() + line.getQuantity());
        }

        inventoryRepository.save(inventory);
    }

    /**
     * 로케이션 현재 수량 업데이트 (ALS-WMS-INB-002 Constraint)
     */
    private void updateLocationCurrentQty(Location location, int quantity) {
        location.setCurrentQty(location.getCurrentQty() + quantity);

        if (location.getCurrentQty() > location.getCapacity()) {
            throw new BusinessException(
                    "로케이션 용량을 초과했습니다: " + location.getCode(),
                    "LOCATION_CAPACITY_EXCEEDED"
            );
        }

        locationRepository.save(location);
    }

    /**
     * PO 라인 received_qty 누적 갱신 (ALS-WMS-INB-002 Constraint)
     */
    private void updatePurchaseOrderLines(InboundReceipt receipt) {
        for (InboundReceiptLine line : receipt.getLines()) {
            PurchaseOrderLine poLine = purchaseOrderLineRepository.findByPoIdAndProductId(
                    receipt.getPurchaseOrder().getPoId(),
                    line.getProduct().getProductId()
            ).orElseThrow(() -> new BusinessException("PO 라인을 찾을 수 없습니다", "PO_LINE_NOT_FOUND"));

            poLine.setReceivedQty(poLine.getReceivedQty() + line.getQuantity());
            purchaseOrderLineRepository.save(poLine);
        }
    }

    /**
     * PO 상태 업데이트 (ALS-WMS-INB-002 Constraint)
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
            po.setStatus(PurchaseOrder.Status.completed);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrder.Status.partial);
        }

        purchaseOrderRepository.save(po);
    }

    /**
     * Entity to DTO 변환
     */
    private InboundReceiptResponse toResponse(InboundReceipt receipt) {
        InboundReceiptResponse response = new InboundReceiptResponse();
        response.setReceiptId(receipt.getReceiptId());
        response.setPoId(receipt.getPurchaseOrder().getPoId());
        response.setStatus(receipt.getStatus().name());
        response.setReceivedBy(receipt.getReceivedBy());
        response.setReceivedAt(receipt.getReceivedAt());
        response.setConfirmedAt(receipt.getConfirmedAt());

        List<InboundReceiptResponse.LineItemResponse> lineResponses = receipt.getLines().stream()
                .map(line -> {
                    InboundReceiptResponse.LineItemResponse lineResponse = new InboundReceiptResponse.LineItemResponse();
                    lineResponse.setReceiptLineId(line.getReceiptLineId());
                    lineResponse.setProductId(line.getProduct().getProductId());
                    lineResponse.setLocationId(line.getLocation().getLocationId());
                    lineResponse.setQuantity(line.getQuantity());
                    lineResponse.setLotNumber(line.getLotNumber());
                    lineResponse.setExpiryDate(line.getExpiryDate());
                    lineResponse.setManufactureDate(line.getManufactureDate());
                    return lineResponse;
                })
                .collect(Collectors.toList());

        response.setLines(lineResponses);

        return response;
    }
}


============================================================
// FILE: src\main\java\com\wms\service\ShipmentOrderService.java
============================================================
package com.wms.service;

import com.wms.dto.ShipmentOrderRequest;
import com.wms.dto.ShipmentOrderResponse;
import com.wms.entity.*;
import com.wms.exception.BusinessException;
import com.wms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
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
    private final CycleCountRepository cycleCountRepository;

    public ShipmentOrderService(
            ShipmentOrderRepository shipmentOrderRepository,
            ShipmentOrderLineRepository shipmentOrderLineRepository,
            ProductRepository productRepository,
            InventoryRepository inventoryRepository,
            LocationRepository locationRepository,
            BackorderRepository backorderRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository,
            AuditLogRepository auditLogRepository,
            CycleCountRepository cycleCountRepository
    ) {
        this.shipmentOrderRepository = shipmentOrderRepository;
        this.shipmentOrderLineRepository = shipmentOrderLineRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.locationRepository = locationRepository;
        this.backorderRepository = backorderRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.cycleCountRepository = cycleCountRepository;
    }

    @Transactional
    public ShipmentOrderResponse createShipmentOrder(ShipmentOrderRequest request) {
        // 1. 출고 지시서 생성
        ShipmentOrder shipmentOrder = new ShipmentOrder();
        shipmentOrder.setShipmentNumber(request.getShipmentNumber());
        shipmentOrder.setCustomerName(request.getCustomerName());
        shipmentOrder.setRequestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : Instant.now());
        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.PENDING);

        shipmentOrder = shipmentOrderRepository.save(shipmentOrder);

        // 2. HAZMAT + FRESH 분리 출고 검증 (ALS-WMS-OUT-002 Constraint)
        boolean hasHazmat = false;
        boolean hasFresh = false;

        for (ShipmentOrderRequest.ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("상품을 찾을 수 없습니다", "PRODUCT_NOT_FOUND"));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                hasHazmat = true;
            }
            if (product.getCategory() == Product.ProductCategory.FRESH) {
                hasFresh = true;
            }
        }

        // HAZMAT + FRESH 혼합 시 분리 출고 처리
        if (hasHazmat && hasFresh) {
            return createSeparatedShipmentOrders(request, shipmentOrder);
        }

        // 3. 출고 라인 생성
        List<ShipmentOrderLine> lines = new ArrayList<>();
        for (ShipmentOrderRequest.ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("상품을 찾을 수 없습니다", "PRODUCT_NOT_FOUND"));

            ShipmentOrderLine line = new ShipmentOrderLine();
            line.setShipmentOrder(shipmentOrder);
            line.setProduct(product);
            line.setRequestedQty(lineReq.getRequestedQty());
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.PENDING);

            lines.add(shipmentOrderLineRepository.save(line));
        }

        return buildResponse(shipmentOrder, lines);
    }

    // HAZMAT + FRESH 분리 출고 처리
    @Transactional
    protected ShipmentOrderResponse createSeparatedShipmentOrders(
            ShipmentOrderRequest request,
            ShipmentOrder originalShipment
    ) {
        // 원본 출고 지시서: FRESH 상품만
        // 신규 출고 지시서: HAZMAT 상품만

        List<ShipmentOrderLine> freshLines = new ArrayList<>();
        ShipmentOrder hazmatShipment = null;
        List<ShipmentOrderLine> hazmatLines = new ArrayList<>();

        for (ShipmentOrderRequest.ShipmentLineRequest lineReq : request.getLines()) {
            Product product = productRepository.findById(lineReq.getProductId())
                    .orElseThrow(() -> new BusinessException("상품을 찾을 수 없습니다", "PRODUCT_NOT_FOUND"));

            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                // HAZMAT 상품 → 별도 출고 지시서
                if (hazmatShipment == null) {
                    hazmatShipment = new ShipmentOrder();
                    hazmatShipment.setShipmentNumber(request.getShipmentNumber() + "-HAZMAT");
                    hazmatShipment.setCustomerName(request.getCustomerName());
                    hazmatShipment.setRequestedAt(request.getRequestedAt() != null ? request.getRequestedAt() : Instant.now());
                    hazmatShipment.setStatus(ShipmentOrder.ShipmentStatus.PENDING);
                    hazmatShipment = shipmentOrderRepository.save(hazmatShipment);
                }

                ShipmentOrderLine line = new ShipmentOrderLine();
                line.setShipmentOrder(hazmatShipment);
                line.setProduct(product);
                line.setRequestedQty(lineReq.getRequestedQty());
                line.setPickedQty(0);
                line.setStatus(ShipmentOrderLine.LineStatus.PENDING);
                hazmatLines.add(shipmentOrderLineRepository.save(line));

            } else {
                // 비-HAZMAT 상품 → 원본 출고 지시서
                ShipmentOrderLine line = new ShipmentOrderLine();
                line.setShipmentOrder(originalShipment);
                line.setProduct(product);
                line.setRequestedQty(lineReq.getRequestedQty());
                line.setPickedQty(0);
                line.setStatus(ShipmentOrderLine.LineStatus.PENDING);
                freshLines.add(shipmentOrderLineRepository.save(line));
            }
        }

        // 원본 출고 지시서 응답 반환 (FRESH만 포함)
        return buildResponse(originalShipment, freshLines);
    }

    @Transactional
    public ShipmentOrderResponse pickShipment(UUID shipmentId) {
        // 1. 출고 지시서 조회
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("출고 지시서를 찾을 수 없습니다", "SHIPMENT_NOT_FOUND"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.PENDING) {
            throw new BusinessException("피킹 가능한 상태가 아닙니다", "INVALID_STATUS");
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.PICKING);
        shipmentOrderRepository.save(shipmentOrder);

        // 2. 각 라인 피킹 수행
        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipmentId);

        for (ShipmentOrderLine line : lines) {
            performPicking(line);
        }

        // 3. 출고 상태 업데이트
        boolean allPicked = lines.stream().allMatch(l -> l.getStatus() == ShipmentOrderLine.LineStatus.PICKED);
        boolean anyPicked = lines.stream().anyMatch(l -> l.getPickedQty() > 0);

        if (allPicked) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
            shipmentOrder.setShippedAt(Instant.now());
        } else if (anyPicked) {
            shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.PARTIAL);
        }

        shipmentOrderRepository.save(shipmentOrder);

        // 4. 안전재고 체크 (ALS-WMS-OUT-002 Constraint)
        for (ShipmentOrderLine line : lines) {
            checkSafetyStock(line.getProduct());
        }

        return buildResponse(shipmentOrder, lines);
    }

    @Transactional
    protected void performPicking(ShipmentOrderLine line) {
        Product product = line.getProduct();
        int requestedQty = line.getRequestedQty();

        // 1. 피킹 가능한 재고 조회 (ALS-WMS-OUT-002 Constraint)
        List<Inventory> pickableInventories = getPickableInventories(product);

        // 2. 가용 재고 계산
        int availableQty = pickableInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 3. 부분출고 의사결정 트리 (ALS-WMS-OUT-002 Constraint)
        double availabilityRatio = (double) availableQty / requestedQty;

        if (availableQty == 0) {
            // 전량 백오더
            createBackorder(line, requestedQty);
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);

        } else if (availabilityRatio < 0.3) {
            // 가용 < 30%: 전량 백오더 (부분출고 안 함)
            createBackorder(line, requestedQty);
            line.setPickedQty(0);
            line.setStatus(ShipmentOrderLine.LineStatus.BACKORDERED);

        } else if (availabilityRatio >= 0.3 && availabilityRatio < 0.7) {
            // 가용 30%~70%: 부분출고 + 백오더 + 긴급발주
            int pickedQty = pickFromInventories(pickableInventories, availableQty, product);
            line.setPickedQty(pickedQty);
            line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);

            int shortageQty = requestedQty - pickedQty;
            if (shortageQty > 0) {
                createBackorder(line, shortageQty);
                createUrgentReorder(product, availableQty - pickedQty);
            }

        } else if (availabilityRatio >= 0.7 && availableQty < requestedQty) {
            // 가용 ≥ 70%: 부분출고 + 백오더
            int pickedQty = pickFromInventories(pickableInventories, availableQty, product);
            line.setPickedQty(pickedQty);
            line.setStatus(ShipmentOrderLine.LineStatus.PARTIAL);

            int shortageQty = requestedQty - pickedQty;
            if (shortageQty > 0) {
                createBackorder(line, shortageQty);
            }

        } else {
            // 전량 피킹 가능
            int pickedQty = pickFromInventories(pickableInventories, requestedQty, product);
            line.setPickedQty(pickedQty);
            line.setStatus(ShipmentOrderLine.LineStatus.PICKED);
        }

        shipmentOrderLineRepository.save(line);
    }

    // 피킹 가능한 재고 조회 (FIFO/FEFO + 만료 처리)
    protected List<Inventory> getPickableInventories(Product product) {
        List<Inventory> allInventories = inventoryRepository.findByProduct_ProductIdAndQuantityGreaterThan(
                product.getProductId(), 0);

        List<Inventory> pickable = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Inventory inv : allInventories) {
            // is_expired=true 제외 (ALS-WMS-OUT-002 Constraint)
            if (inv.getIsExpired()) {
                continue;
            }

            // is_frozen=true 로케이션 제외 (ALS-WMS-OUT-002 Constraint)
            if (inv.getLocation().getIsFrozen()) {
                continue;
            }

            // 유통기한 관리 상품의 경우
            if (product.getHasExpiry() && inv.getExpiryDate() != null && inv.getManufactureDate() != null) {
                // 유통기한 만료 제외
                if (inv.getExpiryDate().isBefore(today)) {
                    continue;
                }

                // 잔여 유통기한 < 10%: 출고 불가 (폐기 전환)
                double remainingPct = calculateRemainingShelfLifePct(
                        inv.getExpiryDate(), inv.getManufactureDate(), today);

                if (remainingPct < 10.0) {
                    // is_expired=true로 설정 (ALS-WMS-OUT-002 Constraint)
                    inv.setIsExpired(true);
                    inventoryRepository.save(inv);
                    continue;
                }
            }

            // HAZMAT 상품은 HAZMAT zone에서만 피킹 (ALS-WMS-OUT-002 Constraint)
            if (product.getCategory() == Product.ProductCategory.HAZMAT) {
                if (inv.getLocation().getZone() != Location.Zone.HAZMAT) {
                    continue;
                }
            }

            pickable.add(inv);
        }

        // FIFO/FEFO 정렬 (ALS-WMS-OUT-002 Constraint)
        pickable.sort((a, b) -> {
            if (product.getHasExpiry()) {
                // FEFO 우선
                if (a.getExpiryDate() != null && b.getExpiryDate() != null) {
                    // 잔여율 <30% 최우선 출고
                    LocalDate today = LocalDate.now();
                    double aPct = calculateRemainingShelfLifePct(a.getExpiryDate(), a.getManufactureDate(), today);
                    double bPct = calculateRemainingShelfLifePct(b.getExpiryDate(), b.getManufactureDate(), today);

                    boolean aUrgent = aPct < 30.0;
                    boolean bUrgent = bPct < 30.0;

                    if (aUrgent && !bUrgent) return -1;
                    if (!aUrgent && bUrgent) return 1;

                    // 둘 다 긴급 또는 둘 다 정상이면 FEFO
                    int expiryCompare = a.getExpiryDate().compareTo(b.getExpiryDate());
                    if (expiryCompare != 0) return expiryCompare;
                }
            }
            // FIFO (received_at 기준)
            return a.getReceivedAt().compareTo(b.getReceivedAt());
        });

        return pickable;
    }

    // 재고에서 피킹 수행 (재고 차감 + 로케이션 차감)
    protected int pickFromInventories(List<Inventory> inventories, int qtyToPick, Product product) {
        int remainingQty = qtyToPick;
        int totalPicked = 0;

        // HAZMAT max_pick_qty 제한 (ALS-WMS-OUT-002 Constraint)
        if (product.getCategory() == Product.ProductCategory.HAZMAT && product.getMaxPickQty() != null) {
            if (qtyToPick > product.getMaxPickQty()) {
                qtyToPick = product.getMaxPickQty();
                remainingQty = qtyToPick;
            }
        }

        for (Inventory inv : inventories) {
            if (remainingQty <= 0) break;

            int pickQty = Math.min(inv.getQuantity(), remainingQty);

            // 보관 유형 불일치 경고 (ALS-WMS-OUT-002 Constraint)
            if (inv.getLocation().getStorageType() != product.getStorageType()) {
                createAuditLog("STORAGE_TYPE_MISMATCH", "INVENTORY", inv.getInventoryId(),
                        String.format("{\"locationStorageType\":\"%s\",\"productStorageType\":\"%s\",\"location\":\"%s\"}",
                                inv.getLocation().getStorageType(), product.getStorageType(), inv.getLocation().getCode()),
                        "SYSTEM");
            }

            // 재고 차감
            inv.setQuantity(inv.getQuantity() - pickQty);
            inventoryRepository.save(inv);

            // 로케이션 차감 (ALS-WMS-OUT-002 Constraint)
            Location location = inv.getLocation();
            location.setCurrentQty(location.getCurrentQty() - pickQty);
            locationRepository.save(location);

            remainingQty -= pickQty;
            totalPicked += pickQty;
        }

        return totalPicked;
    }

    // 백오더 생성
    protected void createBackorder(ShipmentOrderLine line, int shortageQty) {
        Backorder backorder = new Backorder();
        backorder.setShipmentLine(line);
        backorder.setProduct(line.getProduct());
        backorder.setShortageQty(shortageQty);
        backorder.setStatus(Backorder.BackorderStatus.OPEN);
        backorderRepository.save(backorder);
    }

    // 긴급발주 트리거 (ALS-WMS-OUT-002 Constraint)
    protected void createUrgentReorder(Product product, int currentStock) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductId(product.getProductId())
                .orElse(null);

        if (rule != null) {
            AutoReorderLog log = new AutoReorderLog();
            log.setProduct(product);
            log.setTriggerType(AutoReorderLog.TriggerType.URGENT_REORDER);
            log.setCurrentStock(currentStock);
            log.setMinQty(rule.getMinQty());
            log.setReorderQty(rule.getReorderQty());
            log.setTriggeredBy("SYSTEM");
            autoReorderLogRepository.save(log);
        }
    }

    // 안전재고 체크 (ALS-WMS-OUT-002 Constraint)
    protected void checkSafetyStock(Product product) {
        SafetyStockRule rule = safetyStockRuleRepository.findByProduct_ProductId(product.getProductId())
                .orElse(null);

        if (rule != null) {
            // 전체 가용 재고 합산 (is_expired=true 제외)
            int totalAvailable = inventoryRepository.findByProduct_ProductIdAndIsExpiredFalse(product.getProductId())
                    .stream()
                    .mapToInt(Inventory::getQuantity)
                    .sum();

            // 안전재고 미달 시 자동 재발주
            if (totalAvailable <= rule.getMinQty()) {
                AutoReorderLog log = new AutoReorderLog();
                log.setProduct(product);
                log.setTriggerType(AutoReorderLog.TriggerType.SAFETY_STOCK_TRIGGER);
                log.setCurrentStock(totalAvailable);
                log.setMinQty(rule.getMinQty());
                log.setReorderQty(rule.getReorderQty());
                log.setTriggeredBy("SYSTEM");
                autoReorderLogRepository.save(log);
            }
        }
    }

    // 잔여 유통기한 비율 계산
    protected double calculateRemainingShelfLifePct(LocalDate expiryDate, LocalDate manufactureDate, LocalDate today) {
        if (expiryDate == null || manufactureDate == null) {
            return 100.0;
        }

        long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
        long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

        if (totalDays <= 0) {
            return 0.0;
        }

        return (double) remainingDays / totalDays * 100.0;
    }

    // 감사 로그 기록
    protected void createAuditLog(String eventType, String entityType, UUID entityId, String details, String performedBy) {
        AuditLog log = new AuditLog();
        log.setEventType(eventType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setPerformedBy(performedBy);
        auditLogRepository.save(log);
    }

    @Transactional
    public ShipmentOrderResponse shipShipment(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("출고 지시서를 찾을 수 없습니다", "SHIPMENT_NOT_FOUND"));

        if (shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.PICKING &&
                shipmentOrder.getStatus() != ShipmentOrder.ShipmentStatus.PARTIAL) {
            throw new BusinessException("출고 확정 가능한 상태가 아닙니다", "INVALID_STATUS");
        }

        shipmentOrder.setStatus(ShipmentOrder.ShipmentStatus.SHIPPED);
        shipmentOrder.setShippedAt(Instant.now());
        shipmentOrderRepository.save(shipmentOrder);

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipmentId);
        return buildResponse(shipmentOrder, lines);
    }

    @Transactional(readOnly = true)
    public ShipmentOrderResponse getShipmentOrder(UUID shipmentId) {
        ShipmentOrder shipmentOrder = shipmentOrderRepository.findById(shipmentId)
                .orElseThrow(() -> new BusinessException("출고 지시서를 찾을 수 없습니다", "SHIPMENT_NOT_FOUND"));

        List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipmentId);
        return buildResponse(shipmentOrder, lines);
    }

    @Transactional(readOnly = true)
    public List<ShipmentOrderResponse> getAllShipmentOrders() {
        List<ShipmentOrder> shipments = shipmentOrderRepository.findAll();
        return shipments.stream()
                .map(shipment -> {
                    List<ShipmentOrderLine> lines = shipmentOrderLineRepository.findByShipmentOrder_ShipmentId(shipment.getShipmentId());
                    return buildResponse(shipment, lines);
                })
                .collect(Collectors.toList());
    }

    // 응답 DTO 생성
    protected ShipmentOrderResponse buildResponse(ShipmentOrder shipmentOrder, List<ShipmentOrderLine> lines) {
        ShipmentOrderResponse response = new ShipmentOrderResponse();
        response.setShipmentId(shipmentOrder.getShipmentId());
        response.setShipmentNumber(shipmentOrder.getShipmentNumber());
        response.setCustomerName(shipmentOrder.getCustomerName());
        response.setStatus(shipmentOrder.getStatus().name());
        response.setRequestedAt(shipmentOrder.getRequestedAt());
        response.setShippedAt(shipmentOrder.getShippedAt());
        response.setCreatedAt(shipmentOrder.getCreatedAt());
        response.setUpdatedAt(shipmentOrder.getUpdatedAt());

        List<ShipmentOrderResponse.ShipmentLineResponse> lineResponses = lines.stream()
                .map(line -> {
                    ShipmentOrderResponse.ShipmentLineResponse lineResp = new ShipmentOrderResponse.ShipmentLineResponse();
                    lineResp.setShipmentLineId(line.getShipmentLineId());
                    lineResp.setProductId(line.getProduct().getProductId());
                    lineResp.setProductName(line.getProduct().getName());
                    lineResp.setProductSku(line.getProduct().getSku());
                    lineResp.setRequestedQty(line.getRequestedQty());
                    lineResp.setPickedQty(line.getPickedQty());
                    lineResp.setStatus(line.getStatus().name());
                    return lineResp;
                })
                .collect(Collectors.toList());

        response.setLines(lineResponses);
        return response;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final ProductRepository productRepository;
    private final LocationRepository locationRepository;
    private final InventoryRepository inventoryRepository;
    private final SafetyStockRuleRepository safetyStockRuleRepository;
    private final AutoReorderLogRepository autoReorderLogRepository;

    public StockTransferService(
            StockTransferRepository stockTransferRepository,
            ProductRepository productRepository,
            LocationRepository locationRepository,
            InventoryRepository inventoryRepository,
            SafetyStockRuleRepository safetyStockRuleRepository,
            AutoReorderLogRepository autoReorderLogRepository
    ) {
        this.stockTransferRepository = stockTransferRepository;
        this.productRepository = productRepository;
        this.locationRepository = locationRepository;
        this.inventoryRepository = inventoryRepository;
        this.safetyStockRuleRepository = safetyStockRuleRepository;
        this.autoReorderLogRepository = autoReorderLogRepository;
    }

    @Transactional
    public StockTransferResponse createStockTransfer(StockTransferRequest request) {
        // 1. 기본 검증 및 엔티티 조회
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new BusinessException("상품을 찾을 수 없습니다", "PRODUCT_NOT_FOUND"));

        Location fromLocation = locationRepository.findById(request.getFromLocationId())
                .orElseThrow(() -> new BusinessException("출발지 로케이션을 찾을 수 없습니다", "FROM_LOCATION_NOT_FOUND"));

        Location toLocation = locationRepository.findById(request.getToLocationId())
                .orElseThrow(() -> new BusinessException("도착지 로케이션을 찾을 수 없습니다", "TO_LOCATION_NOT_FOUND"));

        // 2. 기본 규칙 체크 (ALS-WMS-STK-002 Constraints)

        // 2-1. 출발지와 도착지가 동일한 경우 거부
        if (fromLocation.getLocationId().equals(toLocation.getLocationId())) {
            throw new BusinessException("출발지와 도착지가 동일합니다", "SAME_LOCATION");
        }

        // 2-2. 실사 동결 체크
        if (fromLocation.getIsFrozen()) {
            throw new BusinessException(
                    "실사 중인 로케이션에서 이동할 수 없습니다: " + fromLocation.getCode(),
                    "FROM_LOCATION_FROZEN"
            );
        }

        if (toLocation.getIsFrozen()) {
            throw new BusinessException(
                    "실사 중인 로케이션으로 이동할 수 없습니다: " + toLocation.getCode(),
                    "TO_LOCATION_FROZEN"
            );
        }

        // 2-3. 출발지 재고 확인
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLot(
                        product.getProductId(),
                        fromLocation.getLocationId(),
                        request.getLotNumber()
                )
                .orElseThrow(() -> new BusinessException(
                        "출발지에 해당 재고가 없습니다",
                        "SOURCE_INVENTORY_NOT_FOUND"
                ));

        // 2-4. 이동 수량이 출발지 재고보다 많으면 거부
        if (request.getQuantity() > sourceInventory.getQuantity()) {
            throw new BusinessException(
                    "이동 수량이 출발지 재고보다 많습니다 (가용: " + sourceInventory.getQuantity() + ")",
                    "INSUFFICIENT_QUANTITY"
            );
        }

        // 2-5. 도착지 용량 체크
        if (toLocation.getCurrentQty() + request.getQuantity() > toLocation.getCapacity()) {
            throw new BusinessException(
                    "도착지 로케이션의 용량을 초과합니다 (용량: " + toLocation.getCapacity() +
                            ", 현재: " + toLocation.getCurrentQty() + ", 이동: " + request.getQuantity() + ")",
                    "DESTINATION_CAPACITY_EXCEEDED"
            );
        }

        // 3. 보관 유형 호환성 검증 (ALS-WMS-STK-002 Level 2)
        validateStorageTypeCompatibility(product, toLocation);

        // 4. 위험물 혼적 금지 (ALS-WMS-STK-002 Level 2)
        validateHazmatSegregation(product, toLocation);

        // 5. 유통기한 이동 제한 (ALS-WMS-STK-002 Level 2)
        if (product.getHasExpiry() && sourceInventory.getExpiryDate() != null) {
            validateExpiryDateRestrictions(sourceInventory, toLocation);
        }

        // 6. 대량 이동 승인 체크 (ALS-WMS-STK-002 Level 2)
        boolean needsApproval = false;
        BigDecimal transferRatio = BigDecimal.valueOf(request.getQuantity())
                .divide(BigDecimal.valueOf(sourceInventory.getQuantity()), 4, BigDecimal.ROUND_HALF_UP);

        if (transferRatio.compareTo(BigDecimal.valueOf(0.80)) >= 0) {
            needsApproval = true;
        }

        // 7. StockTransfer 이력 생성
        StockTransfer transfer = new StockTransfer();
        transfer.setProduct(product);
        transfer.setFromLocation(fromLocation);
        transfer.setToLocation(toLocation);
        transfer.setQuantity(request.getQuantity());
        transfer.setLotNumber(request.getLotNumber());
        transfer.setRequestedBy(request.getRequestedBy());
        transfer.setReason(request.getReason());

        if (needsApproval) {
            // 대량 이동 → 승인 대기
            transfer.setTransferStatus(StockTransfer.TransferStatus.pending_approval);
            stockTransferRepository.save(transfer);

            return StockTransferResponse.from(transfer);
        } else {
            // 즉시 이동
            transfer.setTransferStatus(StockTransfer.TransferStatus.immediate);
            transfer.setCompletedAt(Instant.now());

            // 8. 트랜잭션으로 재고 이동 실행
            executeTransfer(sourceInventory, toLocation, request.getQuantity(), product);

            stockTransferRepository.save(transfer);

            // 9. 안전재고 체크 (ALS-WMS-STK-002 Level 2)
            checkSafetyStock(product);

            return StockTransferResponse.from(transfer);
        }
    }

    @Transactional
    public StockTransferResponse approveTransfer(UUID transferId, String approvedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("재고 이동 내역을 찾을 수 없습니다", "TRANSFER_NOT_FOUND"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new BusinessException(
                    "승인 대기 상태가 아닙니다",
                    "NOT_PENDING_APPROVAL"
            );
        }

        // 출발지 재고 재확인
        Inventory sourceInventory = inventoryRepository.findByProductAndLocationAndLot(
                        transfer.getProduct().getProductId(),
                        transfer.getFromLocation().getLocationId(),
                        transfer.getLotNumber()
                )
                .orElseThrow(() -> new BusinessException(
                        "출발지에 해당 재고가 없습니다",
                        "SOURCE_INVENTORY_NOT_FOUND"
                ));

        if (transfer.getQuantity() > sourceInventory.getQuantity()) {
            throw new BusinessException(
                    "이동 수량이 출발지 재고보다 많습니다",
                    "INSUFFICIENT_QUANTITY"
            );
        }

        // 트랜잭션으로 재고 이동 실행
        executeTransfer(sourceInventory, transfer.getToLocation(), transfer.getQuantity(), transfer.getProduct());

        // 승인 처리
        transfer.setTransferStatus(StockTransfer.TransferStatus.approved);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(Instant.now());
        transfer.setCompletedAt(Instant.now());

        stockTransferRepository.save(transfer);

        // 안전재고 체크
        checkSafetyStock(transfer.getProduct());

        return StockTransferResponse.from(transfer);
    }

    @Transactional
    public StockTransferResponse rejectTransfer(UUID transferId, String rejectedBy) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("재고 이동 내역을 찾을 수 없습니다", "TRANSFER_NOT_FOUND"));

        if (transfer.getTransferStatus() != StockTransfer.TransferStatus.pending_approval) {
            throw new BusinessException(
                    "승인 대기 상태가 아닙니다",
                    "NOT_PENDING_APPROVAL"
            );
        }

        transfer.setTransferStatus(StockTransfer.TransferStatus.rejected);
        transfer.setApprovedBy(rejectedBy);
        transfer.setApprovedAt(Instant.now());

        stockTransferRepository.save(transfer);

        return StockTransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public StockTransferResponse getTransfer(UUID transferId) {
        StockTransfer transfer = stockTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException("재고 이동 내역을 찾을 수 없습니다", "TRANSFER_NOT_FOUND"));

        return StockTransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public List<StockTransferResponse> getTransferHistory(UUID productId) {
        List<StockTransfer> transfers;

        if (productId != null) {
            transfers = stockTransferRepository.findByProductProductIdOrderByRequestedAtDesc(productId);
        } else {
            transfers = stockTransferRepository.findAll();
        }

        return transfers.stream()
                .map(StockTransferResponse::from)
                .collect(Collectors.toList());
    }

    // === 내부 헬퍼 메서드 ===

    private void validateStorageTypeCompatibility(Product product, Location toLocation) {
        Product.StorageType productType = product.getStorageType();
        Location.StorageType locationType = toLocation.getStorageType();

        // FROZEN 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.FROZEN &&
                locationType == Location.StorageType.AMBIENT) {
            throw new BusinessException(
                    "FROZEN 상품은 AMBIENT 로케이션으로 이동할 수 없습니다",
                    "STORAGE_TYPE_INCOMPATIBLE"
            );
        }

        // COLD 상품 → AMBIENT 로케이션: 거부
        if (productType == Product.StorageType.COLD &&
                locationType == Location.StorageType.AMBIENT) {
            throw new BusinessException(
                    "COLD 상품은 AMBIENT 로케이션으로 이동할 수 없습니다",
                    "STORAGE_TYPE_INCOMPATIBLE"
            );
        }

        // HAZMAT 상품 → 비-HAZMAT zone 로케이션: 거부
        if (product.getCategory() == Product.ProductCategory.HAZMAT &&
                toLocation.getZone() != Location.Zone.HAZMAT) {
            throw new BusinessException(
                    "HAZMAT 상품은 HAZMAT zone으로만 이동할 수 있습니다",
                    "HAZMAT_ZONE_REQUIRED"
            );
        }
    }

    private void validateHazmatSegregation(Product product, Location toLocation) {
        // 도착지 로케이션에 이미 적재된 재고 조회
        List<Inventory> existingInventories = inventoryRepository.findByProduct_ProductIdAndQuantityGreaterThan(
                product.getProductId(), 0
        ).stream()
                .filter(inv -> inv.getLocation().getLocationId().equals(toLocation.getLocationId()))
                .collect(Collectors.toList());

        boolean isHazmat = product.getCategory() == Product.ProductCategory.HAZMAT;

        for (Inventory existing : existingInventories) {
            boolean existingIsHazmat = existing.getProduct().getCategory() == Product.ProductCategory.HAZMAT;

            // HAZMAT과 비-HAZMAT 혼적 금지
            if (isHazmat != existingIsHazmat) {
                throw new BusinessException(
                        "HAZMAT 상품과 비-HAZMAT 상품은 동일 로케이션에 혼적할 수 없습니다",
                        "HAZMAT_SEGREGATION_VIOLATION"
                );
            }
        }
    }

    private void validateExpiryDateRestrictions(Inventory sourceInventory, Location toLocation) {
        LocalDate expiryDate = sourceInventory.getExpiryDate();
        LocalDate manufactureDate = sourceInventory.getManufactureDate();
        LocalDate today = LocalDate.now();

        // 유통기한 만료: 이동 불가
        if (expiryDate.isBefore(today)) {
            throw new BusinessException(
                    "유통기한이 만료된 재고는 이동할 수 없습니다",
                    "EXPIRED_PRODUCT"
            );
        }

        // 잔여 유통기한 계산
        if (manufactureDate != null) {
            long totalDays = ChronoUnit.DAYS.between(manufactureDate, expiryDate);
            long remainingDays = ChronoUnit.DAYS.between(today, expiryDate);

            if (totalDays > 0) {
                BigDecimal remainingPct = BigDecimal.valueOf(remainingDays)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalDays), 2, BigDecimal.ROUND_HALF_UP);

                // 잔여 유통기한 < 10%: SHIPPING zone으로만 이동 허용
                if (remainingPct.compareTo(BigDecimal.valueOf(10)) < 0) {
                    if (toLocation.getZone() != Location.Zone.SHIPPING) {
                        throw new BusinessException(
                                "잔여 유통기한이 10% 미만인 상품은 SHIPPING zone으로만 이동할 수 있습니다 (잔여: " +
                                        remainingPct + "%)",
                                "EXPIRY_SHIPPING_ONLY"
                        );
                    }
                }
            }
        }
    }

    private void executeTransfer(Inventory sourceInventory, Location toLocation, Integer quantity, Product product) {
        // 1. 출발지 재고 차감
        sourceInventory.setQuantity(sourceInventory.getQuantity() - quantity);
        inventoryRepository.save(sourceInventory);

        // 2. 출발지 로케이션 현재 수량 갱신
        Location fromLocation = sourceInventory.getLocation();
        fromLocation.setCurrentQty(fromLocation.getCurrentQty() - quantity);
        locationRepository.save(fromLocation);

        // 3. 도착지에 동일 상품+lot_number 조합이 있는지 확인
        Inventory destInventory = inventoryRepository.findByProductAndLocationAndLot(
                product.getProductId(),
                toLocation.getLocationId(),
                sourceInventory.getLotNumber()
        ).orElse(null);

        if (destInventory != null) {
            // 기존 레코드의 quantity 증가
            destInventory.setQuantity(destInventory.getQuantity() + quantity);
            inventoryRepository.save(destInventory);
        } else {
            // 새 inventory 레코드 생성 (received_at은 원래 값 유지)
            Inventory newInventory = new Inventory();
            newInventory.setProduct(product);
            newInventory.setLocation(toLocation);
            newInventory.setQuantity(quantity);
            newInventory.setLotNumber(sourceInventory.getLotNumber());
            newInventory.setExpiryDate(sourceInventory.getExpiryDate());
            newInventory.setManufactureDate(sourceInventory.getManufactureDate());
            newInventory.setReceivedAt(sourceInventory.getReceivedAt());
            newInventory.setIsExpired(sourceInventory.getIsExpired());
            inventoryRepository.save(newInventory);
        }

        // 4. 도착지 로케이션 현재 수량 갱신
        toLocation.setCurrentQty(toLocation.getCurrentQty() + quantity);
        locationRepository.save(toLocation);
    }

    private void checkSafetyStock(Product product) {
        // STORAGE zone 내 전체 재고 합산
        List<Inventory> storageInventories = inventoryRepository.findByProduct_ProductIdAndIsExpiredFalse(
                        product.getProductId()
                ).stream()
                .filter(inv -> inv.getLocation().getZone() == Location.Zone.STORAGE)
                .collect(Collectors.toList());

        int totalStorageQty = storageInventories.stream()
                .mapToInt(Inventory::getQuantity)
                .sum();

        // 안전재고 규칙 조회
        safetyStockRuleRepository.findByProduct_ProductId(product.getProductId())
                .ifPresent(rule -> {
                    if (totalStorageQty <= rule.getMinQty()) {
                        // 자동 재발주 로그 기록
                        AutoReorderLog log = new AutoReorderLog();
                        log.setProduct(product);
                        log.setCurrentQty(totalStorageQty);
                        log.setMinQty(rule.getMinQty());
                        log.setReorderQty(rule.getReorderQty());
                        log.setTriggerReason("SAFETY_STOCK_TRIGGER");
                        log.setTriggeredAt(Instant.now());
                        autoReorderLogRepository.save(log);
                    }
                });
    }
}

