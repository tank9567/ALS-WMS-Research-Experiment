-- WMS Database Schema

-- Products Table
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL CHECK (category IN ('GENERAL', 'FRESH', 'HAZMAT', 'HIGH_VALUE')),
    unit VARCHAR(20) NOT NULL,
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    manages_expiry BOOLEAN NOT NULL DEFAULT false,
    shelf_life_days INTEGER,
    min_remaining_shelf_life_pct INTEGER DEFAULT 30,
    max_pick_qty INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Locations Table
CREATE TABLE locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    zone VARCHAR(50) NOT NULL CHECK (zone IN ('RECEIVING', 'STORAGE', 'SHIPPING', 'HAZMAT')),
    storage_type VARCHAR(20) NOT NULL CHECK (storage_type IN ('AMBIENT', 'COLD', 'FROZEN', 'HAZMAT')),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    current_qty INTEGER NOT NULL DEFAULT 0 CHECK (current_qty >= 0),
    is_frozen BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_capacity CHECK (current_qty <= capacity)
);

-- Inventory Table
CREATE TABLE inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    lot_number VARCHAR(100),
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    manufacture_date DATE,
    expiry_date DATE,
    received_at TIMESTAMPTZ NOT NULL,
    is_expired BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(product_id, location_id, lot_number, expiry_date)
);

-- Suppliers Table
CREATE TABLE suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    contact VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'hold', 'inactive')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Supplier Penalties Table
CREATE TABLE supplier_penalties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    penalty_type VARCHAR(50) NOT NULL CHECK (penalty_type IN ('OVER_DELIVERY', 'SHORT_SHELF_LIFE')),
    reason TEXT,
    penalized_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_supplier_penalties_date ON supplier_penalties(supplier_id, penalized_at);

-- Purchase Orders Table
CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_number VARCHAR(100) UNIQUE NOT NULL,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    po_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (po_type IN ('NORMAL', 'URGENT', 'IMPORT')),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'partial', 'completed', 'cancelled', 'hold')),
    order_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Purchase Order Lines Table
CREATE TABLE purchase_order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    ordered_qty INTEGER NOT NULL CHECK (ordered_qty > 0),
    received_qty INTEGER NOT NULL DEFAULT 0 CHECK (received_qty >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Inbound Receipts Table
CREATE TABLE inbound_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_number VARCHAR(100) UNIQUE NOT NULL,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    status VARCHAR(30) NOT NULL DEFAULT 'inspecting' CHECK (status IN ('inspecting', 'pending_approval', 'confirmed', 'rejected')),
    received_date TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Inbound Receipt Lines Table
CREATE TABLE inbound_receipt_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inbound_receipt_id UUID NOT NULL REFERENCES inbound_receipts(id),
    purchase_order_line_id UUID NOT NULL REFERENCES purchase_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    lot_number VARCHAR(100),
    received_qty INTEGER NOT NULL CHECK (received_qty > 0),
    manufacture_date DATE,
    expiry_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seasonal Config Table
CREATE TABLE seasonal_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    season_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.5,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_date_range CHECK (end_date >= start_date)
);

-- Shipment Orders Table
CREATE TABLE shipment_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_number VARCHAR(100) UNIQUE NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'picking', 'partial', 'shipped', 'cancelled')),
    order_date TIMESTAMPTZ NOT NULL,
    shipped_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Shipment Order Lines Table
CREATE TABLE shipment_order_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_order_id UUID NOT NULL REFERENCES shipment_orders(id),
    product_id UUID NOT NULL REFERENCES products(id),
    requested_qty INTEGER NOT NULL CHECK (requested_qty > 0),
    picked_qty INTEGER NOT NULL DEFAULT 0 CHECK (picked_qty >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'picked', 'partial', 'backordered')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Backorders Table
CREATE TABLE backorders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_order_line_id UUID NOT NULL REFERENCES shipment_order_lines(id),
    product_id UUID NOT NULL REFERENCES products(id),
    backordered_qty INTEGER NOT NULL CHECK (backordered_qty > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'fulfilled', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Safety Stock Rules Table
CREATE TABLE safety_stock_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL UNIQUE REFERENCES products(id),
    min_qty INTEGER NOT NULL CHECK (min_qty >= 0),
    reorder_qty INTEGER NOT NULL CHECK (reorder_qty > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Auto Reorder Logs Table
CREATE TABLE auto_reorder_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    trigger_reason VARCHAR(50) NOT NULL CHECK (trigger_reason IN ('SAFETY_STOCK_TRIGGER', 'URGENT_REORDER')),
    current_stock INTEGER NOT NULL,
    reorder_qty INTEGER NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Audit Logs Table
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    description TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Stock Transfers Table
CREATE TABLE stock_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    from_location_id UUID NOT NULL REFERENCES locations(id),
    to_location_id UUID NOT NULL REFERENCES locations(id),
    lot_number VARCHAR(100),
    expiry_date DATE,
    transfer_qty INTEGER NOT NULL CHECK (transfer_qty > 0),
    transfer_status VARCHAR(30) NOT NULL DEFAULT 'immediate' CHECK (transfer_status IN ('immediate', 'pending_approval', 'approved', 'rejected')),
    reason TEXT,
    requested_by VARCHAR(255),
    approved_by VARCHAR(255),
    transferred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_different_locations CHECK (from_location_id != to_location_id)
);

-- Cycle Counts Table
CREATE TABLE cycle_counts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES locations(id),
    product_id UUID NOT NULL REFERENCES products(id),
    lot_number VARCHAR(100),
    expiry_date DATE,
    system_qty INTEGER NOT NULL,
    counted_qty INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'completed')),
    counted_by VARCHAR(255),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Inventory Adjustments Table
CREATE TABLE inventory_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cycle_count_id UUID REFERENCES cycle_counts(id),
    product_id UUID NOT NULL REFERENCES products(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    lot_number VARCHAR(100),
    expiry_date DATE,
    system_qty INTEGER NOT NULL,
    actual_qty INTEGER NOT NULL,
    difference_qty INTEGER NOT NULL,
    reason TEXT NOT NULL,
    approval_status VARCHAR(30) NOT NULL DEFAULT 'pending' CHECK (approval_status IN ('auto_approved', 'pending', 'approved', 'rejected')),
    approved_by VARCHAR(255),
    approved_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_inventory_product ON inventory(product_id);
CREATE INDEX idx_inventory_location ON inventory(location_id);
CREATE INDEX idx_inventory_expiry ON inventory(expiry_date) WHERE expiry_date IS NOT NULL;
CREATE INDEX idx_po_lines_po ON purchase_order_lines(purchase_order_id);
CREATE INDEX idx_po_lines_product ON purchase_order_lines(product_id);
CREATE INDEX idx_inbound_lines_receipt ON inbound_receipt_lines(inbound_receipt_id);
CREATE INDEX idx_inbound_lines_po_line ON inbound_receipt_lines(purchase_order_line_id);
CREATE INDEX idx_seasonal_dates ON seasonal_config(start_date, end_date);
CREATE INDEX idx_shipment_lines_order ON shipment_order_lines(shipment_order_id);
CREATE INDEX idx_shipment_lines_product ON shipment_order_lines(product_id);
CREATE INDEX idx_backorders_line ON backorders(shipment_order_line_id);
CREATE INDEX idx_backorders_product ON backorders(product_id);
CREATE INDEX idx_safety_stock_product ON safety_stock_rules(product_id);
CREATE INDEX idx_auto_reorder_product ON auto_reorder_logs(product_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_stock_transfers_product ON stock_transfers(product_id);
CREATE INDEX idx_stock_transfers_from_location ON stock_transfers(from_location_id);
CREATE INDEX idx_stock_transfers_to_location ON stock_transfers(to_location_id);
CREATE INDEX idx_stock_transfers_status ON stock_transfers(transfer_status);
CREATE INDEX idx_cycle_counts_location ON cycle_counts(location_id);
CREATE INDEX idx_cycle_counts_product ON cycle_counts(product_id);
CREATE INDEX idx_cycle_counts_status ON cycle_counts(status);
CREATE INDEX idx_inventory_adjustments_product ON inventory_adjustments(product_id);
CREATE INDEX idx_inventory_adjustments_location ON inventory_adjustments(location_id);
CREATE INDEX idx_inventory_adjustments_status ON inventory_adjustments(approval_status);
CREATE INDEX idx_inventory_adjustments_cycle_count ON inventory_adjustments(cycle_count_id);
