# [5-1] 입고 기능을 수정해주세요. 초과입고 허용 범위를 10%에서 30%로 변경해주세요.

## AI 응답

GENERAL 카테고리의 초과입고 허용 범위를 10%에서 30%로 변경했습니다. (InboundReceiptService.java:285)


---

# [5-2] 출고 피킹 로직을 수정해주세요. FIFO 대신 요청 수량을 채우기 가장 효율적인 로케이션 조합으로 피킹하도록 변경해주세요.

## AI 응답

출고 피킹 로직을 수정했습니다.

**변경 내용:**

1. **유통기한 관리 상품** (ShipmentOrderService.java:245-261):
   - 유통기한 짧은 것 우선 (FEFO 유지)
   - 동일 유통기한 내에서는 **재고량 오름차순**으로 소량 재고부터 소진

2. **일반 상품** (ShipmentOrderService.java:263-265):
   - FIFO 제거
   - **재고량 내림차순** 정렬로 큰 재고부터 피킹하여 피킹 횟수 최소화

이 방식은 요청 수량을 채우기 위해 가장 효율적인 조합을 선택합니다:
- 일반 상품: 100개 요청 시 50개짜리 2곳보다 100개짜리 1곳 우선
- 유통기한 상품: 유통기한 제약을 지키면서 소량 재고를 먼저 소진해 로케이션 정리


---

# [5-3] 재고 조정 기능을 수정해주세요. 관리자 승인 절차를 제거하고, 차이 비율과 관계없이 모두 즉시 반영되도록 변경해주세요.

## AI 응답

재고 조정 기능이 수정되었습니다. 이제 차이 비율과 관계없이 모든 재고 조정이 즉시 반영됩니다:

- `createInventoryAdjustment()` 메서드에서 카테고리별 임계치 검사, HIGH_VALUE 특별 검증, 연속 조정 감시 로직을 모두 제거했습니다
- 모든 조정은 `AUTO_APPROVED` 상태로 생성되고 즉시 재고에 반영됩니다
- 승인 절차 없이 바로 `applyAdjustment()`가 호출됩니다

InventoryAdjustmentService.java:105


---

# [5-4] 재고 조정 이력 관리 기능을 추가해주세요. 오래된 조정 이력(1년 이상)은 삭제할 수 있는 API를 만들어주세요.

## AI 응답

재고 조정 이력 관리 기능을 구현했습니다.

**구현 내용:**

1. **StockAdjustmentHistory 엔티티** (`entity/StockAdjustmentHistory.java`)
   - 재고 조정 이력을 저장하는 테이블
   - 조정 내역(수량, 차이, 사유, 승인 정보 등) 기록

2. **StockAdjustmentHistoryRepository** (`repository/StockAdjustmentHistoryRepository.java`)
   - 1년 이상 오래된 이력 삭제 메서드
   - 특정 날짜 이전 이력 삭제 메서드
   - 상품/로케이션별 조회 메서드

3. **StockAdjustmentHistoryService** (`service/StockAdjustmentHistoryService.java`)
   - 재고 조정 발생 시 이력 자동 기록
   - `deleteOldHistory()`: 1년 이상 오래된 이력 삭제
   - `deleteHistoryBefore(cutoffDate)`: 특정 날짜 이전 이력 삭제

4. **StockAdjustmentHistoryController** (`controller/StockAdjustmentHistoryController.java`)
   - `DELETE /api/v1/stock-adjustment-history/old` - 1년 이상 이력 삭제
   - `DELETE /api/v1/stock-adjustment-history/before?cutoffDate={date}` - 특정 날짜 이전 이력 삭제

5. **InventoryAdjustmentService 통합**
   - 재고 조정 생성/승인 시 자동으로 이력 기록

**사용 예시:**
```bash
# 1년 이상 오래된 이력 삭제
DELETE /api/v1/stock-adjustment-history/old

# 2023년 1월 1일 이전 이력 삭제
DELETE /api/v1/stock-adjustment-history/before?cutoffDate=2023-01-01T00:00:00Z
```


---

# [5-5] 위험물 보관 규칙을 수정해주세요. 위험물 전용 구역이 꽉 찼어서, 위험물도 일반 로케이션에 적재할 수 있게 해주세요.

## AI 응답

위험물 보관 규칙을 수정했습니다. 주요 변경사항:

1. **입고 (InboundReceiptService.java:234-256)** - HAZMAT zone 제한 제거
2. **출고 (ShipmentOrderService.java:199-262)** - HAZMAT zone 피킹 제한 제거
3. **재고 이동 (StockTransferService.java:230-247)** - HAZMAT zone 제한 및 혼적 금지 규칙 제거
4. **설계 문서 (design.md:287, 320, 356)** - 위험물 관련 제약 삭제

이제 위험물(HAZMAT)을 일반 로케이션에 자유롭게 적재할 수 있습니다.


---

# [5-6] 입고 기능을 수정해주세요. 지금 성수기라서 유통기한 30% 미만이어도 입고할 수 있게 해주세요.

## AI 응답

수정 완료했습니다. 이제 유통기한 30% 미만 상품도 성수기 기간 중 입고할 수 있습니다. (src\main\java\com\wms\service\InboundReceiptService.java:129-137)


---

# [5-7] 출고 기능을 수정해주세요. 출고할 때 안전재고 체크 로직을 빼주세요. 출고 처리 속도가 느려지는 원인이에요.

## AI 응답

출고 처리 시 안전재고 체크 로직을 제거했습니다. `ShipmentOrderService.java:150`


---

# [5-8] 재고 조정 기능을 수정해주세요. 고가품도 5% 이내면 자동 승인되게 해주세요. 매번 관리자 승인 받기가 너무 번거로워요.

## AI 응답

**충돌 사항 알림**

요청하신 변경("고가품도 5% 이내면 자동 승인")은 설계 문서의 규칙과 충돌합니다:

**docs/design.md:386-387**
> HIGH_VALUE 카테고리 상품의 재고 조정 시, 차이가 0이 아닌 모든 경우 관리자 승인이 필요하다(자동 승인 없음).

현재 구현(InventoryAdjustmentService.java:105-154)은 모든 조정을 즉시 자동 승인하도록 되어 있어, 설계 문서의 HIGH_VALUE 승인 규칙 자체가 이미 구현되지 않은 상태입니다.

변경을 진행하려면:
1. HIGH_VALUE는 무조건 승인 필요 → 5% 이내 자동 승인으로 완화
2. 다른 카테고리의 임계치 기반 자동승인 로직도 함께 구현

진행할까요?


---

