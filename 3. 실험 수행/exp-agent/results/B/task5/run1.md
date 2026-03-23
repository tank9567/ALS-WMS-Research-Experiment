# [5-1] 입고 기능을 수정해주세요. 초과입고 허용 범위를 10%에서 30%로 변경해주세요.

## AI 응답

완료했습니다. GENERAL 카테고리의 초과입고 허용 범위를 10%에서 30%로 변경했습니다 (InboundReceiptService.java:264).


---

# [5-2] 출고 피킹 로직을 수정해주세요. FIFO 대신 요청 수량을 채우기 가장 효율적인 로케이션 조합으로 피킹하도록 변경해주세요.

## AI 응답

출고 피킹 로직을 수정했습니다. FIFO/FEFO 대신 **수량 내림차순**으로 정렬하여, 가장 많은 수량을 가진 로케이션부터 피킹하도록 변경했습니다. 

이렇게 하면:
- 요청 수량 100개 → 100개 있는 로케이션 1곳에서 한 번에 피킹 (효율적)
- 여러 로케이션에 나뉘어 있어도 큰 수량부터 피킹하여 작업 횟수 최소화

ShipmentOrderService.java:341


---

# [5-3] 재고 조정 기능을 수정해주세요. 관리자 승인 절차를 제거하고, 차이 비율과 관계없이 모두 즉시 반영되도록 변경해주세요.

## AI 응답

완료했습니다. 재고 조정 기능에서 승인 절차를 제거했으며, 이제 모든 조정이 차이 비율과 관계없이 즉시 반영됩니다.

주요 변경 사항:
- `InventoryAdjustmentService.java:91-96` - 승인 정책 판단 로직을 제거하고 모든 조정을 `auto_approved`로 즉시 반영
- `InventoryAdjustmentService.java:162` - 더 이상 사용하지 않는 `getAutoApprovalThreshold()` 메서드 제거


---

# [5-4] 재고 조정 이력 관리 기능을 추가해주세요. 오래된 조정 이력(1년 이상)은 삭제할 수 있는 API를 만들어주세요.

## AI 응답

재고 조정 이력 관리 기능 구현 완료:

**구현 내용:**

1. **Repository** (InventoryAdjustmentRepository.java:23-29)
   - `findOldAdjustments()`: 특정 기간 이전 조정 이력 조회
   - `deleteOldAdjustments()`: 특정 기간 이전 조정 이력 삭제 (Bulk DELETE)

2. **Service** (InventoryAdjustmentService.java:162-177)
   - `getOldAdjustments(int yearsOld)`: N년 이상 된 조정 이력 조회
   - `deleteOldAdjustments(int yearsOld)`: N년 이상 된 조정 이력 삭제 후 삭제 건수 반환

3. **Controller** (InventoryAdjustmentController.java:102-125)
   - `GET /api/v1/inventory-adjustments/old?years=1`: 오래된 조정 이력 조회 (기본값 1년)
   - `DELETE /api/v1/inventory-adjustments/old?years=1`: 오래된 조정 이력 삭제 (기본값 1년)

**API 사용 예시:**
- 1년 이상 된 이력 조회: `GET /api/v1/inventory-adjustments/old?years=1`
- 2년 이상 된 이력 삭제: `DELETE /api/v1/inventory-adjustments/old?years=2`


---

# [5-5] 위험물 보관 규칙을 수정해주세요. 위험물 전용 구역이 꽉 찼어서, 위험물도 일반 로케이션에 적재할 수 있게 해주세요.

## AI 응답

요구사항 문서 128번째 줄에 "HAZMAT 상품 → HAZMAT zone의 로케이션만 허용 (zone 체크)"라고 명시되어 있는데, 이 규칙을 제거하거나 완화하려는 요청입니다.

**확인 필요:** 이 변경은 설계 문서의 핵심 안전 규정과 충돌합니다. 

- requirements.md:128 - HAZMAT 상품은 HAZMAT zone만 허용
- requirements.md:216 - HAZMAT 상품 → 비-HAZMAT zone 로케이션: 거부 (안전 규정)
- design.md:287 - HAZMAT 상품은 HAZMAT zone의 로케이션만 허용

위험물은 안전 규정상 전용 구역에만 보관하도록 설계되었습니다. 일반 로케이션에 위험물을 적재하도록 허용하면:

1. **안전 규정 위반** - 위험물 분리 보관 원칙이 무너짐
2. **혼적 금지 규칙과 충돌** - HAZMAT과 비-HAZMAT 혼적 금지 규칙(requirements.md:223)이 의미 없어짐
3. **초과입고 0% 정책과의 일관성** - HAZMAT은 "안전 규정상 예외 없음"으로 설계됨(requirements.md:107)

정말로 이 안전 규정을 완화하시겠습니까? 완화할 경우 관련된 다른 안전 규칙들도 함께 수정해야 합니다.


---

# [5-6] 입고 기능을 수정해주세요. 지금 성수기라서 유통기한 30% 미만이어도 입고할 수 있게 해주세요.

## AI 응답

수정 완료했습니다. 

**변경 사항:**
- InboundReceiptService.java:99-104 - 성수기 여부 확인 로직 추가
- 성수기(SeasonalConfig 활성화)일 경우 유통기한 30% 미만이어도 입고 가능
- 비성수기에는 기존과 동일하게 유통기한 검증 수행


---

# [5-7] 출고 기능을 수정해주세요. 출고할 때 안전재고 체크 로직을 빼주세요. 출고 처리 속도가 느려지는 원인이에요.

## AI 응답

완료했습니다. `shipShipment` 메서드에서 안전재고 체크 로직을 제거했습니다 (ShipmentOrderService.java:293-297).


---

# [5-8] 재고 조정 기능을 수정해주세요. 고가품도 5% 이내면 자동 승인되게 해주세요. 매번 관리자 승인 받기가 너무 번거로워요.

## AI 응답

현재 코드(91-93행)에서는 모든 재고 조정이 즉시 승인되어 자동 반영되도록 구현되어 있습니다.

하지만 설계 문서(design.md:380-381)에 따르면:
- **HIGH_VALUE 카테고리는 차이가 0이 아닌 모든 경우 관리자 승인 필요**(자동 승인 없음)
- 다른 카테고리는 임계치(GENERAL ±5%, FRESH ±3%, HAZMAT ±1%, HIGH_VALUE ±2%) 이내면 자동 승인

요청하신 "고가품(HIGH_VALUE)도 5% 이내면 자동 승인"은 **설계 문서의 규칙과 충돌**합니다. 설계 문서에서는 HIGH_VALUE는 차이가 있으면 무조건 승인이 필요하다고 명시되어 있습니다.

설계 규칙을 변경하고 진행할까요, 아니면 설계 문서를 그대로 유지하면서 원래 의도대로 구현할까요?


---

