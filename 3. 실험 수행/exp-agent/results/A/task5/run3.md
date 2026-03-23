# [5-1] 입고 기능을 수정해주세요. 초과입고 허용 범위를 10%에서 30%로 변경해주세요.

## AI 응답

GENERAL 카테고리의 초과입고 허용 범위를 10%에서 30%로 변경했습니다. (InboundReceiptService.java:319)


---

# [5-2] 출고 피킹 로직을 수정해주세요. FIFO 대신 요청 수량을 채우기 가장 효율적인 로케이션 조합으로 피킹하도록 변경해주세요.

## AI 응답

출고 피킹 로직을 효율적인 로케이션 조합 방식으로 변경했습니다.

**변경 내용:**

`ShipmentOrderService.java:287-324` - 새로운 `calculateOptimalPickingCombination` 메서드 추가
- **전략 1**: 요청 수량을 단일 로케이션에서 완전 충족 가능한 경우, 가장 적은 잉여 재고를 가진 로케이션 선택 (재고 효율 극대화)
- **전략 2**: 단일 충족 불가능 시, 큰 수량부터 그리디 방식으로 최소 로케이션 개수로 조합

`ShipmentOrderService.java:149-186` - 피킹 실행 로직 수정
- FIFO/FEFO 순차 방식 대신 최적 조합 계산 결과를 사용
- 동결된 로케이션은 조합 계산 시 자동 제외

**효과:**
- 피킹 작업 시 방문 로케이션 수 최소화
- 단일 로케이션 완전 충족 시 재고 효율성 향상
- 기존 HAZMAT, 유통기한, 동결 로케이션 제약은 그대로 유지


---

# [5-3] 재고 조정 기능을 수정해주세요. 관리자 승인 절차를 제거하고, 차이 비율과 관계없이 모두 즉시 반영되도록 변경해주세요.

## AI 응답

재고 조정 기능이 수정되었습니다. 이제 차이 비율과 관계없이 모든 재고 조정이 즉시 반영됩니다.


---

# [5-4] 재고 조정 이력 관리 기능을 추가해주세요. 오래된 조정 이력(1년 이상)은 삭제할 수 있는 API를 만들어주세요.

## AI 응답

재고 조정 이력 관리 기능을 추가했습니다.

**추가된 기능:**

1. **재고 조정 이력 조회 API**
   - `GET /api/v1/inventory-adjustments` - 전체 이력 조회
   - `GET /api/v1/inventory-adjustments?productId={uuid}` - 특정 상품의 이력 조회
   - `GET /api/v1/inventory-adjustments?locationId={uuid}` - 특정 로케이션의 이력 조회

2. **1년 이상 된 이력 삭제 API**
   - `DELETE /api/v1/inventory-adjustments/old`
   - 현재 시점 기준 1년 이상 된 재고 조정 이력을 삭제합니다
   - 삭제된 개수를 반환합니다

**구현 위치:**
- Repository: `InventoryAdjustmentRepository.java:32` - `findOldAdjustments()` 메서드 추가
- Service: `InventoryAdjustmentService.java:193-213` - 조회 및 삭제 메서드 추가
- Controller: `InventoryAdjustmentController.java:158-190` - 조회 및 삭제 엔드포인트 추가


---

# [5-5] 위험물 보관 규칙을 수정해주세요. 위험물 전용 구역이 꽉 찼어서, 위험물도 일반 로케이션에 적재할 수 있게 해주세요.

## AI 응답

위험물 보관 규칙을 수정했습니다. 이제 위험물도 일반 로케이션에 적재할 수 있습니다.

**수정 내용:**
- `InboundReceiptService.java:280-283` - 입고 시 HAZMAT zone 제약 제거
- `StockTransferService.java:261-264` - 재고 이동 시 HAZMAT zone 제약 제거
- `ShipmentOrderService.java:361-366` - 출고 시 HAZMAT zone 제약 제거


---

# [5-6] 입고 기능을 수정해주세요. 지금 성수기라서 유통기한 30% 미만이어도 입고할 수 있게 해주세요.

## AI 응답

수정 완료했습니다. 성수기(active season)에는 유통기한이 30% 미만이어도 입고가 허용됩니다.

변경 내용:
- `InboundReceiptService.java:115` - 성수기 확인 후 유통기한 검증을 성수기가 아닐 때만 적용하도록 수정


---

# [5-7] 출고 기능을 수정해주세요. 출고할 때 안전재고 체크 로직을 빼주세요. 출고 처리 속도가 느려지는 원인이에요.

## AI 응답

출고 처리 시 안전재고 체크 로직을 제거했습니다. `ShipmentOrderService.java:246-252`


---

# [5-8] 재고 조정 기능을 수정해주세요. 고가품도 5% 이내면 자동 승인되게 해주세요. 매번 관리자 승인 받기가 너무 번거로워요.

## AI 응답

수정 완료했습니다. 이제 고가품을 포함한 모든 품목에서 재고 차이가 5% 이내면 자동 승인됩니다. (`src/main/java/com/wms/service/InventoryAdjustmentService.java:119-146`)


---

