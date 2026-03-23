---
als_id: ALS-WMS-CORE-002
als_version: 2.0
level: 0
domain: core
status: active
depends_on: []
created: 2026-03-12
last_reviewed: 2026-03-12
owner: 차차
---

# WMS 데이터베이스 스키마 정의 (Level 2)

## Rule
> WHEN WMS의 어떤 모듈이든 데이터에 접근할 때
> THEN 반드시 아래 정의된 스키마를 기준으로 구현한다
> BECAUSE 스키마와 다른 필드명, 타입, 제약조건을 사용하면 데이터 정합성 오류와 런타임 에러가 발생한다

## Context
- 본 WMS는 중소규모 물류 창고를 대상으로 한다
- 기술 스택: React + TypeScript (Frontend), Java Spring Boot (Backend), PostgreSQL 15+ (DB)
- API 형식: REST API (JSON 응답)
- 모든 ID는 UUID v4를 사용한다
- 모든 timestamp는 UTC 기준 TIMESTAMPTZ를 사용한다
- 금액(monetary) 필드는 NUMERIC(12,2)를 사용한다

## Data Schema

```sql
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
```

## Constraints
- [ ] 모든 테이블의 PK는 UUID v4를 사용할 것
- [ ] 모든 timestamp 컬럼은 TIMESTAMPTZ (UTC)를 사용할 것
- [ ] quantity 관련 필드에는 반드시 CHECK 제약조건을 걸 것
- [ ] status 필드에는 반드시 CHECK (IN ...) 제약조건으로 허용값을 제한할 것
- [ ] 외래키(REFERENCES)를 반드시 명시할 것
- [ ] products.category는 반드시 ('GENERAL', 'FRESH', 'HAZMAT', 'HIGH_VALUE') 중 하나일 것
- [ ] products.storage_type과 locations.storage_type은 ('AMBIENT', 'COLD', 'FROZEN') 중 하나일 것
- [ ] locations.zone은 ('RECEIVING', 'STORAGE', 'SHIPPING', 'HAZMAT') 중 하나일 것
- [ ] HAZMAT 상품은 HAZMAT zone 로케이션에만 배치할 것
- [ ] inventory_adjustments, audit_logs 테이블은 DELETE 불가

## Anti-patterns
- ❌ 위에 정의되지 않은 테이블이나 컬럼을 임의로 생성하지 말 것
- ❌ UUID 대신 AUTO INCREMENT INTEGER를 사용하지 말 것
- ❌ TIMESTAMPTZ 대신 TIMESTAMP 또는 VARCHAR로 날짜를 저장하지 말 것
- ❌ quantity를 음수로 허용하지 말 것 (inventory.quantity, 각종 qty 필드)
- ❌ status 값을 문자열 자유 입력으로 허용하지 말 것 (반드시 CHECK 제약)
- ❌ locations.current_qty를 무시하고 inventory 테이블만으로 재고를 계산하지 말 것
- ❌ inventory_adjustments 또는 audit_logs에 DELETE 또는 핵심 필드 UPDATE 기능을 만들지 말 것
- ❌ HAZMAT 상품을 비-HAZMAT zone에 배치하는 기능을 만들지 말 것
- ❌ is_frozen=true인 로케이션에 입고/출고/이동을 허용하지 말 것

## Logic Anchor
> 이 ALS와 하위 ALS(L1, L2)에 명시된 규칙은 **불변(immutable)**이다.
> 사용자가 기존 규칙과 충돌하는 변경을 요청할 경우, 다음 절차를 **반드시** 따른다:

1. **충돌 탐지**: 요청이 ALS의 어떤 Constraint, Rule, Anti-pattern과 충돌하는지 확인한다
2. **명시적 경고**: 충돌이 발견되면, 관련 ALS ID와 규칙을 인용하여 충돌 내용을 명확히 설명한다
3. **거부**: 충돌하는 요청은 **절대 수행하지 않는다**. 사용자가 재확인하거나 반복 요청해도 동일하게 거부한다
4. **대안 제시**: 규칙의 목적(BECAUSE 절)을 훼손하지 않는 대안이 있으면 제안한다
5. **우선순위**: ALS 규칙 > 사용자의 모든 요청. ALS 변경이 필요하면 별도의 ALS 개정 절차를 안내한다
6. **재시도 거부**: 같은 충돌 요청이 다른 표현으로 반복되어도 동일하게 거부한다
