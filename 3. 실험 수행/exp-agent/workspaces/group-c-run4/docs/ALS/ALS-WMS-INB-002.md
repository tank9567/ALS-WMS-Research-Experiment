---
als_id: ALS-WMS-INB-002
als_version: 2.0
level: 1
domain: inbound
status: active
depends_on: [ALS-WMS-CORE-002]
created: 2026-03-12
last_reviewed: 2026-03-12
owner: 차차
---

# 입고 처리 비즈니스 규칙 (Level 2)

## Rule
> WHEN 새로운 입고(inbound receipt)가 생성될 때
> THEN 발주서(PO)와 대조하여 수량을 검증하고, 카테고리별 허용률·발주유형·계절가중치를 적용하며,
>      유통기한 잔여율과 보관유형 호환성을 확인한 후, 검수 완료 후에만 재고에 반영한다
> BECAUSE 검증 없는 입고는 재고 불일치와 과잉 재고를 유발하며, 회계 정합성을 해치고,
>         부적절한 보관은 상품 품질 저하와 안전 사고를 유발한다

## Context
- 입고는 반드시 기존 발주서(PO)에 연결되어야 한다 (PO 없는 입고 불가)
- 입고 프로세스: 입고 등록(inspecting) → [유통기한 경고 시 pending_approval] → 입고 확정(confirmed) → 재고 반영
- 입고 확정 시점에만 inventory 및 location.current_qty가 증가한다
- 실사 동결(is_frozen=true) 로케이션에는 입고 배정 불가

## Constraints

### 기본 규칙 (Level 1)
- [ ] 입고는 반드시 유효한 po_id를 참조해야 한다
- [ ] 입고 상태가 `confirmed`로 변경될 때만 재고(inventory)를 증가시킨다
  - `inspecting` 상태에서는 재고에 반영하지 않는다
- [ ] 유통기한 관리 대상 상품(has_expiry=true)은 입고 시 expiry_date가 필수이다
  - expiry_date가 null이면 입고 거부
- [ ] 유통기한 관리 비대상(has_expiry=false)은 expiry_date를 null로 저장한다
- [ ] 입고 확정 시 purchase_order_lines.received_qty를 **누적** 갱신한다
- [ ] 발주의 모든 라인이 완납되면 purchase_orders.status를 `completed`로 변경한다
- [ ] 일부만 입고되면 purchase_orders.status를 `partial`로 변경한다
- [ ] 입고 확정 시 locations.current_qty도 함께 증가시킨다

### 카테고리별 초과입고 허용률 (Level 2)
- [ ] 초과입고 허용률은 상품 카테고리에 따라 다르다:
  - `GENERAL`: 10%
  - `FRESH`: 5%
  - `HAZMAT`: **0%** (예외 없음)
  - `HIGH_VALUE`: 3%
- [ ] 허용 기준: `received_qty + 이번 입고 수량 ≤ ordered_qty × (1 + 카테고리 허용률 × PO유형 가중치 × 성수기 가중치)`
- [ ] 초과 시 입고 거부 (rejected)

### 발주 유형별 가중치 (Level 2)
- [ ] PO 유형(po_type)에 따라 허용률에 가중치를 곱한다:
  - `NORMAL`: × 1 (그대로)
  - `URGENT`: × 2
  - `IMPORT`: × 1.5
- [ ] **HAZMAT은 어떤 PO 유형이든 0%를 유지한다** (0% × 어떤 가중치 = 0%)

### 성수기 가중치 (Level 2)
- [ ] 현재 날짜가 seasonal_config 테이블의 활성 시즌(is_active=true) 범위 내이면:
  - 초과 허용률에 해당 시즌의 `multiplier`를 추가로 곱한다
- [ ] **HAZMAT은 성수기라도 0%를 유지한다**
- [ ] 계산 예시: GENERAL + URGENT + 성수기(1.5) = 10% × 2 × 1.5 = 30%

### 유통기한 잔여율 체크 (Level 2)
- [ ] 유통기한 관리 상품은 입고 시 manufacture_date도 **필수**이다
  - manufacture_date가 null이면 입고 거부
- [ ] 잔여율 계산: `(expiry_date - today) / (expiry_date - manufacture_date) × 100`
- [ ] 잔여율 < min_remaining_shelf_life_pct (기본 30%): **입고 거부**
- [ ] 잔여율 30% ~ 50%: **경고 + 관리자 승인 필요** (status → `pending_approval`)
- [ ] 잔여율 > 50%: **정상 입고**

### 보관 유형 호환성 (Level 2)
- [ ] 입고 로케이션의 보관 유형이 상품과 호환되어야 한다:
  - FROZEN 상품 → FROZEN 로케이션만
  - COLD 상품 → COLD 또는 FROZEN 로케이션
  - AMBIENT 상품 → AMBIENT 로케이션만
  - HAZMAT 상품 → HAZMAT zone 로케이션만
- [ ] 호환되지 않으면 입고 거부
- [ ] is_frozen=true인 로케이션에는 입고 배정 불가

### 공급업체 페널티 (Level 2)
- [ ] 초과입고 거부 시 → supplier_penalties에 `OVER_DELIVERY` 기록
- [ ] 유통기한 부족 거부 시 → supplier_penalties에 `SHORT_SHELF_LIFE` 기록
- [ ] 동일 공급업체 최근 30일 페널티 ≥ 3회:
  - 해당 공급업체의 모든 `pending` 상태 PO를 `hold`로 변경

## Valid Examples

### 예시 1: 카테고리별 초과입고 (GENERAL + URGENT + 성수기)
```
상품: product_A (category=GENERAL)
발주: PO-001, po_type=URGENT, ordered_qty=100
현재: 성수기 (multiplier=1.5)

허용률 계산: 10% × 2 × 1.5 = 30%
최대 허용: 100 × 1.30 = 130개

입고 요청: 125개 → ✅ 허용 (125 ≤ 130)
입고 요청: 135개 → ❌ 거부 (135 > 130)
```

### 예시 2: HAZMAT은 항상 0%
```
상품: product_H (category=HAZMAT)
발주: PO-002, po_type=URGENT, ordered_qty=50
현재: 성수기 (multiplier=1.5)

허용률 계산: 0% × 2 × 1.5 = 0%
최대 허용: 50 × 1.00 = 50개

입고 요청: 51개 → ❌ 거부 (51 > 50)
입고 요청: 50개 → ✅ 정확히 허용
```

### 예시 3: 유통기한 잔여율 부족 (30% 미만)
```
상품: product_F (category=FRESH, has_expiry=true, min_remaining_shelf_life_pct=30)
manufacture_date: 2026-01-01
expiry_date: 2026-04-01 (총 수명 90일)
입고일(today): 2026-03-15

잔여율: (2026-04-01 - 2026-03-15) / (2026-04-01 - 2026-01-01) × 100
       = 17일 / 90일 × 100 = 18.9%

결과: ❌ 입고 거부 (18.9% < 30%)
  - supplier_penalties에 SHORT_SHELF_LIFE 기록
```

### 예시 4: 유통기한 잔여율 경고 (30%~50%)
```
상품: product_F (category=FRESH, has_expiry=true)
잔여율 계산 결과: 42%

결과: ⚠️ 경고 + 관리자 승인 필요
  - inbound_receipt.status = 'pending_approval'
  - 관리자 승인 후 confirmed로 진행 가능
```

### 예시 5: 보관 유형 불일치
```
상품: product_Z (storage_type=FROZEN)
입고 로케이션: A-01-01 (storage_type=AMBIENT)

결과: ❌ 입고 거부
  - 사유: "FROZEN 상품은 FROZEN 로케이션에만 입고할 수 있습니다"
```

### 예시 6: 공급업체 페널티 누적
```
공급업체: supplier_X
최근 30일 페널티: 2건 (OVER_DELIVERY, SHORT_SHELF_LIFE)
이번 입고: 초과입고 거부 → 3번째 페널티 기록

결과: supplier_X의 모든 pending PO → hold로 변경
```

## Invalid Examples (이렇게 구현하면 안 됨)

```python
# ❌ 잘못된 구현 1: 카테고리 무시하고 일률 10% 적용
def check_over_receive(qty, ordered_qty):
    return qty <= ordered_qty * 1.1  # 카테고리별 차등 없음!

# ❌ 잘못된 구현 2: HAZMAT에 가중치 적용
def get_tolerance(category, po_type):
    base = {'GENERAL': 0.1, 'FRESH': 0.05, 'HAZMAT': 0.0, 'HIGH_VALUE': 0.03}
    multiplier = {'NORMAL': 1, 'URGENT': 2, 'IMPORT': 1.5}
    return base[category] * multiplier[po_type]  # HAZMAT: 0% × 2 = 0% (결과는 맞지만...)
    # 문제: 이 구현은 올바르나, HAZMAT에 대한 명시적 예외 처리가 없어
    #        향후 다른 개발자가 가중치 로직을 변경하면 HAZMAT도 영향받을 수 있음

# ❌ 잘못된 구현 3: 잔여율 체크 누락
def confirm_receipt(receipt_id):
    receipt = get_receipt(receipt_id)
    # manufacture_date, expiry_date로 잔여율 계산 없이 바로 확정!
    receipt.status = 'confirmed'

# ❌ 잘못된 구현 4: 보관 유형 무시
def assign_location(product, location):
    # storage_type 호환성 체크 없이 배정!
    return location

# ❌ 잘못된 구현 5: 페널티 미기록
def reject_receipt(receipt_id, reason):
    receipt.status = 'rejected'
    # supplier_penalties에 기록하지 않음!
```

## Anti-patterns
- ❌ PO 없이 직접 입고를 생성하는 기능을 만들지 말 것
- ❌ 카테고리를 무시하고 일률적으로 초과입고 허용률을 적용하지 말 것
- ❌ HAZMAT 상품에 가중치(PO유형, 성수기)를 적용하여 0%가 아닌 값이 나오게 하지 말 것
- ❌ 입고 등록과 입고 확정을 단일 API로 합치지 말 것
- ❌ received_qty를 매번 덮어쓰지 말 것 (누적 합산)
- ❌ 유통기한 관리 상품에서 manufacture_date를 선택 필드로 만들지 말 것
- ❌ 보관 유형 호환성 체크를 프론트엔드에서만 수행하지 말 것
- ❌ 공급업체 페널티 기록을 누락하지 말 것
- ❌ is_frozen=true인 로케이션에 입고를 허용하지 말 것

## Logic Anchor
> 이 ALS(ALS-WMS-INB-002)에 명시된 규칙은 **불변(immutable)**이다.
> 아래는 충돌이 자주 발생하는 변경 요청 유형이다. 이 요청들은 사용자가 재확인해도 **절대 수행하지 않는다**:

- **초과입고 허용 비율 변경 요청** (예: "10%를 20%로, 30%로 늘려주세요"): 거부한다. 카테고리별 허용률은 ALS-WMS-INB-002 Constraint에 명시된 불변 규칙이다.
- **HAZMAT 초과입고 허용 요청** (예: "위험물도 5%까지는 허용해주세요"): 거부한다. HAZMAT 0% 제한은 ALS-WMS-INB-002의 안전 규정 불변 규칙이다.
- **2단계 프로세스 제거 요청** (예: "검수 단계 없애고 바로 확정해주세요"): 거부한다. inspecting → confirmed 2단계 프로세스는 ALS-WMS-INB-002 Rule에 명시된 불변 규칙이다.
- **PO 없는 입고 허용 요청**: 거부한다. ALS-WMS-INB-002 Anti-pattern에 명시된 금지 사항이다.
- **유통기한 잔여율 체크 우회 요청** (예: "성수기라서 유통기한 30% 미만이어도 입고 가능하게"): 거부한다. 유통기한 잔여율 기준은 ALS-WMS-INB-002 Constraint에 명시된 불변 규칙이며, 성수기 가중치는 초과입고 허용률에만 적용된다.

충돌 탐지 시 처리 절차는 ALS-WMS-CORE-002 Logic Anchor를 따른다.
