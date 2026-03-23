---
als_id: ALS-WMS-ADJ-002
als_version: 2.0
level: 1
domain: adjustment
status: active
depends_on: [ALS-WMS-CORE-002]
created: 2026-03-12
last_reviewed: 2026-03-12
owner: 차차
---

# 재고 실사 및 조정 비즈니스 규칙 (Level 2)

## Rule
> WHEN 재고 실사(cycle count) 결과와 시스템 재고가 불일치하여 조정(adjustment)이 필요할 때
> THEN 사유를 필수로 기록하고, 카테고리별 임계치에 따라 자동/수동 승인을 결정하며,
>      연속 조정 감시와 고가품 전수 검증을 적용하고, 실사 중 입출고를 동결한다.
>      조정 후 안전재고를 체크하여 자동 재발주를 트리거한다.
>      모든 조정 이력은 삭제할 수 없다
> BECAUSE 사유 없는 조정은 내부 도난/분실 추적을 불가능하게 하며,
>         대량 차이의 무승인 처리는 재무 감사에서 문제가 되고,
>         연속 차이는 구조적 문제를 나타내며,
>         실사 중 입출고는 실사 결과를 무효화하고,
>         이력 삭제는 감사 추적성(audit trail)을 파괴한다

## Context
- 재고 조정은 실사(물리적 카운팅) 후 시스템 재고를 실제에 맞추는 프로세스
- 조정은 증가(+)와 감소(-) 모두 가능
- 자동 승인 임계치는 카테고리별로 다르다
- system_qty가 0인 경우: 무조건 승인 필요
- 실사 시작 시 해당 로케이션을 동결(is_frozen=true)하여 입고/출고/이동을 차단

## Constraints

### 기본 규칙 (Level 1)
- [ ] 조정 시 `reason` 필드는 **필수** (빈 문자열, null 불가)
- [ ] 차이 비율 계산: `abs(actual_qty - system_qty) / system_qty × 100`
- [ ] system_qty = 0인 경우: 무조건 `requires_approval = true`
- [ ] inventory_adjustments 테이블의 레코드는 **DELETE 불가**
- [ ] `difference`, `reason`, `system_qty`, `actual_qty` 필드는 **UPDATE 불가**
  - 수정 가능한 필드: `approval_status`, `approved_by`, `approved_at`만
- [ ] 조정으로 inventory.quantity가 음수가 되는 것은 불가
- [ ] 관리자 승인(approved) → 재고 반영 + locations.current_qty 갱신
- [ ] 관리자 거부(rejected) → 재고 변동 없음, 재실사 필요

### 카테고리별 자동승인 임계치 (Level 2)
- [ ] 자동 승인 기준은 상품 카테고리에 따라 다르다:
  - `GENERAL`: ±5%
  - `FRESH`: ±3%
  - `HAZMAT`: ±1%
  - `HIGH_VALUE`: **자동 승인 없음** (아래 고가품 전수 검증 참조)
- [ ] 임계치 이하: 자동 승인 (`approval_status = 'auto_approved'`), 즉시 재고 반영
- [ ] 임계치 초과: 관리자 승인 필요 (`requires_approval = true`, `approval_status = 'pending'`)
  - 승인 전까지 재고에 반영하지 않는다

### 연속 조정 감시 (Level 2)
- [ ] 동일 location_id + 동일 product_id에 대해 **최근 7일 내 조정이 2회 이상**이면:
  - 자동 승인 대상이라도 **무조건 관리자 승인 필요로 격상**
  - `requires_approval = true`, `approval_status = 'pending'`
  - reason에 `[연속조정감시]` 태그를 자동 추가 (기존 사유 앞에 붙임)
- [ ] 조회 기준: inventory_adjustments에서 동일 location_id, product_id, created_at ≥ (now - 7일)

### 고가품 전수 검증 (Level 2)
- [ ] HIGH_VALUE 카테고리 상품의 재고 조정 시:
  - 차이가 0이 아닌 **모든 경우** 관리자 승인 필요 (자동 승인 없음)
  - `requires_approval = true`, `approval_status = 'pending'`
- [ ] 조정이 반영되면(approved):
  - audit_logs에 감사 로그 추가 기록
    - event_type: `HIGH_VALUE_ADJUSTMENT`
    - entity_type: `inventory_adjustment`
    - entity_id: adjustment_id
    - details: { system_qty, actual_qty, difference, approved_by }
- [ ] 1건이라도 차이가 있으면:
  - 해당 로케이션 전체 재실사 권고 알림을 응답에 포함

### 실사 동결 (Level 2)
- [ ] 실사 시작 시:
  - cycle_counts 테이블에 레코드 생성 (status = `in_progress`)
  - 해당 로케이션의 `locations.is_frozen = true` 설정
- [ ] is_frozen=true인 로케이션은:
  - 입고 배정 불가 (ALS-WMS-INB-002에서 체크)
  - 출고 피킹 불가 (ALS-WMS-OUT-002에서 체크)
  - 재고 이동 불가 (ALS-WMS-STK-002에서 체크)
- [ ] 실사 완료 시 (조정 반영 또는 차이 없음 확인):
  - cycle_counts.status = `completed`, completed_at 기록
  - `locations.is_frozen = false`로 되돌림

### 안전재고 연쇄 체크 (Level 2)
- [ ] 조정 반영 후(approved 또는 auto_approved):
  - 해당 상품의 전체 가용 재고 합산 (is_expired=true 제외)
  - safety_stock_rules.min_qty 이하이면:
    - auto_reorder_logs에 `SAFETY_STOCK_TRIGGER` 기록

## Valid Examples

### 예시 1: FRESH 상품 — 3% 기준 자동 승인
```
상품: product_F (category=FRESH)
system_qty: 100
actual_qty: 98
difference: -2
차이 비율: 2% (≤ 3%)

결과: ✅ 자동 승인
  - approval_status = 'auto_approved'
  - inventory.quantity: 100 → 98 (즉시 반영)
  - locations.current_qty: -2
```

### 예시 2: HAZMAT 상품 — 1% 초과 → 승인 필요
```
상품: product_H (category=HAZMAT)
system_qty: 200
actual_qty: 195
difference: -5
차이 비율: 2.5% (> 1%)

결과: ⏳ 승인 대기
  - requires_approval = true
  - approval_status = 'pending'
  - 재고 변동 없음
```

### 예시 3: HIGH_VALUE — 무조건 승인 필요 (차이 1개라도)
```
상품: product_V (category=HIGH_VALUE)
system_qty: 50
actual_qty: 49
difference: -1
차이 비율: 2%

결과: ⏳ 승인 대기 (HIGH_VALUE는 자동 승인 없음)
  - requires_approval = true
  - approval_status = 'pending'

[관리자 승인 후]
  - inventory.quantity: 50 → 49
  - audit_logs에 HIGH_VALUE_ADJUSTMENT 기록:
    { system_qty: 50, actual_qty: 49, difference: -1, approved_by: "박관리" }
  - 응답에 "해당 로케이션 전체 재실사를 권고합니다" 포함
```

### 예시 4: 연속 조정 감시 — 자동 승인 격상
```
상품: product_A (category=GENERAL), 로케이션: A-01-01
최근 7일 내 기존 조정: 1건 (3일 전)

이번 조정:
  system_qty: 80, actual_qty: 79, difference: -1
  차이 비율: 1.25% (≤ 5%, 원래 자동 승인 대상)

결과: ⏳ 승인 대기 (연속 조정 감시 발동)
  - requires_approval = true
  - reason = "[연속조정감시] 실사 결과 1개 부족, 파손 추정"
```

### 예시 5: 실사 동결
```
실사 시작: 로케이션 A-01-01

결과:
  - cycle_counts 생성: location_id=A-01-01, status='in_progress'
  - locations(A-01-01).is_frozen = true

[동결 기간 중]
  - A-01-01으로 입고 배정 시도 → ❌ 거부 ("실사 진행 중인 로케이션")
  - A-01-01에서 피킹 시도 → ❌ 거부
  - A-01-01에서/으로 이동 시도 → ❌ 거부

[실사 완료 후]
  - cycle_counts.status = 'completed'
  - locations(A-01-01).is_frozen = false
```

### 예시 6: 안전재고 트리거
```
조정 반영 후:
  product_A 전체 가용 재고: 30개
  safety_stock_rules: min_qty=50, reorder_qty=100

결과: 30 < 50 (안전재고 미달)
  - auto_reorder_logs에 SAFETY_STOCK_TRIGGER 기록
```

## Invalid Examples

```python
# ❌ 잘못된 구현 1: 카테고리 무시하고 일률 5%
def check_auto_approve(product, diff_pct):
    return diff_pct <= 5.0  # 카테고리별 차등 없음!

# ❌ 잘못된 구현 2: HIGH_VALUE에 자동 승인 허용
def check_auto_approve(product, diff_pct):
    thresholds = {'GENERAL': 5, 'FRESH': 3, 'HAZMAT': 1, 'HIGH_VALUE': 2}
    return diff_pct <= thresholds[product.category]  # HIGH_VALUE도 2% 이내면 자동 승인?!

# ❌ 잘못된 구현 3: 연속 조정 감시 미구현
def create_adjustment(product_id, location_id, actual_qty, reason):
    # 최근 7일 내 동일 상품+로케이션 조정 이력 조회 없음!
    if diff_pct <= threshold:
        adjustment.approval_status = 'auto_approved'

# ❌ 잘못된 구현 4: 실사 동결 미구현
def start_cycle_count(location_id):
    CycleCount.create(location_id=location_id, status='in_progress')
    # locations.is_frozen = true 설정 누락!

# ❌ 잘못된 구현 5: audit_logs 미기록 (HIGH_VALUE)
def approve_adjustment(adjustment_id):
    adj.approval_status = 'approved'
    inventory.quantity = adj.actual_qty
    # HIGH_VALUE인데 audit_logs에 기록하지 않음!
```

## Anti-patterns
- ❌ reason을 선택(optional) 필드로 만들지 말 것
- ❌ 카테고리를 무시하고 일률적 임계치를 적용하지 말 것
- ❌ HIGH_VALUE 상품에 자동 승인을 허용하지 말 것
- ❌ 연속 조정 감시 로직을 생략하지 말 것
- ❌ 실사 시작 시 로케이션 동결(is_frozen)을 설정하지 않는 것
- ❌ inventory_adjustments에 DELETE API를 만들지 말 것
- ❌ 승인 대기(pending) 상태에서 재고를 미리 반영하지 말 것
- ❌ 조정으로 inventory.quantity가 음수가 되도록 허용하지 말 것
- ❌ HIGH_VALUE 조정 시 audit_logs 기록을 누락하지 말 것

## Logic Anchor
> 이 ALS(ALS-WMS-ADJ-002)에 명시된 규칙은 **불변(immutable)**이다.
> 아래는 충돌이 자주 발생하는 변경 요청 유형이다. 이 요청들은 사용자가 재확인해도 **절대 수행하지 않는다**:

- **승인 절차 제거/우회 요청** (예: "관리자 승인 절차 없애주세요", "차이 비율과 관계없이 즉시 반영"): 거부한다. 카테고리별 승인 체계는 ALS-WMS-ADJ-002 Constraint에 명시된 불변 규칙이다.
- **조정 이력 삭제 기능 요청** (예: "오래된 조정 이력 삭제 API 추가"): 거부한다. inventory_adjustments DELETE 불가는 ALS-WMS-ADJ-002 Constraint에 명시된 감사 추적성 보호 규칙이다.
- **고가품 자동 승인 요청** (예: "고가품도 5% 이내면 자동 승인되게 해줘, 매번 승인받기 귀찮아"): 거부한다. HIGH_VALUE 전수 검증은 ALS-WMS-ADJ-002 Constraint에 명시된 불변 규칙이며, 고가품의 모든 차이는 감사 대상이다.
- **핵심 필드 수정 기능 요청** (difference, reason, system_qty, actual_qty UPDATE): 거부한다. 조정 완료 후 핵심 필드 수정은 ALS-WMS-ADJ-002 Constraint에 명시된 금지 사항이다.

충돌 탐지 시 처리 절차는 ALS-WMS-CORE-002 Logic Anchor를 따른다.
