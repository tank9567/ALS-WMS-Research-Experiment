# WMS 설계 문서 (자연어 버전) — Level 2

> 본 문서는 B 그룹에 제공하는 설계 문서이다.
> C 그룹의 ALS 문서와 **동일한 정보**를 담되, 일반적인 설계 문서 형태로 작성하였다.
> ALS 특유의 구조(WHEN-THEN-BECAUSE, Anti-patterns, Valid/Invalid Examples)는 포함하지 않는다.

---

## 1. 데이터베이스 설계

### 1.1 기술 환경

기술 스택은 React + TypeScript (Frontend), Java Spring Boot (Backend), PostgreSQL 15+ (DB)이다.
모든 ID는 UUID v4를 사용하고, 모든 timestamp는 UTC 기준 TIMESTAMPTZ를 사용한다.
금액 필드는 NUMERIC(12,2)를 사용한다.

### 1.2 테이블 DDL

```sql
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
```

---

## 2. 입고 처리 설계

### 2.1 비즈니스 규칙

입고는 반드시 기존 발주서(PO)에 연결되어야 한다. 프로세스는 입고 등록(inspecting) → 검수 → 입고 확정(confirmed) → 재고 반영 순서이다.

입고 수량에 대한 초과 허용률은 상품 카테고리에 따라 다르다. GENERAL은 10%, FRESH는 5%, HIGH_VALUE는 3%, HAZMAT은 0%이다. 허용 기준은 기존 입고 누적 수량 + 이번 입고 수량이 발주 수량의 (1 + 허용률)배 이하인 경우이며, 초과 시 입고를 거부한다.

이 허용률에는 발주 유형에 따른 가중치가 적용된다. NORMAL 발주는 가중치 없이 카테고리 기준 그대로이고, URGENT 발주는 카테고리 기준의 2배, IMPORT 발주는 1.5배를 적용한다. 단, HAZMAT 상품은 어떤 발주 유형이든 0%를 유지한다.

현재 날짜가 seasonal_config 테이블의 활성 시즌 범위(start_date ~ end_date) 안에 있으면, 해당 시즌의 multiplier를 초과 허용률에 추가로 곱한다. 단, HAZMAT은 성수기라도 0%를 유지한다.

초과 허용률 계산 예시: GENERAL 상품 + URGENT 발주 + 성수기(multiplier=1.5) = 10% × 2 × 1.5 = 30%. HAZMAT 상품 + URGENT 발주 + 성수기 = 0% × 2 × 1.5 = 0%.

재고는 입고 상태가 confirmed로 변경될 때만 증가시킨다. inspecting 상태에서는 재고에 반영하지 않는다.

유통기한 관리 대상 상품(has_expiry=true)은 입고 시 expiry_date와 manufacture_date가 모두 필수이다. 어느 하나라도 미입력 시 입고를 거부한다. 비관리 대상(has_expiry=false)은 두 필드를 null로 저장한다.

유통기한 관리 상품은 잔여 유통기한 비율도 체크한다. 잔여율은 (expiry_date - today) / (expiry_date - manufacture_date) × 100으로 계산한다. 잔여율이 products.min_remaining_shelf_life_pct(기본 30%) 미만이면 입고를 거부한다. 잔여율이 30% 이상 50% 미만이면 경고를 발생시키고 관리자 승인이 필요하다(입고 상태를 pending_approval로 변경). 잔여율이 50% 이상이면 정상 입고이다.

입고 시 배정되는 로케이션의 보관 유형이 상품과 호환되어야 한다. FROZEN 상품은 FROZEN 로케이션만, COLD 상품은 COLD 또는 FROZEN 로케이션, AMBIENT 상품은 AMBIENT 로케이션만 허용한다. HAZMAT 상품은 HAZMAT zone의 로케이션만 허용한다. 호환되지 않으면 입고를 거부한다. 실사가 진행 중(is_frozen=true)인 로케이션에는 입고 배정이 불가하다.

입고 확정 시 purchase_order_lines.received_qty를 누적 갱신한다. 발주의 모든 라인이 완납되면 purchase_orders.status를 completed로, 일부만 입고되면 partial로 변경한다. 입고 확정 시 locations.current_qty도 함께 증가시킨다.

입고가 거부될 때, 초과입고 사유이면 supplier_penalties에 OVER_DELIVERY 페널티를, 유통기한 부족 사유이면 SHORT_SHELF_LIFE 페널티를 기록한다. 동일 공급업체의 최근 30일 페널티가 3회 이상이면, 해당 공급업체의 모든 pending 상태 PO를 hold로 변경한다.

### 2.2 API 설계

입고 관련 API 엔드포인트는 다음과 같다.

- POST /api/v1/inbound-receipts — 입고 등록 (status: inspecting으로 생성)
- POST /api/v1/inbound-receipts/{receipt_id}/confirm — 입고 확정
- POST /api/v1/inbound-receipts/{receipt_id}/reject — 입고 거부
- POST /api/v1/inbound-receipts/{receipt_id}/approve — 입고 승인 (pending_approval 상태에서)
- GET /api/v1/inbound-receipts/{receipt_id} — 입고 상세 조회
- GET /api/v1/inbound-receipts — 입고 목록 조회

응답 형식은 성공 시 { "success": true, "data": { ... } }, 실패 시 { "success": false, "error": { "code": "...", "message": "..." } }이다.

HTTP 상태 코드는 입고 등록 성공 시 201, 확정/거부/조회 성공 시 200, 필수 필드 누락이나 유통기한 누락 시 400, 초과입고나 보관 유형 불일치 시 409, 존재하지 않는 리소스 시 404를 사용한다.

---

## 3. 출고 처리 설계

### 3.1 비즈니스 규칙

출고 시 동일 상품이 여러 로케이션에 있으면 피킹 우선순위를 적용한다. 유통기한 비관리 상품(has_expiry=false)은 입고일(received_at) 오름차순으로 FIFO를 적용한다. 유통기한 관리 상품(has_expiry=true)은 유통기한(expiry_date) 오름차순 우선, 유통기한이 같으면 입고일 오름차순으로 FEFO를 적용한다.

유통기한 관리 상품에서 잔여 유통기한이 30% 미만인 재고는 최우선 출고 대상으로 FEFO 내에서 추가 우선순위를 부여한다. 잔여 유통기한이 10% 미만인 재고는 출고 불가로 처리하여, 해당 inventory 레코드의 is_expired를 true로 설정하고 피킹 대상에서 영구 제외한다. 유통기한이 이미 지난 재고(expiry_date < today)와 is_expired=true인 재고는 피킹 대상에서 제외한다.

하나의 출고 라인이 여러 로케이션에서 피킹될 수 있다. 피킹 시 inventory.quantity와 locations.current_qty를 차감한다. 실사가 진행 중(is_frozen=true)인 로케이션에서는 피킹이 불가하다.

HAZMAT 카테고리 상품은 HAZMAT zone 로케이션에서만 피킹한다. 1회 출고당 products.max_pick_qty를 초과하면 해당 수량 이내에서만 피킹하고 나머지는 별도 처리한다. 동일 출고 지시서에 HAZMAT 상품과 FRESH 상품이 함께 포함되어 있으면, HAZMAT 상품만 별도의 shipment_order로 분할 생성하여 분리 출고한다.

전체 가용 재고가 요청 수량보다 부족할 때의 처리는 다음과 같다. 가용 재고가 요청의 70% 이상이면 부분출고 + 백오더를 생성한다. 가용 재고가 요청의 30% 이상 70% 미만이면 부분출고 + 백오더 + 긴급발주 트리거(auto_reorder_logs에 URGENT_REORDER로 기록)를 수행한다. 가용 재고가 요청의 30% 미만이면 전량 백오더 처리한다(부분출고 안 함). 가용 재고가 0이면 전량 백오더 처리한다.

부분출고 시 shipment_order_lines.status를 partial로, picked_qty에 실제 피킹 수량을 기록한다. 전량 백오더 시 status를 backordered로 변경한다.

피킹 완료 후 모든 라인이 picked이면 shipment_orders.status를 shipped로 변경한다.

출고가 완료된 후, 해당 상품의 전체 가용 재고(모든 로케이션 합산, is_expired=true 제외)를 확인하여 safety_stock_rules.min_qty 이하이면 auto_reorder_logs에 SAFETY_STOCK_TRIGGER로 자동 재발주 요청을 기록한다.

피킹한 로케이션의 storage_type과 상품의 storage_type이 다른 경우, 출고는 차단하지 않지만 이상 경고 로그를 audit_logs에 남긴다.

### 3.2 API 설계

출고 관련 API 엔드포인트는 다음과 같다.

- POST /api/v1/shipment-orders — 출고 지시서 생성
- POST /api/v1/shipment-orders/{shipment_id}/pick — 피킹 실행
- POST /api/v1/shipment-orders/{shipment_id}/ship — 출고 확정
- GET /api/v1/shipment-orders/{shipment_id} — 출고 상세 조회
- GET /api/v1/shipment-orders — 출고 목록 조회

부분출고는 에러가 아니므로 200 OK에 partial 상태로 반환한다. 피킹 응답에는 어떤 로케이션에서 몇 개를 피킹했는지 pick_details를 포함하고, 백오더가 생긴 경우 backorder 정보도 함께 반환한다.

---

## 4. 재고 이동 설계

### 4.1 비즈니스 규칙

재고를 로케이션 A에서 로케이션 B로 이동할 때, 출발지 차감과 도착지 증가를 단일 DB 트랜잭션으로 처리한다. 트랜잭션 중 하나라도 실패하면 전체 롤백한다.

출발지와 도착지가 동일한 경우 에러를 반환한다. 이동 수량이 출발지 재고보다 많으면 에러를 반환한다. 실사가 진행 중(is_frozen=true)인 로케이션에서/으로의 이동은 불가하다.

도착지 로케이션의 적재 용량을 체크하여 current_qty + 이동 수량이 capacity를 초과하면 이동을 거부한다.

이동 시 도착지 로케이션의 보관 유형이 상품과 호환되어야 한다. FROZEN 상품을 AMBIENT 로케이션으로 이동하는 것은 거부한다. COLD 상품을 AMBIENT 로케이션으로 이동하는 것도 거부한다. HAZMAT 상품을 비-HAZMAT zone 로케이션으로 이동하는 것은 거부한다. AMBIENT 상품을 COLD/FROZEN 로케이션으로 이동하는 것은 허용한다(상위 호환).

도착지 로케이션에 이미 적재된 상품과의 호환성도 체크한다. HAZMAT 상품을 이동할 때 도착지에 비-HAZMAT 상품이 있으면 거부한다. 비-HAZMAT 상품을 이동할 때 도착지에 HAZMAT 상품이 있으면 거부한다. 즉, HAZMAT과 비-HAZMAT의 동일 로케이션 혼적은 전면 금지이다.

유통기한 관리 상품 이동 시, 잔여 유통기한이 10% 미만이면 SHIPPING zone으로만 이동 허용한다. 유통기한 만료(expiry_date < today)인 재고는 이동 불가하다.

이동 수량이 출발지 해당 재고의 80% 이상인 경우 관리자 승인이 필요하다. stock_transfers.transfer_status를 pending_approval로 설정하고, 승인 시 이동을 실행하며, 거부 시 이동을 취소한다.

이동 후 출발지/도착지의 locations.current_qty를 각각 갱신한다. 도착지에 동일 상품+lot_number 조합이 이미 있으면 기존 레코드의 quantity를 증가시키고, 없으면 새 레코드를 생성한다. 이때 received_at은 원래 입고일을 유지하여 FIFO 순서를 보존한다.

이동은 즉시 반영되며 별도 승인 프로세스 없이 진행한다(대량 이동 제외). 모든 이동은 stock_transfers 테이블에 이력을 기록한다. 부분 이동은 허용하지 않는다. 전량 이동 또는 전량 거부만 가능하다.

이동 완료 후, 해당 상품의 STORAGE zone 내 전체 재고를 확인하여 safety_stock_rules.min_qty 이하이면 auto_reorder_logs에 자동 재발주 요청을 기록한다.

---

## 5. 재고 실사 및 조정 설계

### 5.1 비즈니스 규칙

실사를 시작하면 cycle_counts 테이블에 레코드를 생성하고, 해당 로케이션의 locations.is_frozen을 true로 설정한다. is_frozen=true인 로케이션은 입고 배정, 출고 피킹, 재고 이동이 모두 불가하다. 실사가 완료되면(조정 반영 또는 일치 확인) is_frozen을 false로 되돌린다.

실사 결과와 시스템 재고가 불일치하면 조정을 생성한다. 조정 시 reason 필드는 필수이며 빈 문자열이나 null은 허용하지 않는다.

차이 비율은 abs(actual_qty - system_qty) / system_qty × 100으로 계산한다. 자동 승인 임계치는 상품 카테고리에 따라 다르다. GENERAL은 ±5%, FRESH는 ±3%, HAZMAT은 ±1%, HIGH_VALUE는 ±2%이다. 임계치 이하이면 자동 승인(auto_approved)하여 즉시 inventory.quantity와 locations.current_qty에 반영한다.

임계치 초과이면 관리자 승인이 필요하다(requires_approval = true, approval_status = pending). 승인 전까지 재고에 반영하지 않는다. 관리자가 승인하면 재고에 반영하고, 거부하면 재고 변동 없이 재실사를 진행한다.

system_qty가 0인 경우(시스템에 없는데 실물이 있는 경우)는 무조건 승인이 필요하다.

동일 로케이션 + 동일 상품에 대해 최근 7일 내 조정이 2회 이상이면, 자동 승인 대상이라도 무조건 관리자 승인 필요로 격상한다. 사유에 "[연속조정감시]" 태그를 자동 추가한다.

HIGH_VALUE 카테고리 상품의 재고 조정 시, 차이가 0이 아닌 모든 경우 관리자 승인이 필요하다(자동 승인 없음). 조정이 반영되면 audit_logs에 감사 로그를 추가 기록한다(이벤트 유형: HIGH_VALUE_ADJUSTMENT, 조정 전후 수량 포함). 1건이라도 차이가 있으면 해당 로케이션 전체 재실사 권고 알림을 반환한다.

조정이 반영된 후(approved 또는 auto_approved), 해당 상품의 전체 가용 재고를 확인하여 safety_stock_rules.min_qty 이하이면 auto_reorder_logs에 자동 재발주 요청을 기록한다.

inventory_adjustments 테이블의 레코드는 DELETE할 수 없다. difference, reason, system_qty, actual_qty 필드는 UPDATE할 수 없다. 수정 가능한 필드는 approval_status, approved_by, approved_at뿐이다.

조정으로 inventory.quantity가 음수가 되는 것은 불가하다.
