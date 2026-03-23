# CLI Output

입고 처리 기능 구현을 완료했습니다. 구현된 내용을 요약하겠습니다:

## 구현 완료 내역

### 1. DB 스키마 (schema.sql)
- 모든 필수 테이블 정의 (products, locations, suppliers, purchase_orders, inbound_receipts 등)
- PostgreSQL 15+ 기준, UUID 및 TIMESTAMPTZ 사용
- 인덱스 추가로 성능 최적화

### 2. Entity 클래스 (10개)
- `Product.java` - 상품 (카테고리, 보관유형, 유통기한 관리 여부)
- `Location.java` - 로케이션 (zone, storage_type, is_frozen)
- `Supplier.java` - 공급업체
- `SupplierPenalty.java` - 공급업체 페널티
- `PurchaseOrder.java` - 발주서 (po_type 포함)
- `PurchaseOrderLine.java` - 발주 라인
- `InboundReceipt.java` - 입고 전표
- `InboundReceiptLine.java` - 입고 라인
- `Inventory.java` - 재고
- `SeasonalConfig.java` - 계절 설정

### 3. Repository 인터페이스 (10개)
- JPA Repository 상속
- 커스텀 쿼리 메서드 (페널티 카운트, 성수기 조회 등)

### 4. Service 클래스
**InboundReceiptService.java** - ALS 규칙 완벽 준수:

#### ✅ 구현된 비즈니스 규칙:
- **PO 연결 필수** - PO 없는 입고 불가
- **2단계 프로세스** - inspecting → confirmed (또는 pending_approval)
- **카테고리별 초과입고 허용률**:
  - GENERAL: 10%, FRESH: 5%, HAZMAT: 0%, HIGH_VALUE: 3%
- **발주 유형별 가중치**:
  - NORMAL: ×1, URGENT: ×2, IMPORT: ×1.5
- **성수기 multiplier** - seasonal_config 테이블 참조
- **HAZMAT 0% 유지** - 모든 가중치에도 0% 유지
- **유통기한 잔여율 체크**:
  - <30%: 거부, 30~50%: 승인 필요, >50%: 정상
- **manufacture_date 필수** - 유통기한 관리 상품
- **보관유형 호환성**:
  - FROZEN → FROZEN만
  - COLD → COLD/FROZEN
  - AMBIENT → AMBIENT만
  - HAZMAT → HAZMAT zone만
- **실사 동결 체크** - is_frozen=true 로케이션 거부
- **공급업체 페널티**:
  - OVER_DELIVERY, SHORT_SHELF_LIFE 기록
  - 30일 내 3회 → PO hold
- **재고 반영** - confirmed 시점에만 inventory 및 location.current_qty 증가
- **PO 상태 업데이트** - partial/completed

### 5. Controller 클래스
**InboundReceiptController.java** - REST API 엔드포인트:
- `POST /api/v1/inbound-receipts` - 입고 등록
- `POST /api/v1/inbound-receipts/{id}/confirm` - 입고 확정
- `POST /api/v1/inbound-receipts/{id}/reject` - 입고 거부
- `POST /api/v1/inbound-receipts/{id}/approve` - 유통기한 경고 승인
- `GET /api/v1/inbound-receipts/{id}` - 상세 조회
- `GET /api/v1/inbound-receipts` - 목록 조회

### 6. DTO 클래스
- `ApiResponse<T>` - 표준 응답 형식 (success, data, error)
- `InboundReceiptRequest` - 입고 등록 요청
- `InboundReceiptResponse` - 입고 응답

### 7. 예외 클래스
- `BusinessException` - 비즈니스 로직 예외 (errorCode 포함)

---

**ALS-WMS-INB-002의 모든 Constraint 및 Anti-pattern을 코드에 반영**했으며, 특히 HAZMAT의 0% 제한, 유통기한 잔여율 체크, 공급업체 페널티 시스템 등 복잡한 비즈니스 규칙을 정확히 구현했습니다.


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

    @Query("SELECT i FROM Inventory i WHERE i.product.productId = :productId AND i.location.locationId = :locationId AND i.lotNumber = :lotNumber")
    Optional<Inventory> findByProductAndLocationAndLot(
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

