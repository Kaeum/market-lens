CREATE TABLE stock_price (
    time          TIMESTAMPTZ NOT NULL,
    stock_code    VARCHAR(10) NOT NULL,
    current_price BIGINT NOT NULL,
    change_rate   DECIMAL(8,2),
    volume        BIGINT NOT NULL DEFAULT 0,
    market_cap    BIGINT,
    open_price    BIGINT,
    high_price    BIGINT,
    low_price     BIGINT
);

SELECT create_hypertable('stock_price', 'time');

CREATE INDEX idx_stock_price_code_time ON stock_price (stock_code, time DESC);

CREATE TABLE stock_price_snapshot (
    stock_code    VARCHAR(10) PRIMARY KEY REFERENCES stock(stock_code),
    current_price BIGINT NOT NULL,
    change_rate   DECIMAL(8,2),
    volume        BIGINT NOT NULL DEFAULT 0,
    market_cap    BIGINT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
