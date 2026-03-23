-- stock_transfers 테이블 생성
CREATE TABLE stock_transfers (
    transfer_id UUID PRIMARY KEY,
    from_location_id UUID NOT NULL,
    to_location_id UUID NOT NULL,
    product_id UUID NOT NULL,
    lot_number VARCHAR(50),
    quantity INT NOT NULL CHECK (quantity > 0),
    transfer_status VARCHAR(50) NOT NULL,
    requested_by VARCHAR(100),
    approved_by VARCHAR(100),
    transfer_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_transfer_from_location FOREIGN KEY (from_location_id) REFERENCES locations(location_id),
    CONSTRAINT fk_stock_transfer_to_location FOREIGN KEY (to_location_id) REFERENCES locations(location_id),
    CONSTRAINT fk_stock_transfer_product FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE INDEX idx_stock_transfers_from_location ON stock_transfers(from_location_id);
CREATE INDEX idx_stock_transfers_to_location ON stock_transfers(to_location_id);
CREATE INDEX idx_stock_transfers_product ON stock_transfers(product_id);
CREATE INDEX idx_stock_transfers_status ON stock_transfers(transfer_status);
CREATE INDEX idx_stock_transfers_created_at ON stock_transfers(created_at);
