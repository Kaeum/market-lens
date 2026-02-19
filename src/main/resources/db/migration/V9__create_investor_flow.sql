CREATE TABLE investor_flow (
    time             TIMESTAMPTZ  NOT NULL,
    stock_code       VARCHAR(10)  NOT NULL REFERENCES stock(stock_code),
    investor_type    VARCHAR(20)  NOT NULL,
    sell_volume      BIGINT       NOT NULL DEFAULT 0,
    buy_volume       BIGINT       NOT NULL DEFAULT 0,
    net_volume       BIGINT       NOT NULL DEFAULT 0,
    sell_amount      BIGINT       NOT NULL DEFAULT 0,
    buy_amount       BIGINT       NOT NULL DEFAULT 0,
    net_amount       BIGINT       NOT NULL DEFAULT 0,
    collected_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

SELECT create_hypertable('investor_flow', 'time');

CREATE UNIQUE INDEX idx_investor_flow_unique ON investor_flow (stock_code, time, investor_type);
CREATE INDEX idx_investor_flow_code_time ON investor_flow (stock_code, time DESC);
CREATE INDEX idx_investor_flow_time_type ON investor_flow (time DESC, investor_type);
