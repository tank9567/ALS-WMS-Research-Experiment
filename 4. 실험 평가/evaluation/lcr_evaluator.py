#!/usr/bin/env python3
"""
LCR (Logic Compliance Rate) 코드 분석 기반 평가기 — Level 2

AI 생성 코드를 정적 분석하여 98개 비즈니스 로직 준수 항목을 채점한다.
Level 1 (48항목) + Level 2 확장 (50항목) = 총 98항목

한계: 정적 텍스트 분석이므로 false positive/negative 가능. 최종 결과는 수동 검증 권장.
"""

import os
import re
import csv
import sys
from pathlib import Path

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
RESULTS_DIR = os.path.join(os.path.dirname(os.path.dirname(BASE_DIR)), "3. 실험 수행", "exp-agent", "results")


def read_result(group, task, run):
    """결과 파일 읽기"""
    path = os.path.join(RESULTS_DIR, group, f"task{task}", f"run{run}.md")
    if not os.path.exists(path):
        return ""
    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
        return f.read()


def ci(pattern, text):
    """Case-insensitive regex search, returns bool"""
    return bool(re.search(pattern, text, re.IGNORECASE))


def ci_all(patterns, text):
    """모든 패턴이 매칭되는지"""
    return all(ci(p, text) for p in patterns)


def ci_any(patterns, text):
    """하나라도 매칭되는지"""
    return any(ci(p, text) for p in patterns)


def count_matches(pattern, text):
    return len(re.findall(pattern, text, re.IGNORECASE))


# ============================================================
# Task 1: 입고 처리 — 기본 규칙 (Level 1, 12항목)
# ============================================================

def eval_1_1(code):
    """PO 연결 필수 — po_id 검증 로직"""
    return ci_any([
        r'po_id.*not\s*found', r'purchase.?order.*not\s*found',
        r'findById\(.*po', r'po_id.*required',
        r'발주.*존재.*확인', r'purchase.?order.*exist',
        r'orElseThrow.*purchase', r'po_id.*null',
        r'없는.*발주', r'invalid.*po',
    ], code)


def eval_1_2(code):
    """2단계 프로세스 — inspecting/confirmed 상태 분리"""
    has_inspecting = ci(r'inspecting', code)
    has_confirm = ci_any([
        r'/confirm', r'confirm.*receipt',
        r'status.*=.*confirmed', r'confirm.*inbound',
    ], code)
    return has_inspecting and has_confirm


def eval_1_3(code):
    """초과입고 제한 로직 존재"""
    return ci_any([
        r'1\.1\s*\*|1\.10\s*\*|\*\s*1\.1(?:0)?',
        r'110\s*%|10\s*%.*초과|10\s*%.*over',
        r'over.?receiv.*10', r'ordered.?qty\s*\*\s*1\.1',
        r'OVER_RECEIPT.*0\.1', r'tolerance.*0\.1|tolerance.*10',
        r'초과.*입고.*제한.*10|10.*초과.*입고',
        r'maxAllowed.*1\.1|max.*110',
        r'received.?qty.*>.*ordered.?qty.*\*',
        # Level 2: 카테고리별 초과율이 있어도 통과
        r'over.?receipt.?rate|overReceiptRate|허용률',
        r'category.*tolerance|tolerance.*category',
    ], code)


def eval_1_4(code):
    """초과입고 거부 응답 — 409 또는 에러"""
    has_limit = eval_1_3(code)
    has_error = ci_any([
        r'409', r'CONFLICT',
        r'throw.*exception.*초과|throw.*exception.*over.*receiv|throw.*exception.*exceed',
        r'거부|reject|refuse', r'HttpStatus\.\w+.*409',
    ], code)
    return has_limit and has_error


def eval_1_5(code):
    """재고 반영 시점 — confirmed 시에만 inventory 증가"""
    return ci_any([
        r'confirmed.*inventory|inventory.*confirmed',
        r'status.*==.*confirmed.*then.*inventory',
        r'if.*confirmed.*save.*inventory',
        r'확정.*재고.*증가|재고.*확정.*시',
        r'confirm.*\{[^}]*inventory', r'confirm.*\{[^}]*quantity',
        r'when.*confirmed.*add.*stock',
    ], code) or (
        ci(r'confirmed', code) and ci(r'inventory', code) and
        ci_any([r'save.*inventory', r'inventory\.set', r'quantity.*\+', r'addQuantity'], code)
    )


def eval_1_6(code):
    """유통기한 필수 체크 — has_expiry + expiry_date null 거부"""
    return ci_any([
        r'has.?expiry.*true.*expiry.?date.*null',
        r'has.?expiry.*expiry.?date.*required',
        r'유통기한.*필수|유통기한.*관리.*대상.*입력',
        r'if.*has.?expiry.*expiry.?date\s*==\s*null',
        r'expiry.?date.*required.*has.?expiry',
        r'has.?expiry.*&&.*expiry.?date', r'expiry.*mandatory.*has.?expiry',
    ], code)


def eval_1_7(code):
    """유통기한 비관리 — has_expiry=false일 때 null 허용"""
    return eval_1_6(code) or ci_any([
        r'has.?expiry.*false.*null.*ok',
        r'!has.?expiry', r'has.?expiry.*false',
    ], code)


def eval_1_8(code):
    """received_qty 누적 갱신"""
    return ci_any([
        r'received.?qty\s*\+\s*=|received.?qty.*\+=',
        r'received.?qty.*\+.*quantity',
        r'setReceived.?Qty.*get.*\+',
        r'누적.*갱신|누적.*합산',
        r'received.?qty.*accumul',
        r'addReceived|incrementReceived',
        r'received.?qty\s*=\s*received.?qty\s*\+',
    ], code)


def eval_1_9(code):
    """PO 상태 completed"""
    return ci_any([
        r'status.*completed|completed.*status',
        r'setStatus.*completed|status.*=.*completed',
        r'모든.*라인.*입고.*완료|전체.*입고.*완료',
        r'all.*received.*completed',
        r'checkPoCompletion|updatePoStatus',
    ], code) and ci(r'completed', code)


def eval_1_10(code):
    """PO 상태 partial"""
    return ci_any([
        r'status.*partial|partial.*status',
        r'부분.*입고|partial.*receiv',
        r'setStatus.*partial|status.*=.*partial',
    ], code) and ci(r'partial', code)


def eval_1_11(code):
    """location.current_qty 갱신"""
    return ci_any([
        r'current.?qty\s*\+\s*=|current.?qty.*\+=',
        r'setCurrentQty.*\+|setCurrent.?Qty.*get.*\+',
        r'location.*current.?qty.*update|update.*location.*current.?qty',
        r'current.?qty\s*=\s*current.?qty\s*\+',
        r'addCurrentQty|incrementCurrentQty', r'location.*quantity.*\+',
    ], code)


def eval_1_12(code):
    """입고 거부 시 무변동"""
    return ci_any([
        r'rejected.*no.*change|reject.*inventory.*unchanged',
        r'거부.*재고.*변동.*없|reject.*무변동',
        r'if.*rejected.*return', r'status.*rejected',
    ], code) or (ci(r'rejected?', code) and not ci(r'rejected.*inventory.*add|rejected.*quantity.*\+', code))


# ============================================================
# Task 1: 입고 처리 — 확장 규칙 (Level 2, 13항목)
# ============================================================

def eval_1_13(code):
    """카테고리별 초과율 차등 — GENERAL=10%, FRESH=5%, HAZMAT=0%, HIGH_VALUE=3%"""
    has_category_diff = ci_any([
        r'GENERAL.*10|GENERAL.*0\.1',
        r'FRESH.*5|FRESH.*0\.05',
        r'HAZMAT.*0[^.]|HAZMAT.*0\.0\b',
        r'HIGH.?VALUE.*3|HIGH.?VALUE.*0\.03',
        r'category.*switch|switch.*category',
        r'getOverReceiptRate|get.*tolerance.*category',
        r'카테고리.*초과.*허용|초과.*허용.*카테고리',
    ], code)
    return has_category_diff


def eval_1_14(code):
    """HAZMAT 0% 예외 없음"""
    return ci_any([
        r'HAZMAT.*0\s*%|HAZMAT.*zero.*tolerance',
        r'HAZMAT.*항상.*0|HAZMAT.*always.*0',
        r'if.*HAZMAT.*return\s*0|HAZMAT.*->.*0',
        r'HAZMAT.*예외.*없|HAZMAT.*no.*exception',
    ], code) or (ci(r'HAZMAT', code) and ci(r'0[^.]', code) and eval_1_13(code))


def eval_1_15(code):
    """PO유형 가중치 — NORMAL=×1, URGENT=×2, IMPORT=×1.5"""
    return ci_any([
        r'URGENT.*2|URGENT.*\*\s*2',
        r'IMPORT.*1\.5|IMPORT.*\*\s*1\.5',
        r'po.?type.*weight|po.?type.*multiplier',
        r'발주.*유형.*가중치|가중치.*발주',
        r'getPoTypeMultiplier|getWeight.*poType',
        r'NORMAL.*1[^.].*URGENT.*2.*IMPORT.*1\.5',
    ], code)


def eval_1_16(code):
    """성수기 가중치 — seasonal_config 조회"""
    return ci_any([
        r'seasonal.?config', r'season.*multiplier',
        r'성수기.*가중치|가중치.*성수기',
        r'isSeason|is.?peak|isHighSeason',
        r'seasonal.*start.?date.*end.?date',
        r'season.*active',
    ], code)


def eval_1_17(code):
    """manufacture_date 필수"""
    return ci_any([
        r'manufacture.?date.*required|manufacture.?date.*null.*reject',
        r'manufacture.?date.*필수',
        r'if.*has.?expiry.*manufacture.?date.*null',
        r'manufacture.?date.?required.*true',
        r'제조일.*필수|제조일.*입력',
    ], code)


def eval_1_18(code):
    """잔여율 < 30% 거부"""
    return ci_any([
        r'remaining.*shelf.*life.*<.*30|remaining.*shelf.*life.*<.*0\.3',
        r'shelf.?life.?pct.*<|잔여.*유통기한.*부족',
        r'min.?remaining.?shelf.?life.?pct',
        r'잔여율.*30.*거부|30.*잔여율.*거부',
        r'shelf.?life.*percent|shelf.?life.*ratio',
        r'remaining.?pct.*<.*min',
    ], code)


def eval_1_19(code):
    """잔여율 30~50% 승인 필요"""
    return ci_any([
        r'pending.?approval.*shelf|shelf.*pending.?approval',
        r'30.*50.*승인|잔여율.*승인.*대기',
        r'shelf.?life.*pending|pending.*shelf.?life',
        r'30.*%.*50.*%.*approval|approval.*30.*50',
        r'SHELF_LIFE_WARNING',
    ], code)


def eval_1_20(code):
    """보관유형 호환 체크"""
    return ci_any([
        r'storage.?type.*compatible|compatible.*storage.?type',
        r'storage.?type.*!=|storage.?type.*match',
        r'보관.*유형.*호환|호환.*보관.*유형',
        r'AMBIENT.*COLD.*FROZEN',
        r'checkStorageCompatibility|isStorageCompatible',
        r'product.*storage.?type.*location.*storage.?type',
    ], code)


def eval_1_21(code):
    """HAZMAT zone 체크"""
    return ci_any([
        r'HAZMAT.*zone|zone.*HAZMAT',
        r'hazmat.*location|location.*hazmat',
        r'위험물.*구역|구역.*위험물',
        r'zone.*!=.*HAZMAT.*reject|category.*HAZMAT.*zone.*HAZMAT',
        r'isHazmatZone|checkHazmatZone',
    ], code)


def eval_1_22(code):
    """실사 동결 로케이션 거부"""
    return ci_any([
        r'is.?frozen.*true.*reject|is.?frozen.*거부',
        r'frozen.*location.*reject|동결.*로케이션.*거부',
        r'if.*is.?frozen.*throw|if.*frozen.*error',
        r'location.*frozen.*not.*available',
    ], code)


def eval_1_23(code):
    """페널티 기록 (OVER_DELIVERY)"""
    return ci_any([
        r'OVER.?DELIVERY',
        r'supplier.?penalt.*over|over.*supplier.?penalt',
        r'penalty.?type.*OVER',
        r'초과입고.*페널티|페널티.*초과입고',
        r'savePenalty.*over|recordPenalty.*over',
    ], code)


def eval_1_24(code):
    """페널티 기록 (SHORT_SHELF_LIFE)"""
    return ci_any([
        r'SHORT.?SHELF.?LIFE',
        r'supplier.?penalt.*shelf|shelf.*supplier.?penalt',
        r'penalty.?type.*SHORT.?SHELF',
        r'유통기한.*페널티|페널티.*유통기한',
    ], code)


def eval_1_25(code):
    """페널티 3회 → PO hold"""
    return ci_any([
        r'penalty.*>=?\s*3|penalt.*count.*3',
        r'3.*회.*이상.*hold|hold.*3.*회',
        r'penalty.*threshold.*3',
        r'status.*hold|hold.*status',
        r'페널티.*3.*hold|hold.*페널티',
        r'countPenalties.*>=\s*3',
    ], code) and ci(r'hold', code)


# ============================================================
# Task 2: 출고 처리 — 기본 규칙 (Level 1, 14항목)
# ============================================================

def eval_2_1(code):
    """FIFO 적용"""
    return ci_any([
        r'FIFO', r'received.?at\s+ASC|received.?at.*asc',
        r'ORDER\s+BY\s+received.?at', r'orderBy.*received.?At.*asc',
        r'sort.*received.?at|received.?at.*sort',
        r'입고일.*순|입고.*순서', r'first.?in.?first.?out',
    ], code)


def eval_2_2(code):
    """FEFO 우선 적용"""
    return ci_any([
        r'FEFO', r'expiry.?date\s+ASC|expiry.?date.*asc',
        r'ORDER\s+BY\s+expiry.?date', r'orderBy.*expiry.?Date.*asc',
        r'sort.*expiry.?date|expiry.?date.*sort',
        r'유통기한.*순|유통기한.*우선', r'first.?expir',
    ], code)


def eval_2_3(code):
    """FEFO 동률 시 FIFO"""
    return ci_any([
        r'expiry.?date.*received.?at',
        r'FEFO.*FIFO|유통기한.*입고일',
        r'ORDER\s+BY\s+expiry.?date.*received.?at',
        r'expiry.?date\s+ASC.*received.?at\s+ASC',
        r'thenComparing.*received.?at|then.*received',
    ], code) or (eval_2_2(code) and eval_2_1(code))


def eval_2_4(code):
    """분할 피킹"""
    return ci_any([
        r'pick.?detail|pickDetail|pick_detail',
        r'for.*inventory|while.*inventory.*remaining',
        r'여러.*로케이션.*피킹|분할.*피킹',
        r'multi.*location.*pick',
        r'remaining.*qty|remaining.*quantity',
    ], code)


def eval_2_5(code):
    """만료 재고 제외"""
    return ci_any([
        r'expiry.?date.*<.*today|expiry.?date.*before.*now',
        r'expiry.?date.*>.*today|expiry.?date.*after.*now',
        r'isExpired|is_expired',
        r'만료.*제외|유통기한.*지난.*제외',
        r'expired.*exclude|exclude.*expired',
        r'expiry.?date.*is.*not.*null.*and.*expiry.?date.*>',
        r'where.*expiry.?date.*>|where.*expiry.?date.*>=',
        r'filter.*expir|expir.*filter',
    ], code)


def eval_2_6(code):
    """부분출고"""
    return ci_any([
        r'partial.*ship|부분.*출고',
        r'picked.?qty\s*<\s*requested.?qty',
        r'shortage|부족', r'available.*<.*requested',
    ], code)


def eval_2_7(code):
    """백오더 자동 생성"""
    return ci_any([
        r'backorder|back.?order|백오더',
        r'shortage.?qty|shortage_qty',
        r'create.*backorder|save.*backorder',
        r'BackOrder|BACKORDER',
    ], code)


def eval_2_8(code):
    """picked_qty 기록"""
    return ci_any([
        r'picked.?qty|pickedQty|picked_qty',
        r'setPickedQty|setPicked.?Qty', r'picked.*quantity',
    ], code)


def eval_2_9(code):
    """부분출고 상태 partial"""
    return ci_any([
        r'status.*partial.*pick|partial.*status.*line',
        r'setStatus.*partial', r'line.*status.*partial', r'PARTIAL',
    ], code) and ci(r'partial', code)


def eval_2_10(code):
    """전량 백오더"""
    return ci_any([
        r'backordered|BACKORDERED', r'status.*backordered',
        r'setStatus.*backordered', r'전량.*백오더|0.*가용.*백오더',
    ], code)


def eval_2_11(code):
    """inventory.quantity 차감"""
    return ci_any([
        r'quantity\s*-\s*=|quantity.*-=',
        r'setQuantity.*-|setQuantity.*get.*-',
        r'quantity\s*=\s*quantity\s*-',
        r'inventory.*subtract|subtract.*inventory',
        r'deduct|decrement.*quantity', r'reduceQuantity|reduce_quantity',
    ], code) or (
        ci(r'inventory', code) and ci_any([r'quantity.*-', r'subtract', r'deduct', r'차감'], code)
    )


def eval_2_12(code):
    """location.current_qty 차감 (출고)"""
    return ci_any([
        r'current.?qty\s*-\s*=|current.?qty.*-=',
        r'setCurrent.?Qty.*-|setCurrentQty.*get.*-',
        r'location.*current.?qty.*-|current.?qty.*location.*-',
        r'current.?qty\s*=\s*current.?qty\s*-',
    ], code) or (
        ci(r'location', code) and ci(r'current.?qty', code) and ci_any([r'-=', r'subtract', r'차감'], code)
    )


def eval_2_13(code):
    """피킹 상세 반환"""
    return ci_any([
        r'pick.?detail|pickDetail|pick_detail',
        r'location.?id.*quantity.*pick',
        r'피킹.*상세|피킹.*결과.*반환', r'PickDetail|PICK_DETAIL',
    ], code)


def eval_2_14(code):
    """shipped 상태"""
    return ci_any([
        r'status.*shipped|shipped.*status',
        r'setStatus.*shipped', r'SHIPPED', r'출고.*완료',
    ], code)


# ============================================================
# Task 2: 출고 처리 — 확장 규칙 (Level 2, 14항목)
# ============================================================

def eval_2_15(code):
    """is_expired 재고 제외"""
    return ci_any([
        r'is.?expired\s*=\s*false|is.?expired.*!=.*true',
        r'where.*is.?expired.*false|filter.*is.?expired.*false',
        r'is.?expired.*==\s*false',
        r'!.*is.?expired|not.*expired',
    ], code)


def eval_2_16(code):
    """실사 동결 로케이션 제외"""
    return ci_any([
        r'is.?frozen\s*=\s*false|is.?frozen.*!=.*true',
        r'frozen.*location.*exclude|동결.*로케이션.*제외',
        r'where.*is.?frozen.*false|filter.*frozen.*false',
        r'!.*is.?frozen|not.*frozen',
    ], code)


def eval_2_17(code):
    """잔여율 < 30% 최우선 출고"""
    return ci_any([
        r'remaining.*shelf.*30|30.*shelf.*life.*prior',
        r'잔여율.*30.*우선|30.*잔여율.*우선',
        r'nearExpiry.*priority|expiry.*priority.*30',
        r'shelf.?life.*<.*30.*first',
    ], code)


def eval_2_18(code):
    """잔여율 < 10% 출고 불가"""
    return ci_any([
        r'remaining.*shelf.*10.*expired|10.*shelf.*expired',
        r'잔여율.*10.*만료|10.*잔여율.*is.?expired',
        r'shelf.?life.*<.*10.*is.?expired.*true',
        r'mark.*expired.*shelf.*10|set.*expired.*10',
    ], code)


def eval_2_19(code):
    """HAZMAT zone 전용 피킹"""
    return ci_any([
        r'HAZMAT.*zone.*pick|pick.*HAZMAT.*zone',
        r'위험물.*피킹.*전용|전용.*위험물.*피킹',
        r'category.*HAZMAT.*location.*zone.*HAZMAT',
        r'hazmat.*only.*hazmat.*zone',
    ], code) or (ci(r'HAZMAT', code) and ci(r'zone', code) and ci(r'pick', code))


def eval_2_20(code):
    """max_pick_qty 제한"""
    return ci_any([
        r'max.?pick.?qty', r'maxPickQty',
        r'최대.*피킹.*수량|피킹.*수량.*제한',
        r'pick.*qty.*>.*max|pick.*exceed.*max',
        r'max.?pick.*limit',
    ], code)


def eval_2_21(code):
    """HAZMAT+FRESH 분리 출고"""
    return ci_any([
        r'HAZMAT.*FRESH.*분리|분리.*HAZMAT.*FRESH',
        r'separate.*shipment.*HAZMAT.*FRESH|split.*shipment',
        r'hazmat.*fresh.*conflict|cannot.*mix.*hazmat.*fresh',
        r'분리.*출고|별도.*shipment',
    ], code)


def eval_2_22(code):
    """부분출고 70% 기준"""
    return ci_any([
        r'70\s*%|0\.7\b',
        r'>=?\s*0?\.?70?\s*\*|>=?\s*70\s*%',
        r'available.*>=.*70|가용.*70.*이상',
        r'partial.*70.*percent|70.*partial',
    ], code) and ci(r'partial|부분', code)


def eval_2_23(code):
    """긴급발주 트리거 (30~70%)"""
    return ci_any([
        r'URGENT.?REORDER',
        r'auto.?reorder.?log.*URGENT|URGENT.*auto.?reorder',
        r'30.*70.*긴급|긴급.*발주.*트리거',
        r'trigger.?type.*URGENT.?REORDER',
        r'urgent.*reorder.*30.*70|30.*70.*urgent.*reorder',
    ], code)


def eval_2_24(code):
    """전량 백오더 (< 30%)"""
    return ci_any([
        r'<\s*30\s*%.*backorder|<\s*0\.3.*backorder',
        r'30.*미만.*전량.*백오더|전량.*백오더.*30',
        r'available.*<.*30.*full.*backorder',
        r'가용.*30.*미만.*백오더',
    ], code) or (ci(r'30', code) and ci(r'backorder|백오더', code) and ci(r'full|전량|all', code))


def eval_2_25(code):
    """안전재고 체크"""
    return ci_any([
        r'safety.?stock|안전.?재고',
        r'min.?qty.*safety|safety.*min.?qty',
        r'safetyStockRule|safety_stock_rule',
        r'check.*safety.*stock|safety.*stock.*check',
    ], code)


def eval_2_26(code):
    """안전재고 트리거 기록"""
    return ci_any([
        r'SAFETY.?STOCK.?TRIGGER',
        r'auto.?reorder.?log.*SAFETY|SAFETY.*auto.?reorder',
        r'trigger.?type.*SAFETY.?STOCK',
        r'안전재고.*트리거|트리거.*안전재고',
    ], code)


def eval_2_27(code):
    """보관유형 불일치 경고"""
    return ci_any([
        r'storage.?type.*mismatch|mismatch.*storage.?type',
        r'보관.*유형.*불일치|불일치.*보관.*유형',
        r'audit.?log.*storage.*type|storage.*type.*audit.?log',
        r'STORAGE.?TYPE.?MISMATCH',
        r'warn.*storage.*incompatible',
    ], code)


def eval_2_28(code):
    """피킹 응답에 backorder 포함"""
    return ci_any([
        r'response.*backorder|backorder.*response',
        r'return.*backorder|result.*backorder',
        r'응답.*백오더|백오더.*응답',
        r'PickResult.*backorder|PickResponse.*backorder',
    ], code) or (eval_2_7(code) and ci_any([r'response|result|return|응답'], code))


# ============================================================
# Task 3: 재고 이동 — 기본 규칙 (Level 1, 10항목)
# ============================================================

def eval_3_1(code):
    """단일 트랜잭션"""
    return ci_any([
        r'@Transactional', r'transaction',
        r'BEGIN.*COMMIT|START.*TRANSACTION', r'트랜잭션|원자적', r'atomic',
    ], code)


def eval_3_2(code):
    """롤백 처리"""
    return ci_any([
        r'rollback|ROLLBACK', r'@Transactional',
        r'try.*catch.*throw|catch.*rollback', r'롤백',
    ], code) or eval_3_1(code)


def eval_3_3(code):
    """동일 로케이션 거부"""
    return ci_any([
        r'from.*==.*to.*throw|from.*equals.*to.*throw',
        r'from.?location.*==.*to.?location',
        r'from.?location.*equals.*to.?location',
        r'same.*location.*error|동일.*로케이션.*거부',
        r'from_location_id\s*!=\s*to_location_id',
        r'from.?location.?id.*!=.*to.?location.?id',
        r'출발지.*도착지.*같|같은.*로케이션',
    ], code)


def eval_3_4(code):
    """출발지 재고 부족 체크"""
    return ci_any([
        r'quantity\s*<\s*.*transfer|transfer.*quantity.*>.*available',
        r'insufficient.*stock|stock.*insufficient',
        r'재고.*부족|부족.*재고', r'not.*enough.*quantity',
        r'source.*quantity.*<|from.*quantity.*<',
        r'inventory.*quantity.*<.*move|move.*>.*inventory',
    ], code)


def eval_3_5(code):
    """도착지 용량 체크"""
    return ci_any([
        r'capacity.*check|check.*capacity',
        r'current.?qty.*\+.*quantity.*>.*capacity',
        r'capacity.*exceed|exceed.*capacity',
        r'용량.*초과|적재.*초과', r'destination.*capacity|to.*capacity',
        r'capacity.*<.*current.?qty.*\+|remaining.*capacity',
    ], code)


def eval_3_6(code):
    """출발지 location.current_qty 차감"""
    return ci_any([
        r'from.*location.*current.?qty.*-|from.*current.?qty.*-',
        r'출발지.*current.?qty.*차감',
    ], code) or (eval_1_11(code) and ci(r'from|source|출발', code))


def eval_3_7(code):
    """도착지 location.current_qty 증가"""
    return ci_any([
        r'to.*location.*current.?qty.*\+|to.*current.?qty.*\+',
        r'도착지.*current.?qty.*증가',
    ], code) or (eval_1_11(code) and ci(r'to|dest|도착', code))


def eval_3_8(code):
    """기존 레코드 병합"""
    return ci_any([
        r'findBy.*product.*location.*lot|findBy.*ProductAndLocation',
        r'existing.*inventory|inventory.*exist',
        r'merge|upsert', r'기존.*레코드.*수량.*증가|병합',
        r'if.*exist.*add.*quantity|optional.*present',
        r'orElse.*new.*Inventory', r'ON\s+CONFLICT.*DO\s+UPDATE',
    ], code)


def eval_3_9(code):
    """received_at 보존"""
    return ci_any([
        r'received.?at.*보존|received.?at.*유지|received.?at.*preserve',
        r'set.*received.?at.*get.*received.?at|received.?at.*=.*source.*received.?at',
        r'original.*received.?at|keep.*received.?at',
        r'입고일.*유지|입고일.*보존',
    ], code) or (
        ci(r'received.?at', code) and ci_any([r'transfer', r'이동', r'move'], code) and
        not ci(r'received.?at.*now|received.?at.*new|received.?at.*current', code)
    )


def eval_3_10(code):
    """이력 기록 — stock_transfers"""
    return ci_any([
        r'stock.?transfer.*save|save.*stock.?transfer',
        r'StockTransfer', r'stock_transfers',
        r'transfer.*history|transfer.*record|이력.*기록',
        r'transferRepository\.save',
    ], code)


# ============================================================
# Task 3: 재고 이동 — 확장 규칙 (Level 2, 10항목)
# ============================================================

def eval_3_11(code):
    """실사 동결 체크 (출발지)"""
    return ci_any([
        r'from.*location.*is.?frozen|source.*frozen',
        r'출발지.*동결|동결.*출발지',
        r'is.?frozen.*from|from.*frozen.*reject',
    ], code) or (ci(r'is.?frozen|frozen', code) and ci(r'from|source|출발', code) and ci(r'reject|throw|error|거부', code))


def eval_3_12(code):
    """실사 동결 체크 (도착지)"""
    return ci_any([
        r'to.*location.*is.?frozen|dest.*frozen',
        r'도착지.*동결|동결.*도착지',
        r'is.?frozen.*to|to.*frozen.*reject',
    ], code) or (ci(r'is.?frozen|frozen', code) and ci(r'to|dest|도착', code) and ci(r'reject|throw|error|거부', code))


def eval_3_13(code):
    """보관유형 호환 체크"""
    return eval_1_20(code) or ci_any([
        r'storage.?type.*destination|destination.*storage.?type',
        r'to.*location.*storage.?type.*product.*storage.?type',
    ], code)


def eval_3_14(code):
    """HAZMAT zone 체크"""
    return ci_any([
        r'HAZMAT.*zone.*transfer|transfer.*HAZMAT.*zone',
        r'HAZMAT.*이동.*zone|zone.*이동.*HAZMAT',
        r'hazmat.*non.?hazmat.*reject',
    ], code) or (ci(r'HAZMAT', code) and ci(r'zone', code) and ci(r'transfer|이동|move', code))


def eval_3_15(code):
    """혼적 금지 (HAZMAT→비HAZMAT)"""
    return ci_any([
        r'혼적.*금지|금지.*혼적', r'mix.*HAZMAT|HAZMAT.*mix',
        r'co.?locate.*HAZMAT|HAZMAT.*co.?locate',
        r'segregat.*HAZMAT|HAZMAT.*segregat',
        r'hazmat.*non.?hazmat.*same.*location',
    ], code)


def eval_3_16(code):
    """혼적 금지 (비HAZMAT→HAZMAT)"""
    # 같은 혼적 금지 로직의 반대 방향
    return eval_3_15(code) or ci_any([
        r'non.?HAZMAT.*HAZMAT.*location.*reject',
        r'비위험물.*위험물.*혼적',
    ], code)


def eval_3_17(code):
    """유통기한 < 10% 이동 제한"""
    return ci_any([
        r'shelf.?life.*10.*SHIPPING|10.*shelf.?life.*SHIPPING',
        r'잔여율.*10.*SHIPPING|10.*잔여율.*SHIPPING',
        r'near.?expiry.*only.*shipping',
        r'remaining.*pct.*<.*10.*zone.*SHIPPING',
    ], code)


def eval_3_18(code):
    """유통기한 만료 이동 불가"""
    return ci_any([
        r'expired.*transfer.*reject|transfer.*expired.*reject',
        r'만료.*이동.*불가|이동.*불가.*만료',
        r'is.?expired.*true.*transfer.*reject',
        r'expiry.?date.*<.*today.*reject.*transfer',
    ], code)


def eval_3_19(code):
    """대량 이동 승인 (80%)"""
    return ci_any([
        r'80\s*%|0\.8\b',
        r'pending.?approval.*transfer|transfer.*pending.?approval',
        r'대량.*이동.*승인|80.*이동.*승인',
        r'transfer.?status.*pending.?approval',
        r'bulk.*transfer.*approval',
    ], code) and ci(r'approval|승인|pending', code)


def eval_3_20(code):
    """안전재고 체크"""
    return ci_any([
        r'safety.?stock.*transfer|transfer.*safety.?stock',
        r'안전.?재고.*이동|이동.*안전.?재고',
    ], code) or (eval_2_25(code) and ci(r'transfer|이동|move', code))


# ============================================================
# Task 4: 재고 실사 및 조정 — 기본 규칙 (Level 1, 12항목)
# ============================================================

def eval_4_1(code):
    """reason 필수"""
    return ci_any([
        r'reason.*null|reason.*empty|reason.*blank',
        r'reason.*required|reason.*필수',
        r'@NotBlank.*reason|@NotEmpty.*reason|@NotNull.*reason',
        r'사유.*필수|사유.*입력',
        r'if.*reason.*==.*null|if.*reason.*isEmpty|if.*reason.*isBlank',
    ], code)


def eval_4_2(code):
    """차이 비율 계산"""
    return ci_any([
        r'difference|차이',
        r'actual.?qty.*-.*system.?qty|system.?qty.*-.*actual.?qty',
        r'abs.*difference|Math\.abs',
        r'차이.*비율|차이.*계산', r'percent.*diff|diff.*percent',
    ], code)


def eval_4_3(code):
    """자동 승인 로직 존재"""
    return ci_any([
        r'auto.?approved|auto_approved|자동.*승인',
        r'5\s*%|0\.05', r'<=\s*5|<=\s*0\.05',
        r'threshold.*5|5.*threshold', r'AUTO_APPROVED',
    ], code)


def eval_4_4(code):
    """자동 승인 즉시 반영"""
    return ci_any([
        r'auto.?approved.*inventory|auto.?approved.*반영',
        r'자동.*승인.*즉시.*반영|자동.*승인.*재고',
    ], code) or (eval_4_3(code) and ci_any([
        r'inventory.*quantity.*=|setQuantity',
        r'apply.*adjust|reflect.*adjust', r'재고.*반영',
    ], code))


def eval_4_5(code):
    """>5% 승인 대기"""
    return ci_any([
        r'pending.*approval|approval.*pending',
        r'requires.?approval.*true|requires_approval.*true',
        r'승인.*대기|승인.*필요',
        r'>.*5\s*%.*pending|>.*0\.05.*pending', r'PENDING',
    ], code) and ci(r'pending', code)


def eval_4_6(code):
    """승인 전 미반영"""
    return eval_4_5(code)


def eval_4_7(code):
    """system_qty=0 승인 필요"""
    return ci_any([
        r'system.?qty.*==?\s*0.*approv|system.?qty.*==?\s*0.*pending',
        r'시스템.*0.*승인|0.*무조건.*승인',
        r'if.*system.?qty.*==\s*0', r'system.?qty.*zero.*approv',
    ], code)


def eval_4_8(code):
    """승인 후 반영"""
    return ci_any([
        r'approve.*inventory|approve.*반영|approved.*반영',
        r'approve.*\{[^}]*inventory|approve.*\{[^}]*quantity',
        r'승인.*재고.*반영',
        r'if.*approved.*apply|if.*approved.*reflect',
    ], code) or (
        ci(r'approved?', code) and ci_any([r'inventory.*set|setQuantity|apply|반영'], code)
    )


def eval_4_9(code):
    """거부 시 무변동"""
    return ci_any([
        r'rejected.*no.*change|reject.*무변동',
        r'거부.*재고.*변동.*없|rejected.*inventory.*unchanged',
    ], code) or (ci(r'rejected?', code) and eval_4_5(code))


def eval_4_10(code):
    """location.current_qty 갱신"""
    return ci_any([
        r'location.*current.?qty.*adjust|adjust.*location.*current.?qty',
        r'current.?qty.*조정|조정.*current.?qty',
    ], code) or (ci(r'current.?qty', code) and ci(r'adjust', code))


def eval_4_11(code):
    """DELETE 불가"""
    return ci_any([
        r'delete.*불가|삭제.*불가|삭제.*금지',
        r'no.*delete|cannot.*delete',
        r'DELETE.*not.*allow|not.*support.*delete',
        r'immutable.*adjust|audit.*trail', r'감사.*추적.*삭제.*불가',
    ], code) or (
        ci(r'adjustment', code) and not ci(r'@DeleteMapping|deleteById|DELETE FROM inventory_adjustment', code)
    )


def eval_4_12(code):
    """핵심 필드 UPDATE 불가"""
    return ci_any([
        r'immutable|불변|수정.*불가',
        r'reason.*수정.*불가|difference.*수정.*불가',
        r'@Column.*updatable.*=.*false',
        r'cannot.*update.*reason|cannot.*update.*difference',
        r'핵심.*필드.*수정.*불가',
    ], code)


# ============================================================
# Task 4: 재고 실사 및 조정 — 확장 규칙 (Level 2, 13항목)
# ============================================================

def eval_4_13(code):
    """GENERAL 임계치 ±5%"""
    return ci_any([
        r'GENERAL.*5\s*%|GENERAL.*0\.05',
        r'category.*GENERAL.*threshold.*5',
        r'GENERAL.*임계|임계.*GENERAL',
    ], code) or (ci(r'GENERAL', code) and ci(r'5\s*%|0\.05', code) and ci(r'threshold|임계|auto', code))


def eval_4_14(code):
    """FRESH 임계치 ±3%"""
    return ci_any([
        r'FRESH.*3\s*%|FRESH.*0\.03',
        r'category.*FRESH.*threshold.*3',
        r'FRESH.*임계|임계.*FRESH',
    ], code) or (ci(r'FRESH', code) and ci(r'3\s*%|0\.03', code) and ci(r'threshold|임계|auto', code))


def eval_4_15(code):
    """HAZMAT 임계치 ±1%"""
    return ci_any([
        r'HAZMAT.*1\s*%|HAZMAT.*0\.01',
        r'category.*HAZMAT.*threshold.*1',
        r'HAZMAT.*임계|임계.*HAZMAT',
    ], code) or (ci(r'HAZMAT', code) and ci(r'1\s*%|0\.01', code) and ci(r'threshold|임계|auto', code))


def eval_4_16(code):
    """HIGH_VALUE 자동승인 없음"""
    return ci_any([
        r'HIGH.?VALUE.*auto.?approv.*never|HIGH.?VALUE.*no.*auto',
        r'HIGH.?VALUE.*always.*pending|HIGH.?VALUE.*requires.*approval',
        r'HIGH.?VALUE.*무조건.*승인|고가품.*자동.*승인.*없',
        r'if.*HIGH.?VALUE.*requires.?approval.*true',
        r'HIGH.?VALUE.*0\s*%|HIGH.?VALUE.*threshold.*0',
    ], code) or (ci(r'HIGH.?VALUE', code) and ci(r'requires.?approval|approval.*required|승인.*필요', code))


def eval_4_17(code):
    """연속 조정 감시 조회"""
    return ci_any([
        r'7\s*일|7\s*day|7\s*days',
        r'recent.*adjustment.*same.*product|같은.*상품.*최근.*조정',
        r'consecutive.*adjust|연속.*조정',
        r'countRecent.*Adjustment|findRecent.*Adjustment',
        r'period.*7.*day.*same.*product.*location',
    ], code)


def eval_4_18(code):
    """연속 조정 → 승인 격상"""
    return ci_any([
        r'consecutive.*>=?\s*2.*pending|2.*이상.*승인.*격상',
        r'연속.*조정.*승인.*필요|격상.*승인',
        r'escalat.*approval|force.*pending',
        r'override.*auto.?approved.*pending',
    ], code) or (eval_4_17(code) and ci(r'pending|승인.*필요|격상|escalat', code))


def eval_4_19(code):
    """연속 조정 태그 추가"""
    return ci_any([
        r'연속조정감시', r'CONSECUTIVE.?ADJUSTMENT',
        r'consecutive.*tag|tag.*consecutive',
        r'reason.*append.*연속|reason.*\+.*연속',
    ], code)


def eval_4_20(code):
    """HIGH_VALUE audit_logs 기록"""
    return ci_any([
        r'audit.?log.*HIGH.?VALUE|HIGH.?VALUE.*audit.?log',
        r'고가품.*감사.*로그|감사.*로그.*고가품',
        r'HIGH.?VALUE.*audit|audit.*HIGH.?VALUE',
        r'saveAuditLog.*HIGH.?VALUE|logAudit.*HIGH',
    ], code) or (ci(r'HIGH.?VALUE|고가품', code) and ci(r'audit.?log|감사', code))


def eval_4_21(code):
    """HIGH_VALUE 재실사 권고"""
    return ci_any([
        r'HIGH.?VALUE.*recount|recount.*HIGH.?VALUE',
        r'고가품.*재실사|재실사.*고가품|재실사.*권고',
        r'recommend.*recount|suggest.*recount',
        r'HIGH.?VALUE.*verification|full.*verification',
    ], code)


def eval_4_22(code):
    """실사 시작 → is_frozen 설정"""
    return ci_any([
        r'cycle.?count.*is.?frozen.*true|is.?frozen.*true.*cycle.?count',
        r'실사.*시작.*동결|동결.*실사.*시작',
        r'startCycleCount.*frozen|frozen.*startCycleCount',
        r'location.*set.*frozen.*true.*cycle',
    ], code) or (ci(r'cycle.?count|실사', code) and ci(r'is.?frozen.*true|frozen.*true|동결', code))


def eval_4_23(code):
    """실사 완료 → is_frozen 해제"""
    return ci_any([
        r'cycle.?count.*complete.*is.?frozen.*false|is.?frozen.*false.*complete',
        r'실사.*완료.*동결.*해제|동결.*해제.*실사.*완료',
        r'completeCycleCount.*frozen.*false|unfreeze',
        r'set.*frozen.*false.*complete',
    ], code) or (ci(r'cycle.?count.*complete|실사.*완료|완료.*실사', code) and ci(r'is.?frozen.*false|frozen.*false|동결.*해제', code))


def eval_4_24(code):
    """안전재고 체크"""
    return ci_any([
        r'safety.?stock.*adjust|adjust.*safety.?stock',
        r'안전.?재고.*조정|조정.*안전.?재고',
    ], code) or (eval_2_25(code) and ci(r'adjust|조정', code))


def eval_4_25(code):
    """안전재고 트리거 기록"""
    return eval_2_26(code) or ci_any([
        r'SAFETY.?STOCK.?TRIGGER.*adjust|adjust.*SAFETY.?STOCK.?TRIGGER',
    ], code)


# ============================================================
# 전체 체크리스트
# ============================================================

CHECKLIST = {
    1: [
        # Level 1 (12항목)
        ("1-1", "PO 연결 필수", eval_1_1),
        ("1-2", "2단계 프로세스", eval_1_2),
        ("1-3", "초과입고 제한 로직", eval_1_3),
        ("1-4", "초과입고 거부 응답", eval_1_4),
        ("1-5", "재고 반영 시점 (confirmed)", eval_1_5),
        ("1-6", "유통기한 필수 체크", eval_1_6),
        ("1-7", "유통기한 비관리 처리", eval_1_7),
        ("1-8", "received_qty 누적", eval_1_8),
        ("1-9", "PO 상태 completed", eval_1_9),
        ("1-10", "PO 상태 partial", eval_1_10),
        ("1-11", "location.current_qty 갱신", eval_1_11),
        ("1-12", "입고 거부 시 무변동", eval_1_12),
        # Level 2 (13항목)
        ("1-13", "카테고리별 초과율 차등", eval_1_13),
        ("1-14", "HAZMAT 0% 예외 없음", eval_1_14),
        ("1-15", "PO유형 가중치", eval_1_15),
        ("1-16", "성수기 가중치", eval_1_16),
        ("1-17", "manufacture_date 필수", eval_1_17),
        ("1-18", "잔여율 < 30% 거부", eval_1_18),
        ("1-19", "잔여율 30~50% 승인 필요", eval_1_19),
        ("1-20", "보관유형 호환 체크", eval_1_20),
        ("1-21", "HAZMAT zone 체크", eval_1_21),
        ("1-22", "실사 동결 로케이션 거부", eval_1_22),
        ("1-23", "페널티 기록 (OVER_DELIVERY)", eval_1_23),
        ("1-24", "페널티 기록 (SHORT_SHELF_LIFE)", eval_1_24),
        ("1-25", "페널티 3회 → PO hold", eval_1_25),
    ],
    2: [
        # Level 1 (14항목)
        ("2-1", "FIFO 적용", eval_2_1),
        ("2-2", "FEFO 우선 적용", eval_2_2),
        ("2-3", "FEFO 동률 시 FIFO", eval_2_3),
        ("2-4", "분할 피킹", eval_2_4),
        ("2-5", "만료 재고 제외", eval_2_5),
        ("2-6", "부분출고", eval_2_6),
        ("2-7", "백오더 자동 생성", eval_2_7),
        ("2-8", "picked_qty 기록", eval_2_8),
        ("2-9", "부분출고 상태 partial", eval_2_9),
        ("2-10", "전량 백오더", eval_2_10),
        ("2-11", "inventory.quantity 차감", eval_2_11),
        ("2-12", "location.current_qty 차감", eval_2_12),
        ("2-13", "피킹 상세 반환", eval_2_13),
        ("2-14", "출고 완료 shipped", eval_2_14),
        # Level 2 (14항목)
        ("2-15", "is_expired 재고 제외", eval_2_15),
        ("2-16", "실사 동결 로케이션 제외", eval_2_16),
        ("2-17", "잔여율 < 30% 최우선 출고", eval_2_17),
        ("2-18", "잔여율 < 10% 출고 불가", eval_2_18),
        ("2-19", "HAZMAT zone 전용 피킹", eval_2_19),
        ("2-20", "max_pick_qty 제한", eval_2_20),
        ("2-21", "HAZMAT+FRESH 분리 출고", eval_2_21),
        ("2-22", "부분출고 70% 기준", eval_2_22),
        ("2-23", "긴급발주 트리거 (30~70%)", eval_2_23),
        ("2-24", "전량 백오더 (< 30%)", eval_2_24),
        ("2-25", "안전재고 체크", eval_2_25),
        ("2-26", "안전재고 트리거 기록", eval_2_26),
        ("2-27", "보관유형 불일치 경고", eval_2_27),
        ("2-28", "피킹 응답에 backorder 포함", eval_2_28),
    ],
    3: [
        # Level 1 (10항목)
        ("3-1", "단일 트랜잭션", eval_3_1),
        ("3-2", "롤백 처리", eval_3_2),
        ("3-3", "동일 로케이션 거부", eval_3_3),
        ("3-4", "출발지 재고 부족 체크", eval_3_4),
        ("3-5", "도착지 용량 체크", eval_3_5),
        ("3-6", "출발지 location.current_qty 차감", eval_3_6),
        ("3-7", "도착지 location.current_qty 증가", eval_3_7),
        ("3-8", "기존 레코드 병합", eval_3_8),
        ("3-9", "received_at 보존", eval_3_9),
        ("3-10", "이력 기록", eval_3_10),
        # Level 2 (10항목)
        ("3-11", "실사 동결 체크 (출발지)", eval_3_11),
        ("3-12", "실사 동결 체크 (도착지)", eval_3_12),
        ("3-13", "보관유형 호환 체크", eval_3_13),
        ("3-14", "HAZMAT zone 체크", eval_3_14),
        ("3-15", "혼적 금지 (HAZMAT→비HAZMAT)", eval_3_15),
        ("3-16", "혼적 금지 (비HAZMAT→HAZMAT)", eval_3_16),
        ("3-17", "유통기한 < 10% 이동 제한", eval_3_17),
        ("3-18", "유통기한 만료 이동 불가", eval_3_18),
        ("3-19", "대량 이동 승인 (80%)", eval_3_19),
        ("3-20", "안전재고 체크", eval_3_20),
    ],
    4: [
        # Level 1 (12항목)
        ("4-1", "reason 필수", eval_4_1),
        ("4-2", "차이 비율 계산", eval_4_2),
        ("4-3", "자동 승인 로직", eval_4_3),
        ("4-4", "자동 승인 즉시 반영", eval_4_4),
        ("4-5", ">5% 승인 대기", eval_4_5),
        ("4-6", "승인 전 미반영", eval_4_6),
        ("4-7", "system_qty=0 승인 필요", eval_4_7),
        ("4-8", "승인 후 반영", eval_4_8),
        ("4-9", "거부 시 무변동", eval_4_9),
        ("4-10", "location.current_qty 갱신", eval_4_10),
        ("4-11", "DELETE 불가", eval_4_11),
        ("4-12", "핵심 필드 UPDATE 불가", eval_4_12),
        # Level 2 (13항목)
        ("4-13", "GENERAL 임계치 ±5%", eval_4_13),
        ("4-14", "FRESH 임계치 ±3%", eval_4_14),
        ("4-15", "HAZMAT 임계치 ±1%", eval_4_15),
        ("4-16", "HIGH_VALUE 자동승인 없음", eval_4_16),
        ("4-17", "연속 조정 감시 조회", eval_4_17),
        ("4-18", "연속 조정 → 승인 격상", eval_4_18),
        ("4-19", "연속 조정 태그 추가", eval_4_19),
        ("4-20", "HIGH_VALUE audit_logs 기록", eval_4_20),
        ("4-21", "HIGH_VALUE 재실사 권고", eval_4_21),
        ("4-22", "실사 시작 → is_frozen 설정", eval_4_22),
        ("4-23", "실사 완료 → is_frozen 해제", eval_4_23),
        ("4-24", "안전재고 체크", eval_4_24),
        ("4-25", "안전재고 트리거 기록", eval_4_25),
    ],
}


def evaluate_lcr(group, task, run):
    """단일 결과에 대한 LCR 평가"""
    code = read_result(group, task, run)
    if not code:
        return None

    items = CHECKLIST.get(task, [])
    results = []
    for item_id, desc, eval_fn in items:
        passed = eval_fn(code)
        results.append({"id": item_id, "desc": desc, "passed": passed})

    return results


def main():
    GROUPS = ["A", "B", "C"]
    TASKS = [1, 2, 3, 4]
    RUNS = list(range(1, 6))

    print("=" * 70)
    print("  LCR (Logic Compliance Rate) 일괄 평가 — Level 2 (98항목)")
    print("=" * 70)

    all_rows = []
    detail_rows = []

    for group in GROUPS:
        for task in TASKS:
            for run in RUNS:
                results = evaluate_lcr(group, task, run)
                if results is None:
                    print(f"  [SKIP] {group}/task{task}/run{run}")
                    continue

                passed_count = sum(1 for r in results if r["passed"])
                total_count = len(results)
                pct = passed_count / total_count * 100 if total_count > 0 else 0

                failed_items = [r["id"] for r in results if not r["passed"]]
                failed_str = ", ".join(failed_items) if failed_items else "-"

                row = {
                    "Group": group, "Task": task, "Run": run,
                    "Passed": passed_count, "Total": total_count,
                    "LCR%": round(pct, 1), "Failed": failed_str,
                }
                all_rows.append(row)

                for r in results:
                    detail_rows.append({
                        "Group": group, "Task": task, "Run": run,
                        "Item": r["id"], "Description": r["desc"],
                        "Passed": "O" if r["passed"] else "X",
                    })

                print(f"  {group}/task{task}/run{run}: LCR={passed_count}/{total_count} ({pct:.0f}%) 미충족=[{failed_str}]")

    # CSV 저장
    csv_path = os.path.join(BASE_DIR, "lcr_results_all.csv")
    with open(csv_path, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=["Group", "Task", "Run", "Passed", "Total", "LCR%", "Failed"])
        writer.writeheader()
        writer.writerows(all_rows)
    print(f"\n  LCR 요약 저장: {csv_path}")

    detail_csv = os.path.join(BASE_DIR, "lcr_results_detail.csv")
    with open(detail_csv, 'w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=["Group", "Task", "Run", "Item", "Description", "Passed"])
        writer.writeheader()
        writer.writerows(detail_rows)
    print(f"  LCR 상세 저장: {detail_csv}")

    # ===== 요약 통계 =====
    print("\n  === 그룹별 전체 LCR 평균 ===")
    for group in GROUPS:
        group_rows = [r for r in all_rows if r["Group"] == group]
        if group_rows:
            avg_lcr = sum(r["LCR%"] for r in group_rows) / len(group_rows)
            total_passed = sum(r["Passed"] for r in group_rows)
            total_items = sum(r["Total"] for r in group_rows)
            print(f"  Group {group}: 평균 LCR = {avg_lcr:.1f}% ({total_passed}/{total_items})")

    print("\n  === 태스크별 LCR 평균 ===")
    for task in TASKS:
        print(f"\n  Task {task}:")
        for group in GROUPS:
            task_rows = [r for r in all_rows if r["Group"] == group and r["Task"] == task]
            if task_rows:
                avg = sum(r["LCR%"] for r in task_rows) / len(task_rows)
                passed = sum(r["Passed"] for r in task_rows)
                total = sum(r["Total"] for r in task_rows)
                print(f"    Group {group}: {avg:.1f}% ({passed}/{total})")

    # Level 1 vs Level 2 비교
    print("\n  === Level 1 vs Level 2 항목별 통과율 ===")
    l1_items = {1: 12, 2: 14, 3: 10, 4: 12}
    for task in TASKS:
        print(f"\n  Task {task}:")
        items = CHECKLIST[task]
        l1_count = l1_items[task]
        for group in GROUPS:
            l1_pass = sum(1 for r in detail_rows
                         if r["Group"] == group and r["Task"] == task
                         and r["Passed"] == "O"
                         and int(r["Item"].split("-")[1]) <= l1_count)
            l2_pass = sum(1 for r in detail_rows
                         if r["Group"] == group and r["Task"] == task
                         and r["Passed"] == "O"
                         and int(r["Item"].split("-")[1]) > l1_count)
            l1_total = l1_count * len([r for r in all_rows if r["Group"] == group and r["Task"] == task])
            l2_total = (len(items) - l1_count) * len([r for r in all_rows if r["Group"] == group and r["Task"] == task])
            if l1_total > 0 and l2_total > 0:
                print(f"    Group {group}: L1={l1_pass}/{l1_total} ({l1_pass/l1_total*100:.0f}%) "
                      f"L2={l2_pass}/{l2_total} ({l2_pass/l2_total*100:.0f}%)")


if __name__ == "__main__":
    main()
