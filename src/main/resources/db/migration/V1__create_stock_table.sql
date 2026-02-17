CREATE TABLE stock (
    stock_code  VARCHAR(10) PRIMARY KEY,
    stock_name  VARCHAR(100) NOT NULL,
    market      VARCHAR(10)  NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_stock_market ON stock (market);
CREATE INDEX idx_stock_is_active ON stock (is_active);
