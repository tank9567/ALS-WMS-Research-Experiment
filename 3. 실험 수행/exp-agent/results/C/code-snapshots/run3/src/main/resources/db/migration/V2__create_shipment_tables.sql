-- V2__create_shipment_tables.sql
-- 출고 관련 테이블 추가

-- 출고 지시서
CREATE TABLE shipment_orders (
    shipment_id UUID PRIMARY KEY,
    customer_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    picked_at TIMESTAMPTZ,
    shipped_at TIMESTAMPTZ,
    CONSTRAINT chk_shipment_status CHECK (status IN ('pending', 'picking', 'partial', 'shipped', 'cancelled'))
);

-- 출고 품목
CREATE TABLE shipment_order_lines (
    line_id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipment_orders(shipment_id),
    product_id UUID NOT NULL REFERENCES products(product_id),
    requested_qty INTEGER NOT NULL CHECK (requested_qty > 0),
    picked_qty INTEGER NOT NULL DEFAULT 0 CHECK (picked_qty >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_line_status CHECK (status IN ('pending', 'picked', 'partial', 'backordered'))
);

CREATE INDEX idx_shipment_lines_shipment ON shipment_order_lines(shipment_id);
CREATE INDEX idx_shipment_lines_product ON shipment_order_lines(product_id);

-- 백오더
CREATE TABLE backorders (
    backorder_id UUID PRIMARY KEY,
    shipment_line_id UUID NOT NULL REFERENCES shipment_order_lines(line_id),
    product_id UUID NOT NULL REFERENCES products(product_id),
    shortage_qty INTEGER NOT NULL CHECK (shortage_qty > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    fulfilled_at TIMESTAMPTZ,
    CONSTRAINT chk_backorder_status CHECK (status IN ('open', 'fulfilled', 'cancelled'))
);

CREATE INDEX idx_backorders_shipment_line ON backorders(shipment_line_id);
CREATE INDEX idx_backorders_product ON backorders(product_id);
CREATE INDEX idx_backorders_status ON backorders(status);

-- 안전재고 규칙
CREATE TABLE safety_stock_rules (
    rule_id UUID PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE REFERENCES products(product_id),
    min_qty INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_safety_stock_product ON safety_stock_rules(product_id);

-- 자동 재발주 로그
CREATE TABLE auto_reorder_logs (
    log_id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(product_id),
    reorder_qty INTEGER NOT NULL CHECK (reorder_qty > 0),
    reason VARCHAR(50) NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reorder_reason CHECK (reason IN ('SAFETY_STOCK_TRIGGER', 'URGENT_REORDER'))
);

CREATE INDEX idx_reorder_logs_product ON auto_reorder_logs(product_id);
CREATE INDEX idx_reorder_logs_triggered ON auto_reorder_logs(triggered_at);

-- 감사 로그
CREATE TABLE audit_logs (
    audit_id UUID PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_logs(created_at);
