-- ========================================
-- V4: 재고 실사 및 조정 테이블
-- Based on: ALS-WMS-ADJ-002
-- ========================================

-- ========================================
-- 1. 실사 세션 (Cycle Counts)
-- ========================================
CREATE TABLE cycle_counts (
    cycle_count_id  UUID PRIMARY KEY,
    location_id     UUID NOT NULL REFERENCES locations(location_id),
    status          VARCHAR(20) NOT NULL DEFAULT 'in_progress'
                    CHECK (status IN ('in_progress', 'completed')),
    started_by      VARCHAR(100) NOT NULL,
    started_at      TIMESTAMPTZ DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    notes           TEXT
);

-- ========================================
-- 2. 재고 조정 (Inventory Adjustments)
-- ========================================
CREATE TABLE inventory_adjustments (
    adjustment_id       UUID PRIMARY KEY,
    product_id          UUID NOT NULL REFERENCES products(product_id),
    location_id         UUID NOT NULL REFERENCES locations(location_id),
    system_qty          INTEGER NOT NULL,
    actual_qty          INTEGER NOT NULL,
    difference          INTEGER NOT NULL,
    reason              TEXT NOT NULL,
    requires_approval   BOOLEAN NOT NULL DEFAULT false,
    approval_status     VARCHAR(20) NOT NULL DEFAULT 'pending'
                        CHECK (approval_status IN ('pending', 'auto_approved', 'approved', 'rejected')),
    approved_by         VARCHAR(100),
    approved_at         TIMESTAMPTZ,
    created_by          VARCHAR(100) NOT NULL,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    cycle_count_id      UUID REFERENCES cycle_counts(cycle_count_id)
);

-- ========================================
-- 인덱스
-- ========================================
CREATE INDEX idx_adjustments_product_location ON inventory_adjustments(product_id, location_id);
CREATE INDEX idx_adjustments_created_at ON inventory_adjustments(created_at);
CREATE INDEX idx_adjustments_approval_status ON inventory_adjustments(approval_status);
CREATE INDEX idx_cycle_counts_location ON cycle_counts(location_id);
