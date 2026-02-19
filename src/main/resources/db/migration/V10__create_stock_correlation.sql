CREATE TABLE stock_correlation (
    stock_code_a   VARCHAR(10)   NOT NULL REFERENCES stock(stock_code),
    stock_code_b   VARCHAR(10)   NOT NULL REFERENCES stock(stock_code),
    correlation    DECIMAL(6,4)  NOT NULL,
    co_rise_rate   DECIMAL(6,4)  NOT NULL,
    window_days    SMALLINT      NOT NULL,
    calculated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (stock_code_a, stock_code_b),
    CONSTRAINT chk_code_order CHECK (stock_code_a < stock_code_b)
);

CREATE INDEX idx_stock_correlation_a ON stock_correlation (stock_code_a);
CREATE INDEX idx_stock_correlation_b ON stock_correlation (stock_code_b);
CREATE INDEX idx_stock_correlation_high ON stock_correlation (correlation DESC) WHERE correlation >= 0.7;
