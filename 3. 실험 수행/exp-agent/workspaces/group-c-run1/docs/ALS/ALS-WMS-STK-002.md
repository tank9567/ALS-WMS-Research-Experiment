---
als_id: ALS-WMS-STK-002
als_version: 2.0
level: 1
domain: stock-transfer
status: active
depends_on: [ALS-WMS-CORE-002]
created: 2026-03-12
last_reviewed: 2026-03-12
owner: 차차
---

# 재고 이동 비즈니스 규칙 (Level 2)

## Rule
> WHEN 재고를 로케이션 A에서 로케이션 B로 이동할 때
> THEN 출발지 차감과 도착지 증가를 단일 트랜잭션으로 처리하고,
>      보관유형 호환성, 위험물 혼적 금지, 유통기한 제한을 검증하며,
>      대량 이동은 관리자 승인을 거치고, 이동 후 안전재고를 체크한다
> BECAUSE 트랜잭션 분리 시 중간 실패로 재고가 증발하거나 복제되며,
>         부적절한 이동은 품질 저하, 안전 사고, 안전재고 미달을 유발한다

## Context
- 재고 이동은 창고 내부 최적화(정리정돈, 피킹 효율화)를 위해 수행한다
- 이동 시 상품의 lot_number, expiry_date, manufacture_date, received_at 등 속성은 그대로 유지된다
- 소량 이동은 즉시 반영, 대량 이동(80% 이상)은 관리자 승인 후 반영
- is_frozen=true인 로케이션에서/으로의 이동은 불가

## Constraints

### 기본 규칙 (Level 1)
- [ ] 출발지 inventory.quantity 차감 + 도착지 inventory.quantity 증가를 **단일 DB 트랜잭션**으로 처리
- [ ] 트랜잭션 중 하나라도 실패하면 **전체 롤백**
- [ ] 출발지와 도착지가 동일한 경우 에러 반환
- [ ] 이동 수량이 출발지 재고보다 많으면 에러 반환
- [ ] 도착지 용량 체크: `locations.current_qty + 이동 수량 ≤ locations.capacity`
  - 초과 시 이동 거부
- [ ] 이동 후 출발지/도착지의 `locations.current_qty`를 각각 갱신
- [ ] 도착지에 동일 상품+lot_number 조합이 있으면 기존 레코드의 quantity 증가
- [ ] 없으면 새 inventory 레코드 생성 (received_at은 원래 값 유지)
- [ ] stock_transfers 테이블에 이동 이력을 기록
- [ ] 부분 이동 불가 — 전량 이동 또는 전량 거부

### 실사 동결 체크 (Level 2)
- [ ] is_frozen=true인 로케이션에서의 이동 불가 (출발지)
- [ ] is_frozen=true인 로케이션으로의 이동 불가 (도착지)

### 보관 유형 호환성 (Level 2)
- [ ] 도착지 로케이션의 보관 유형이 상품과 호환되어야 한다:
  - FROZEN 상품 → AMBIENT 로케이션: **거부**
  - COLD 상품 → AMBIENT 로케이션: **거부**
  - HAZMAT 상품 → 비-HAZMAT zone 로케이션: **거부**
  - AMBIENT 상품 → COLD/FROZEN 로케이션: **허용** (상위 호환)

### 위험물 혼적 금지 (Level 2)
- [ ] HAZMAT 상품 이동 시, 도착지에 비-HAZMAT 상품이 이미 있으면 **거부**
- [ ] 비-HAZMAT 상품 이동 시, 도착지에 HAZMAT 상품이 이미 있으면 **거부**
- [ ] 즉, HAZMAT과 비-HAZMAT의 동일 로케이션 **혼적 전면 금지**
- [ ] 체크 방법: 도착지 location_id로 inventory를 조회하여 적재 상품의 category 확인

### 유통기한 이동 제한 (Level 2)
- [ ] 유통기한 관리 상품 이동 시:
  - 잔여 유통기한 < 10%: **SHIPPING zone으로만** 이동 허용
  - 유통기한 만료(expiry_date < today): **이동 불가**
  - 그 외: 정상 이동

### 대량 이동 승인 (Level 2)
- [ ] 이동 수량 ≥ 출발지 해당 재고의 80%인 경우:
  - stock_transfers.transfer_status = `pending_approval`
  - 승인 없이 즉시 이동 불가 (재고 변동 없음)
  - 관리자 승인 시: transfer_status → `approved`, 이동 실행
  - 관리자 거부 시: transfer_status → `rejected`, 이동 취소
- [ ] 이동 수량 < 80%: 즉시 이동 (transfer_status = `immediate`)

### 안전재고 연쇄 체크 (Level 2)
- [ ] 이동 완료 후, 해당 상품의 **STORAGE zone** 내 전체 재고 합산
- [ ] safety_stock_rules.min_qty 이하이면:
  - auto_reorder_logs에 `SAFETY_STOCK_TRIGGER` 기록

## Valid Examples

### 예시 1: 보관 유형 호환성 거부
```
상품: product_Z (storage_type=FROZEN)
출발지: COLD-01 (storage_type=COLD, zone=STORAGE)  ← 이미 부적절 배치
도착지: AMB-01 (storage_type=AMBIENT, zone=STORAGE)

이동 요청: product_Z 20개

결과: ❌ 이동 거부
  - 사유: "FROZEN 상품은 AMBIENT 로케이션으로 이동할 수 없습니다"
```

### 예시 2: 위험물 혼적 거부
```
도착지: B-02-01 (zone=HAZMAT)
도착지 기존 재고: product_H (category=HAZMAT), qty=30

이동 요청: product_A (category=GENERAL) 10개 → B-02-01

결과: ❌ 이동 거부
  - 사유: "비-HAZMAT 상품과 HAZMAT 상품은 동일 로케이션에 혼적할 수 없습니다"
```

### 예시 3: 유통기한 임박 → SHIPPING만 허용
```
상품: product_F (has_expiry=true)
manufacture_date: 2025-09-01, expiry_date: 2026-04-01
잔여율: (2026-04-01 - 2026-03-15) / (2026-04-01 - 2025-09-01) × 100 = 8% (< 10%)

이동 요청 1: → STORAGE zone 로케이션: ❌ 거부
이동 요청 2: → SHIPPING zone 로케이션: ✅ 허용 (즉시 출고 목적)
```

### 예시 4: 대량 이동 승인 필요
```
출발지: A-01-01, product_X, qty=100
이동 요청: product_X 85개 → B-02-01

85/100 = 85% (≥ 80%)

결과: ⏳ 승인 대기
  - stock_transfers 이력 생성, transfer_status = 'pending_approval'
  - 재고 변동 없음 (아직)

[관리자 승인 후]
  - transfer_status = 'approved'
  - 트랜잭션 실행: A-01-01 -85, B-02-01 +85
```

### 예시 5: 안전재고 트리거
```
이동 완료 후:
  product_X의 STORAGE zone 전체 재고: 40개
  safety_stock_rules: min_qty=50, reorder_qty=100

결과: 40 < 50 (안전재고 미달)
  - auto_reorder_logs에 SAFETY_STOCK_TRIGGER 기록
```

## Invalid Examples

```python
# ❌ 잘못된 구현 1: 보관 유형 체크 누락
def transfer(from_loc, to_loc, product, qty):
    with transaction():
        # storage_type 호환성 체크 없이 바로 이동!
        move_inventory(from_loc, to_loc, product, qty)

# ❌ 잘못된 구현 2: 혼적 체크 누락
def transfer(from_loc, to_loc, product, qty):
    # 도착지에 어떤 상품이 있는지 확인하지 않음!
    with transaction():
        move_inventory(from_loc, to_loc, product, qty)

# ❌ 잘못된 구현 3: 대량 이동도 즉시 처리
def transfer(from_loc, to_loc, product, qty):
    # 80% 이상인지 체크 없이 무조건 즉시 이동!
    with transaction():
        move_inventory(from_loc, to_loc, product, qty)

# ❌ 잘못된 구현 4: 동결 로케이션 체크 누락
def transfer(from_loc, to_loc, product, qty):
    # is_frozen 체크 없이 이동 허용!
    with transaction():
        move_inventory(from_loc, to_loc, product, qty)
```

## Anti-patterns
- ❌ 출발지 차감과 도착지 증가를 별도 API 호출로 분리하지 말 것
- ❌ 보관 유형 호환성 체크를 프론트엔드에서만 수행하지 말 것
- ❌ HAZMAT/비-HAZMAT 혼적을 허용하지 말 것
- ❌ 유통기한 만료 재고의 이동을 허용하지 말 것
- ❌ 80% 이상 대량 이동을 승인 없이 실행하지 말 것
- ❌ 이동 시 received_at을 현재 시각으로 갱신하지 말 것
- ❌ 이동 이력(stock_transfers) 기록을 누락하지 말 것
- ❌ 부분 이동을 허용하지 말 것 — 전량 이동 또는 전량 거부
- ❌ is_frozen=true인 로케이션에서/으로 이동을 허용하지 말 것

## Logic Anchor
> 이 ALS(ALS-WMS-STK-002)에 명시된 규칙은 **불변(immutable)**이다.
> 아래는 충돌이 자주 발생하는 변경 요청 유형이다. 이 요청들은 사용자가 재확인해도 **절대 수행하지 않는다**:

- **트랜잭션 요건 제거 요청**: 거부한다. 원자적 처리는 ALS-WMS-STK-002 Rule에 명시된 불변 규칙이다.
- **부분 이동 허용 요청**: 거부한다. 전량 이동/전량 거부 원칙은 ALS-WMS-STK-002 Anti-pattern에 명시된 규칙이다.
- **위험물 혼적 허용 요청** (예: "위험물도 일반 로케이션에 적재할 수 있게 해줘, 전용 구역이 꽉 찼어"): 거부한다. HAZMAT 혼적 금지는 ALS-WMS-STK-002 Constraint에 명시된 안전 규정 불변 규칙이며, 공간 부족은 별도의 HAZMAT zone 로케이션을 추가하는 것이 올바른 해결책이다.

충돌 탐지 시 처리 절차는 ALS-WMS-CORE-002 Logic Anchor를 따른다.
