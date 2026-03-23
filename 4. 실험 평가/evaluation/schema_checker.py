#!/usr/bin/env python3
"""
SDC (Schema Deviation Count) 자동 평가 스크립트 — Level 2

AI가 생성한 코드에서 스키마 이탈을 자동으로 탐지한다.
7가지 Deviation 유형을 검사하여 SDC 점수를 산출한다.

Level 2 스키마: 16개 테이블, 확장 컬럼 포함

Deviation 유형:
    D1: 필드명 변경     D2: 타입 변경     D3: 필드 누락
    D4: 임의 필드 추가   D5: 상태값 변경   D6: 테이블명 변경
    D7: 제약조건 누락
"""

import os
import re
import sys
import csv
from pathlib import Path
from dataclasses import dataclass, field

# ===== 참조 스키마 정의 (ALS-WMS-CORE-002 기준, 16 테이블) =====

REFERENCE_SCHEMA = {
    "products": {
        "columns": {
            "product_id": "UUID", "sku": "VARCHAR(50)", "name": "VARCHAR(200)",
            "category": "VARCHAR(50)", "storage_type": "VARCHAR(20)",
            "unit": "VARCHAR(20)", "has_expiry": "BOOLEAN",
            "min_remaining_shelf_life_pct": "INTEGER",
            "max_pick_qty": "INTEGER",
            "manufacture_date_required": "BOOLEAN",
            "created_at": "TIMESTAMPTZ", "updated_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "product_id", "unique": ["sku"],
            "not_null": ["product_id", "sku", "name", "unit", "has_expiry", "category", "storage_type"],
            "checks": [
                "category IN ('GENERAL', 'FRESH', 'HAZMAT', 'HIGH_VALUE')",
                "storage_type IN ('AMBIENT', 'COLD', 'FROZEN')",
            ],
        },
        "status_values": {
            "category": ["GENERAL", "FRESH", "HAZMAT", "HIGH_VALUE"],
            "storage_type": ["AMBIENT", "COLD", "FROZEN"],
        },
    },
    "locations": {
        "columns": {
            "location_id": "UUID", "code": "VARCHAR(20)", "zone": "VARCHAR(50)",
            "storage_type": "VARCHAR(20)", "capacity": "INTEGER",
            "current_qty": "INTEGER", "is_active": "BOOLEAN",
            "is_frozen": "BOOLEAN", "created_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "location_id", "unique": ["code"],
            "not_null": ["location_id", "code", "zone", "capacity", "current_qty", "is_active"],
            "checks": [
                "zone IN ('RECEIVING', 'STORAGE', 'SHIPPING', 'HAZMAT')",
                "storage_type IN ('AMBIENT', 'COLD', 'FROZEN')",
            ],
        },
        "status_values": {
            "zone": ["RECEIVING", "STORAGE", "SHIPPING", "HAZMAT"],
            "storage_type": ["AMBIENT", "COLD", "FROZEN"],
        },
    },
    "inventory": {
        "columns": {
            "inventory_id": "UUID", "product_id": "UUID", "location_id": "UUID",
            "quantity": "INTEGER", "lot_number": "VARCHAR(50)",
            "expiry_date": "DATE", "manufacture_date": "DATE",
            "received_at": "TIMESTAMPTZ", "is_expired": "BOOLEAN",
            "created_at": "TIMESTAMPTZ", "updated_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "inventory_id",
            "fk": ["product_id->products", "location_id->locations"],
            "unique": [("product_id", "location_id", "lot_number")],
            "checks": ["quantity >= 0"],
            "not_null": ["inventory_id", "product_id", "location_id", "quantity", "received_at"],
        },
        "status_values": {},
    },
    "suppliers": {
        "columns": {
            "supplier_id": "UUID", "name": "VARCHAR(200)",
            "contact_info": "VARCHAR(500)", "status": "VARCHAR(20)",
            "created_at": "TIMESTAMPTZ", "updated_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "supplier_id",
            "not_null": ["supplier_id", "name", "status"],
        },
        "status_values": {
            "status": ["active", "hold", "inactive"],
        },
    },
    "supplier_penalties": {
        "columns": {
            "penalty_id": "UUID", "supplier_id": "UUID",
            "penalty_type": "VARCHAR(50)", "description": "VARCHAR(500)",
            "po_id": "UUID", "created_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "penalty_id",
            "fk": ["supplier_id->suppliers"],
            "not_null": ["penalty_id", "supplier_id", "penalty_type"],
        },
        "status_values": {
            "penalty_type": ["OVER_DELIVERY", "SHORT_SHELF_LIFE"],
        },
    },
    "purchase_orders": {
        "columns": {
            "po_id": "UUID", "po_number": "VARCHAR(30)",
            "supplier_id": "UUID", "po_type": "VARCHAR(20)",
            "status": "VARCHAR(20)", "ordered_at": "TIMESTAMPTZ",
            "created_at": "TIMESTAMPTZ", "updated_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "po_id", "unique": ["po_number"],
            "fk": ["supplier_id->suppliers"],
            "not_null": ["po_id", "po_number", "supplier_id", "status", "ordered_at"],
        },
        "status_values": {
            "status": ["pending", "partial", "completed", "cancelled", "hold"],
            "po_type": ["NORMAL", "URGENT", "IMPORT"],
        },
    },
    "purchase_order_lines": {
        "columns": {
            "po_line_id": "UUID", "po_id": "UUID", "product_id": "UUID",
            "ordered_qty": "INTEGER", "received_qty": "INTEGER",
            "unit_price": "NUMERIC(12,2)",
        },
        "constraints": {
            "pk": "po_line_id",
            "fk": ["po_id->purchase_orders", "product_id->products"],
            "unique": [("po_id", "product_id")],
            "checks": ["ordered_qty > 0", "received_qty >= 0"],
            "not_null": ["po_line_id", "po_id", "product_id", "ordered_qty"],
        },
        "status_values": {},
    },
    "inbound_receipts": {
        "columns": {
            "receipt_id": "UUID", "po_id": "UUID", "status": "VARCHAR(20)",
            "received_by": "VARCHAR(100)", "received_at": "TIMESTAMPTZ",
            "confirmed_at": "TIMESTAMPTZ", "created_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "receipt_id",
            "fk": ["po_id->purchase_orders"],
            "not_null": ["receipt_id", "po_id", "status", "received_by"],
        },
        "status_values": {
            "status": ["inspecting", "pending_approval", "confirmed", "rejected"],
        },
    },
    "inbound_receipt_lines": {
        "columns": {
            "receipt_line_id": "UUID", "receipt_id": "UUID",
            "product_id": "UUID", "location_id": "UUID",
            "quantity": "INTEGER", "lot_number": "VARCHAR(50)",
            "expiry_date": "DATE", "manufacture_date": "DATE",
        },
        "constraints": {
            "pk": "receipt_line_id",
            "fk": ["receipt_id->inbound_receipts", "product_id->products", "location_id->locations"],
            "checks": ["quantity > 0"],
            "not_null": ["receipt_line_id", "receipt_id", "product_id", "location_id", "quantity"],
        },
        "status_values": {},
    },
    "shipment_orders": {
        "columns": {
            "shipment_id": "UUID", "shipment_number": "VARCHAR(30)",
            "customer_name": "VARCHAR(200)", "status": "VARCHAR(20)",
            "requested_at": "TIMESTAMPTZ", "shipped_at": "TIMESTAMPTZ",
            "created_at": "TIMESTAMPTZ", "updated_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "shipment_id", "unique": ["shipment_number"],
            "not_null": ["shipment_id", "shipment_number", "customer_name", "status", "requested_at"],
        },
        "status_values": {
            "status": ["pending", "picking", "partial", "shipped", "cancelled"],
        },
    },
    "shipment_order_lines": {
        "columns": {
            "shipment_line_id": "UUID", "shipment_id": "UUID",
            "product_id": "UUID", "requested_qty": "INTEGER",
            "picked_qty": "INTEGER", "status": "VARCHAR(20)",
        },
        "constraints": {
            "pk": "shipment_line_id",
            "fk": ["shipment_id->shipment_orders", "product_id->products"],
            "checks": ["requested_qty > 0", "picked_qty >= 0"],
            "not_null": ["shipment_line_id", "shipment_id", "product_id", "requested_qty", "status"],
        },
        "status_values": {
            "status": ["pending", "picked", "partial", "backordered"],
        },
    },
    "backorders": {
        "columns": {
            "backorder_id": "UUID", "shipment_line_id": "UUID",
            "product_id": "UUID", "shortage_qty": "INTEGER",
            "status": "VARCHAR(20)", "created_at": "TIMESTAMPTZ",
            "fulfilled_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "backorder_id",
            "fk": ["shipment_line_id->shipment_order_lines", "product_id->products"],
            "checks": ["shortage_qty > 0"],
            "not_null": ["backorder_id", "shipment_line_id", "product_id", "shortage_qty", "status"],
        },
        "status_values": {
            "status": ["open", "fulfilled", "cancelled"],
        },
    },
    "stock_transfers": {
        "columns": {
            "transfer_id": "UUID", "product_id": "UUID",
            "from_location_id": "UUID", "to_location_id": "UUID",
            "quantity": "INTEGER", "lot_number": "VARCHAR(50)",
            "reason": "VARCHAR(500)", "transfer_status": "VARCHAR(20)",
            "transferred_by": "VARCHAR(100)", "approved_by": "VARCHAR(100)",
            "transferred_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "transfer_id",
            "fk": ["product_id->products", "from_location_id->locations", "to_location_id->locations"],
            "checks": ["quantity > 0", "from_location_id != to_location_id"],
            "not_null": ["transfer_id", "product_id", "from_location_id", "to_location_id", "quantity", "transferred_by"],
        },
        "status_values": {
            "transfer_status": ["immediate", "pending_approval", "approved", "rejected"],
        },
    },
    "inventory_adjustments": {
        "columns": {
            "adjustment_id": "UUID", "product_id": "UUID",
            "location_id": "UUID", "system_qty": "INTEGER",
            "actual_qty": "INTEGER", "difference": "INTEGER",
            "reason": "VARCHAR(500)", "requires_approval": "BOOLEAN",
            "approval_status": "VARCHAR(20)", "approved_by": "VARCHAR(100)",
            "adjusted_by": "VARCHAR(100)", "created_at": "TIMESTAMPTZ",
            "approved_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "adjustment_id",
            "fk": ["product_id->products", "location_id->locations"],
            "not_null": ["adjustment_id", "product_id", "location_id", "system_qty",
                         "actual_qty", "difference", "reason", "approval_status", "adjusted_by"],
        },
        "status_values": {
            "approval_status": ["auto_approved", "pending", "approved", "rejected"],
        },
    },
    "audit_logs": {
        "columns": {
            "log_id": "UUID", "event_type": "VARCHAR(50)",
            "entity_type": "VARCHAR(50)", "entity_id": "UUID",
            "details": "JSONB", "performed_by": "VARCHAR(100)",
            "created_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "log_id",
            "not_null": ["log_id", "event_type", "entity_type", "entity_id", "details", "performed_by"],
        },
        "status_values": {},
    },
    "safety_stock_rules": {
        "columns": {
            "rule_id": "UUID", "product_id": "UUID",
            "min_qty": "INTEGER", "reorder_qty": "INTEGER",
            "created_at": "TIMESTAMPTZ", "updated_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "rule_id",
            "fk": ["product_id->products"],
            "unique": ["product_id"],
            "checks": ["min_qty >= 0", "reorder_qty > 0"],
            "not_null": ["rule_id", "product_id", "min_qty", "reorder_qty"],
        },
        "status_values": {},
    },
    "auto_reorder_logs": {
        "columns": {
            "reorder_log_id": "UUID", "product_id": "UUID",
            "trigger_type": "VARCHAR(50)", "current_stock": "INTEGER",
            "min_qty": "INTEGER", "reorder_qty": "INTEGER",
            "triggered_by": "VARCHAR(100)", "created_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "reorder_log_id",
            "fk": ["product_id->products"],
            "not_null": ["reorder_log_id", "product_id", "trigger_type", "current_stock",
                         "min_qty", "reorder_qty", "triggered_by"],
        },
        "status_values": {
            "trigger_type": ["SAFETY_STOCK_TRIGGER", "URGENT_REORDER"],
        },
    },
    "seasonal_config": {
        "columns": {
            "season_id": "UUID", "season_name": "VARCHAR(100)",
            "start_date": "DATE", "end_date": "DATE",
            "multiplier": "NUMERIC(3,2)", "is_active": "BOOLEAN",
            "created_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "season_id",
            "not_null": ["season_id", "season_name", "start_date", "end_date", "multiplier", "is_active"],
        },
        "status_values": {},
    },
    "cycle_counts": {
        "columns": {
            "cycle_count_id": "UUID", "location_id": "UUID",
            "status": "VARCHAR(20)", "started_by": "VARCHAR(100)",
            "started_at": "TIMESTAMPTZ", "completed_at": "TIMESTAMPTZ",
        },
        "constraints": {
            "pk": "cycle_count_id",
            "fk": ["location_id->locations"],
            "not_null": ["cycle_count_id", "location_id", "status", "started_by"],
        },
        "status_values": {
            "status": ["in_progress", "completed"],
        },
    },
}

# ===== 흔한 필드명 변환 (D1 탐지용) =====
COMMON_RENAMES = {
    "po_id": ["purchase_order_id", "purchaseOrderId", "order_id"],
    "receipt_id": ["inbound_receipt_id", "inboundReceiptId", "receiving_id"],
    "shipment_id": ["shipment_order_id", "shipmentOrderId"],
    "transfer_id": ["stock_transfer_id", "stockTransferId", "movement_id"],
    "adjustment_id": ["inventory_adjustment_id", "inventoryAdjustmentId"],
    "po_line_id": ["purchase_order_line_id", "purchaseOrderLineId"],
    "receipt_line_id": ["inbound_receipt_line_id", "inboundReceiptLineId"],
    "shipment_line_id": ["shipment_order_line_id", "shipmentOrderLineId"],
    "ordered_qty": ["order_quantity", "orderQuantity", "order_qty"],
    "received_qty": ["received_quantity", "receivedQuantity", "receive_qty"],
    "picked_qty": ["picked_quantity", "pickedQuantity", "pick_qty"],
    "requested_qty": ["requested_quantity", "requestedQuantity", "request_qty"],
    "shortage_qty": ["shortage_quantity", "shortageQuantity"],
    "current_qty": ["current_quantity", "currentQuantity"],
    "system_qty": ["system_quantity", "systemQuantity"],
    "actual_qty": ["actual_quantity", "actualQuantity"],
    "has_expiry": ["hasExpiry", "has_expiration", "expiry_managed"],
    "lot_number": ["lotNumber", "lot_no", "batch_number"],
    "expiry_date": ["expiryDate", "expiration_date", "expirationDate"],
    "received_at": ["receivedAt", "receive_date", "receiveDate"],
    "confirmed_at": ["confirmedAt", "confirm_date"],
    "transferred_at": ["transferredAt", "transfer_date"],
    "transferred_by": ["transferredBy", "transfer_user"],
    "adjusted_by": ["adjustedBy", "adjust_user"],
    "approved_by": ["approvedBy", "approve_user"],
    "approved_at": ["approvedAt", "approve_date"],
    "received_by": ["receivedBy", "receive_user"],
    "supplier_name": ["supplierName", "vendor_name", "supplier"],
    "customer_name": ["customerName", "customer"],
    "po_number": ["poNumber", "purchase_order_number"],
    "shipment_number": ["shipmentNumber", "shipment_no"],
    "requires_approval": ["requiresApproval", "needs_approval"],
    "approval_status": ["approvalStatus", "approve_status"],
    "is_active": ["isActive", "active"],
    "unit_price": ["unitPrice", "price"],
    # Level 2 신규 필드
    "storage_type": ["storageType", "storage"],
    "is_frozen": ["isFrozen", "frozen"],
    "is_expired": ["isExpired", "expired"],
    "manufacture_date": ["manufactureDate", "mfg_date", "mfgDate"],
    "min_remaining_shelf_life_pct": ["minRemainingShelfLifePct", "shelf_life_pct"],
    "max_pick_qty": ["maxPickQty", "max_pick_quantity"],
    "manufacture_date_required": ["manufactureDateRequired", "mfg_date_required"],
    "po_type": ["poType", "purchase_order_type", "order_type"],
    "supplier_id": ["supplierId", "vendor_id"],
    "penalty_type": ["penaltyType", "penalty_kind"],
    "transfer_status": ["transferStatus", "move_status"],
    "trigger_type": ["triggerType", "trigger_kind"],
    "season_name": ["seasonName", "name"],
    "cycle_count_id": ["cycleCountId", "count_id"],
    "reorder_log_id": ["reorderLogId", "log_id"],
    "rule_id": ["ruleId", "safety_rule_id"],
    "log_id": ["logId", "audit_id"],
    "event_type": ["eventType", "event_kind"],
    "entity_type": ["entityType", "entity_kind"],
    "entity_id": ["entityId"],
    "performed_by": ["performedBy", "user"],
    "current_stock": ["currentStock", "stock_level"],
    "reorder_qty": ["reorderQty", "reorder_quantity"],
    "min_qty": ["minQty", "minimum_quantity"],
    "started_by": ["startedBy", "start_user"],
    "started_at": ["startedAt", "start_date"],
    "completed_at": ["completedAt", "complete_date"],
    "contact_info": ["contactInfo", "contact"],
    "penalty_id": ["penaltyId"],
    "season_id": ["seasonId"],
    "triggered_by": ["triggeredBy", "trigger_user"],
    "backorder_id": ["backorderId", "back_order_id"],
}

# 흔한 테이블명 변환 (D6 탐지용)
COMMON_TABLE_RENAMES = {
    "inbound_receipts": ["receipts", "receiving", "inbound", "inbounds", "receiving_orders"],
    "inbound_receipt_lines": ["receipt_lines", "receiving_lines", "inbound_lines"],
    "shipment_orders": ["shipments", "outbound_orders", "outbound", "outbounds"],
    "shipment_order_lines": ["shipment_lines", "outbound_lines"],
    "purchase_orders": ["orders", "po", "purchase_order"],
    "purchase_order_lines": ["order_lines", "po_lines"],
    "stock_transfers": ["transfers", "movements", "stock_movements", "inventory_transfers"],
    "inventory_adjustments": ["adjustments", "stock_adjustments"],
    "backorders": ["back_orders", "shortage_orders"],
    # Level 2 신규 테이블
    "supplier_penalties": ["penalties", "supplier_penalty"],
    "safety_stock_rules": ["safety_rules", "stock_rules", "safety_stocks"],
    "auto_reorder_logs": ["reorder_logs", "auto_reorders", "reorders"],
    "seasonal_config": ["seasons", "season_config", "seasonal_configs"],
    "cycle_counts": ["cycle_count", "stock_counts", "physical_counts"],
    "audit_logs": ["audits", "audit_log", "event_logs"],
}


@dataclass
class Deviation:
    type: str
    table: str
    detail: str
    severity: str = "warning"


@dataclass
class CheckResult:
    deviations: list = field(default_factory=list)
    d1_count: int = 0
    d2_count: int = 0
    d3_count: int = 0
    d4_count: int = 0
    d5_count: int = 0
    d6_count: int = 0
    d7_count: int = 0

    @property
    def total(self):
        return self.d1_count + self.d2_count + self.d3_count + \
               self.d4_count + self.d5_count + self.d6_count + self.d7_count

    def add(self, deviation: Deviation):
        self.deviations.append(deviation)
        attr = f"d{deviation.type[1]}_count"
        setattr(self, attr, getattr(self, attr) + 1)


def read_code_files(path: str) -> str:
    code_extensions = {'.java', '.kt', '.py', '.ts', '.js', '.sql', '.xml', '.yml', '.yaml', '.json'}
    all_code = []
    target = Path(path)
    if target.is_file():
        all_code.append(target.read_text(encoding='utf-8', errors='ignore'))
    elif target.is_dir():
        for ext in code_extensions:
            for f in target.rglob(f'*{ext}'):
                try:
                    content = f.read_text(encoding='utf-8', errors='ignore')
                    all_code.append(f"// === FILE: {f.relative_to(target)} ===\n{content}")
                except Exception:
                    pass
    return '\n'.join(all_code)


def check_table_names(code: str, result: CheckResult):
    code_lower = code.lower()
    for table_name, alternatives in COMMON_TABLE_RENAMES.items():
        table_found = table_name in code_lower
        if not table_found:
            for alt in alternatives:
                if alt in code_lower:
                    result.add(Deviation(
                        "D6", table_name,
                        f"테이블명 변경: '{table_name}' -> '{alt}'", "error"
                    ))
                    break


def check_field_names(code: str, result: CheckResult):
    code_lower = code.lower()
    for original, alternatives in COMMON_RENAMES.items():
        if original not in code_lower:
            for alt in alternatives:
                if alt.lower() in code_lower:
                    result.add(Deviation(
                        "D1", "", f"필드명 변경: '{original}' -> '{alt}'", "warning"
                    ))
                    break


def check_field_completeness(code: str, result: CheckResult):
    code_lower = code.lower()
    for table_name, schema in REFERENCE_SCHEMA.items():
        if table_name not in code_lower:
            continue
        for col_name in schema["columns"]:
            camel = re.sub(r'_([a-z])', lambda m: m.group(1).upper(), col_name)
            if col_name not in code_lower and camel.lower() not in code_lower:
                result.add(Deviation(
                    "D3", table_name, f"필드 누락: '{table_name}.{col_name}'", "warning"
                ))


def check_type_usage(code: str, result: CheckResult):
    id_patterns = [
        (r'(private|protected|public)\s+(Long|Integer|long|int)\s+\w*(id|Id)\b', 'Long/Integer for ID (should be UUID)'),
        (r'@GeneratedValue.*GenerationType\.(IDENTITY|SEQUENCE|AUTO)', 'AUTO_INCREMENT instead of UUID'),
        (r'bigint.*_id\b', 'BIGINT for ID column (should be UUID)'),
        (r'serial\b', 'SERIAL type (should be UUID)'),
    ]
    for pattern, description in id_patterns:
        matches = re.findall(pattern, code, re.IGNORECASE)
        if matches:
            result.add(Deviation(
                "D2", "", f"타입 변경: {description} (발견 {len(matches)}건)", "error"
            ))

    code_lower = code.lower()
    if 'timestamp ' in code_lower and 'timestamptz' not in code_lower:
        if 'localdatetime' in code_lower and 'offsetdatetime' not in code_lower and 'zoneddatetime' not in code_lower:
            result.add(Deviation(
                "D2", "", "타입 변경: LocalDateTime 사용 (TIMESTAMPTZ -> OffsetDateTime 권장)", "warning"
            ))


def check_status_values(code: str, result: CheckResult):
    for table_name, schema in REFERENCE_SCHEMA.items():
        for field_name, valid_values in schema.get("status_values", {}).items():
            for value in valid_values:
                if f'"{value}"' not in code and f"'{value}'" not in code:
                    if table_name.lower() in code.lower():
                        result.add(Deviation(
                            "D5", table_name,
                            f"상태값 누락 가능: '{table_name}.{field_name}' = '{value}'", "warning"
                        ))


def check_constraints(code: str, result: CheckResult):
    code_lower = code.lower()
    qty_check_patterns = [
        r'check\s*\(\s*quantity\s*>=\s*0', r'@min\s*\(\s*0\s*\)',
        r'@positiveorzero', r'quantity\s*>=\s*0',
    ]
    has_qty_check = any(re.search(p, code_lower) for p in qty_check_patterns)
    if not has_qty_check and 'quantity' in code_lower:
        result.add(Deviation("D7", "inventory", "제약조건 누락: quantity >= 0 CHECK 미확인", "warning"))

    status_check_patterns = [
        r'check\s*\(\s*status\s+in\b', r'@enumerated', r'enum\s+\w*status',
    ]
    has_status_check = any(re.search(p, code_lower) for p in status_check_patterns)
    if not has_status_check and 'status' in code_lower:
        result.add(Deviation("D7", "", "제약조건 누락: status CHECK IN 제약 미확인", "warning"))

    fk_patterns = [r'@manytoone|@joincolumn|references\s+\w+\(', r'foreign\s+key']
    has_fk = any(re.search(p, code_lower) for p in fk_patterns)
    if not has_fk:
        result.add(Deviation("D7", "", "제약조건 누락: 외래키(FK) 정의 미확인", "warning"))


def run_check(code_path: str) -> CheckResult:
    code = read_code_files(code_path)
    if not code.strip():
        return CheckResult()
    result = CheckResult()
    check_table_names(code, result)
    check_field_names(code, result)
    check_field_completeness(code, result)
    check_type_usage(code, result)
    check_status_values(code, result)
    check_constraints(code, result)
    return result


def print_report(result: CheckResult):
    print("\n" + "=" * 60)
    print("  SDC (Schema Deviation Count) 검사 결과")
    print("=" * 60)
    type_labels = {
        "D1": "필드명 변경", "D2": "타입 변경", "D3": "필드 누락",
        "D4": "임의 필드 추가", "D5": "상태값 변경", "D6": "테이블명 변경",
        "D7": "제약조건 누락",
    }
    print(f"\n  {'유형':<6} {'설명':<15} {'건수':>5}")
    print(f"  {'-'*6} {'-'*15} {'-'*5}")
    for dtype, label in type_labels.items():
        count = getattr(result, f"d{dtype[1]}_count")
        print(f"  {dtype:<6} {label:<15} {count:>5}")
    print(f"\n  {'합계':<6} {'':.<15} {result.total:>5}")
    print(f"\n  SDC = {result.total}건")
    print("=" * 60)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("사용법: python schema_checker.py <ai_generated_code_path>")
        sys.exit(1)
    result = run_check(sys.argv[1])
    print_report(result)
