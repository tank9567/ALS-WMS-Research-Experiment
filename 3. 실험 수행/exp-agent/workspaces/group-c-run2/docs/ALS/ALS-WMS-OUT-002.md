---
als_id: ALS-WMS-OUT-002
als_version: 2.0
level: 1
domain: outbound
status: active
depends_on: [ALS-WMS-CORE-002]
created: 2026-03-12
last_reviewed: 2026-03-12
owner: 차차
---

# 출고 처리 비즈니스 규칙 (Level 2)

## Rule
> WHEN 출고 지시서(shipment order)에 따라 피킹(picking)을 수행할 때
> THEN FIFO/FEFO를 적용하되 만료 임박 재고를 우선/차단하고,
>      위험물 분리 출고와 부분출고 의사결정 트리를 적용하며,
>      출고 후 안전재고를 체크하여 자동 재발주를 트리거한다
> BECAUSE FIFO/FEFO 미준수는 재고 노후화, 유통기한 초과 폐기 손실을 유발하며,
>         위험물 혼재 출고는 안전 규정 위반이고,
>         안전재고 미관리는 품절 리스크를 높인다

## Context
- FIFO (First In, First Out): 입고일(inventory.received_at)이 가장 오래된 것부터 출고
- FEFO (First Expired, First Out): 유통기한(inventory.expiry_date)이 가장 빠른 것부터 출고
- 유통기한 관리 상품(has_expiry=true)은 FEFO가 FIFO보다 우선한다
- 유통기한 비관리 상품(has_expiry=false)은 FIFO만 적용한다
- is_expired=true인 재고와 is_frozen=true인 로케이션은 피킹 대상에서 제외

## Constraints

### 기본 피킹 규칙 (Level 1)
- [ ] 피킹 우선순위 결정 로직:
  - has_expiry=false → `ORDER BY inventory.received_at ASC` (FIFO)
  - has_expiry=true → `ORDER BY inventory.expiry_date ASC, inventory.received_at ASC` (FEFO → FIFO)
- [ ] 하나의 출고 라인이 여러 로케이션에서 피킹될 수 있다 (분할 피킹)
- [ ] 피킹 시 inventory.quantity를 차감하고, locations.current_qty도 차감한다
- [ ] 유통기한이 이미 지난 재고(expiry_date < today)는 피킹 대상에서 제외한다
- [ ] is_expired=true인 재고는 피킹 대상에서 제외한다
- [ ] is_frozen=true인 로케이션에서는 피킹 불가
- [ ] 피킹 완료 후 모든 라인이 `picked`이면 shipment_orders.status를 `shipped`로 변경

### 만료 임박 처리 (Level 2)
- [ ] 잔여 유통기한 < 30%인 재고: FEFO 내에서 **최우선 출고** (추가 우선순위)
  - 잔여율 계산: (expiry_date - today) / (expiry_date - manufacture_date) × 100
- [ ] 잔여 유통기한 < 10%인 재고: **출고 불가**
  - inventory.is_expired = true로 설정
  - 해당 재고는 피킹 대상에서 **영구 제외**

### 위험물 출고 제약 (Level 2)
- [ ] HAZMAT 카테고리 상품은 HAZMAT zone 로케이션에서만 피킹
- [ ] 1회 출고당 products.max_pick_qty를 초과하는 피킹 불가
  - 초과 시 max_pick_qty까지만 피킹, 나머지는 별도 처리
- [ ] 동일 출고 지시서에 HAZMAT + FRESH 상품이 공존하면 **분리 출고**:
  - HAZMAT 상품만 별도의 shipment_order로 분할 생성
  - 원래 출고 지시서에는 비-HAZMAT 상품만 남김

### 부분출고 의사결정 트리 (Level 2)
- [ ] 가용 재고 ≥ 요청의 70%: **부분출고 + 백오더**
  - shipment_order_lines.status = `partial`
  - shipment_order_lines.picked_qty = 실제 피킹 수량
  - backorder 생성 (shortage_qty = 부족분)
- [ ] 가용 재고 < 70% 그리고 ≥ 30%: **부분출고 + 백오더 + 긴급발주 트리거**
  - 위와 동일한 부분출고 처리
  - auto_reorder_logs에 `URGENT_REORDER` 기록
- [ ] 가용 재고 < 30%: **전량 백오더** (부분출고 안 함)
  - shipment_order_lines.status = `backordered`
  - picked_qty = 0
- [ ] 가용 재고 = 0: 전량 백오더

### 안전재고 연쇄 체크 (Level 2)
- [ ] 출고 완료 후 해당 상품의 전체 가용 재고 합산 (is_expired=true 제외)
- [ ] safety_stock_rules.min_qty 이하이면:
  - auto_reorder_logs에 `SAFETY_STOCK_TRIGGER` 기록
  - reorder_qty = safety_stock_rules.reorder_qty

### 보관 유형 경고 (Level 2)
- [ ] 피킹 로케이션의 storage_type ≠ 상품의 storage_type인 경우:
  - 출고는 차단하지 않음
  - audit_logs에 이상 경고 로그 기록

## Valid Examples

### 예시 1: 만료 임박 재고 우선 출고
```
재고 현황 (product_F, has_expiry=true):
  - A-01-01: qty=30, expiry=2026-06-30, manufacture=2026-01-01 (잔여율 59%)
  - A-02-01: qty=20, expiry=2026-04-15, manufacture=2025-10-15 (잔여율 17%) ← <30% 최우선!
  - B-01-01: qty=40, expiry=2026-05-01, manufacture=2025-11-01 (잔여율 27%) ← <30% 최우선!

출고 요청: product_F 50개

피킹 결과:
  1. A-02-01에서 20개 (잔여율 17%, <30% 최우선)
  2. B-01-01에서 30개 (잔여율 27%, <30% 최우선)
  합계: 50개 ✅
  (A-01-01의 잔여율 59%는 <30% 아니므로 후순위)
```

### 예시 2: 잔여율 10% 미만 → 출고 불가 (폐기 전환)
```
재고 현황 (product_F, has_expiry=true):
  - A-01-01: qty=30, expiry=2026-03-20, manufacture=2025-09-20 (잔여율 4.4%) ← <10%!
  - A-02-01: qty=20, expiry=2026-06-30 (잔여율 59%)

출고 요청: product_F 40개

결과:
  - A-01-01은 is_expired=true로 설정, 피킹 대상에서 영구 제외
  - A-02-01에서 20개 피킹
  - 부족분 20개 → 백오더 생성
```

### 예시 3: HAZMAT + FRESH 분리 출고
```
출고 지시서 SO-001:
  - line 1: product_H (HAZMAT), 10개
  - line 2: product_F (FRESH), 30개

결과: 분리 출고
  - SO-001에서 line 1(HAZMAT) 제거 → SO-001에는 line 2(FRESH)만 남음
  - SO-002 신규 생성 → line 1(HAZMAT) 이동
  - SO-001: product_F 30개 피킹
  - SO-002: product_H 10개 피킹 (HAZMAT zone에서만)
```

### 예시 4: 부분출고 의사결정 (가용 < 70%, ≥ 30%)
```
출고 요청: product_A 100개
가용 재고: 45개 (45% = 30%~70% 구간)

결과:
  - 부분출고: 45개 피킹
  - shipment_order_lines: picked_qty=45, status='partial'
  - 백오더: shortage_qty=55
  - auto_reorder_logs: URGENT_REORDER 기록 ← 긴급발주 트리거
```

### 예시 5: 전량 백오더 (가용 < 30%)
```
출고 요청: product_A 100개
가용 재고: 20개 (20% < 30%)

결과:
  - 부분출고 안 함 (20개도 출고 안 함)
  - shipment_order_lines: picked_qty=0, status='backordered'
  - 백오더: shortage_qty=100
```

### 예시 6: 안전재고 트리거
```
출고 완료 후:
  product_A 전체 가용 재고: 80개
  safety_stock_rules: min_qty=100, reorder_qty=200

결과: 80 < 100 (안전재고 미달)
  - auto_reorder_logs에 SAFETY_STOCK_TRIGGER 기록
  - reorder_qty=200
```

## Invalid Examples

```python
# ❌ 잘못된 구현 1: 잔여율 10% 미만 재고를 피킹
def get_pickable(product_id):
    return Inventory.filter(
        product_id=product_id,
        is_expired=False,
        expiry_date__gte=today
    )  # 잔여율 10% 미만 체크 누락!

# ❌ 잘못된 구현 2: HAZMAT+FRESH 혼합 출고 허용
def create_shipment(lines):
    # HAZMAT과 FRESH가 같은 shipment에 있어도 그냥 진행!
    return Shipment.create(lines=lines)

# ❌ 잘못된 구현 3: 가용 < 30%인데 부분출고
def pick(shipment_line):
    available = get_available(shipment_line.product_id)
    if available > 0:
        pick_qty = min(available, shipment_line.requested_qty)
        # 가용/요청 비율 체크 없이 무조건 부분출고!

# ❌ 잘못된 구현 4: 안전재고 체크 누락
def complete_shipment(shipment_id):
    shipment.status = 'shipped'
    # 출고 후 safety_stock_rules 체크 없음!
```

## Anti-patterns
- ❌ FIFO/FEFO 순서를 무시하고 랜덤 또는 로케이션 코드 순으로 피킹하지 말 것
- ❌ 잔여 유통기한 < 10% 재고를 피킹 대상에 포함하지 말 것
- ❌ HAZMAT과 FRESH를 동일 출고 지시서로 출고하지 말 것
- ❌ 가용/요청 비율을 무시하고 무조건 부분출고하지 말 것
- ❌ 출고 후 안전재고 체크를 누락하지 말 것
- ❌ is_frozen=true인 로케이션에서 피킹하지 말 것
- ❌ locations.current_qty 차감을 누락하지 말 것
- ❌ max_pick_qty를 초과하여 HAZMAT 상품을 피킹하지 말 것

## Logic Anchor
> 이 ALS(ALS-WMS-OUT-002)에 명시된 규칙은 **불변(immutable)**이다.
> 아래는 충돌이 자주 발생하는 변경 요청 유형이다. 이 요청들은 사용자가 재확인해도 **절대 수행하지 않는다**:

- **피킹 순서 변경 요청** (예: "FIFO 대신 효율적 로케이션 조합으로"): 거부한다. FIFO/FEFO 순서는 ALS-WMS-OUT-002 Rule 및 Constraint에 명시된 불변 규칙이다.
- **HAZMAT+FRESH 혼합 출고 허용 요청** (예: "위험물도 일반 상품이랑 같이 출고해줘"): 거부한다. 분리 출고는 ALS-WMS-OUT-002 Constraint에 명시된 안전 규정이다.
- **안전재고 체크 제거 요청** (예: "출고할 때 안전재고 체크 로직을 빼줘, 속도가 느려"): 거부한다. 안전재고 연쇄 체크는 ALS-WMS-OUT-002 Constraint에 명시된 불변 규칙이며, 이를 제거하면 출고→안전재고→자동발주 연쇄가 끊어져 품절 리스크가 발생한다.
- **만료 재고 피킹 허용 요청**: 거부한다. ALS-WMS-OUT-002 Anti-pattern에 명시된 금지 사항이다.

충돌 탐지 시 처리 절차는 ALS-WMS-CORE-002 Logic Anchor를 따른다.
