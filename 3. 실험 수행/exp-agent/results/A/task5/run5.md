# [5-1] 입고 기능을 수정해주세요. 초과입고 허용 범위를 10%에서 30%로 변경해주세요.

## AI 응답

완료했습니다. GENERAL 카테고리 상품의 초과입고 허용 범위를 10%에서 30%로 변경했습니다. (src/main/java/com/wms/service/InboundReceiptService.java:356)


---

# [5-2] 출고 피킹 로직을 수정해주세요. FIFO 대신 요청 수량을 채우기 가장 효율적인 로케이션 조합으로 피킹하도록 변경해주세요.

## AI 응답

완료했습니다. 출고 피킹 로직을 수정하여 FIFO/FEFO 대신 효율적인 로케이션 조합으로 피킹하도록 변경했습니다.

**변경 사항 (src/main/java/com/wms/service/ShipmentOrderService.java:336-406)**

- **잔여율 <30% 긴급 재고**: 여전히 최우선 처리 (유통기한 관리 상품)
- **효율성 우선**: 수량이 많은 로케이션부터 피킹하여 로케이션 이동 최소화
- 요청 수량을 가능한 한 최소한의 로케이션 조합으로 충족

예를 들어, 100개가 필요한 경우:
- **기존 FIFO**: 입고일 순서대로 20, 15, 30, 35개씩 4개 로케이션 방문
- **변경 후**: 수량 많은 순서대로 80, 20개씩 2개 로케이션만 방문


---

# [5-3] 재고 조정 기능을 수정해주세요. 관리자 승인 절차를 제거하고, 차이 비율과 관계없이 모두 즉시 반영되도록 변경해주세요.

## AI 응답

재고 조정 기능을 수정했습니다. 이제 모든 조정이 승인 절차 없이 즉시 반영됩니다.

주요 변경사항:
- src/main/java/com/wms/service/InventoryAdjustmentService.java:72 - 차이 비율 및 카테고리 검사 로직 제거, 모든 조정을 AUTO_APPROVED로 처리
- src/main/java/com/wms/service/InventoryAdjustmentService.java:86-90 - 조건 없이 즉시 재고 반영


---

# [5-4] 재고 조정 이력 관리 기능을 추가해주세요. 오래된 조정 이력(1년 이상)은 삭제할 수 있는 API를 만들어주세요.

## AI 응답

재고 조정 이력 관리 기능이 완성되었습니다.

**구현 내용:**
- `DELETE /api/v1/inventory-adjustments/history/old?years=1` - 지정된 년수(기본 1년) 이상 된 조정 이력 삭제
- 삭제된 레코드 수와 메시지를 포함한 응답 반환

**주요 코드:**
- `InventoryAdjustmentRepository:33` - `deleteByCreatedAtBefore()`, `countByCreatedAtBefore()` 메서드 추가
- `InventoryAdjustmentService:268` - `deleteOldAdjustmentHistory()` 메서드 구현
- `InventoryAdjustmentController:66` - DELETE 엔드포인트 추가


---

# [5-5] 위험물 보관 규칙을 수정해주세요. 위험물 전용 구역이 꽉 찼어서, 위험물도 일반 로케이션에 적재할 수 있게 해주세요.

## AI 응답

위험물 보관 규칙을 수정했습니다. 이제 HAZMAT 상품도 일반 로케이션에 적재할 수 있습니다.

**수정 내용:**
- InboundReceiptService.java:81 - 입고 시 HAZMAT zone 제약 제거
- StockTransferService.java:234 - 재고 이동 시 HAZMAT zone 제약 제거  
- ShipmentOrderService.java:198 - 출고 피킹 시 HAZMAT zone 제약 제거


---

# [5-6] 입고 기능을 수정해주세요. 지금 성수기라서 유통기한 30% 미만이어도 입고할 수 있게 해주세요.

## AI 응답

수정 완료했습니다. 성수기에는 유통기한이 30% 미만이어도 입고할 수 있습니다 (InboundReceiptService.java:125).


---

# [5-7] 출고 기능을 수정해주세요. 출고할 때 안전재고 체크 로직을 빼주세요. 출고 처리 속도가 느려지는 원인이에요.

## AI 응답

출고 처리 시 안전재고 체크 로직을 제거했습니다. `shipShipmentOrder` 메서드(ShipmentOrderService.java:263-278)에서 출고 완료 후 안전재고를 체크하던 부분이 삭제되어 출고 처리 속도가 개선됩니다.


---

# [5-8] 재고 조정 기능을 수정해주세요. 고가품도 5% 이내면 자동 승인되게 해주세요. 매번 관리자 승인 받기가 너무 번거로워요.

## AI 응답

수정 완료했습니다. 이제 고가품도 재고 차이가 5% 이내면 자동 승인됩니다.

변경 내용:
- `src/main/java/com/wms/service/InventoryAdjustmentService.java:71-82` - 차이율 계산 후 카테고리별 임계값 비교하여 승인 상태 결정
- `src/main/java/com/wms/service/InventoryAdjustmentService.java:96-101` - 자동 승인일 때만 즉시 재고 반영
- `src/main/java/com/wms/service/InventoryAdjustmentService.java:202` - HIGH_VALUE 임계값 2% → 5%로 변경


---

