CREATE TABLE theme (
    theme_id       BIGSERIAL PRIMARY KEY,
    theme_name     VARCHAR(50) NOT NULL UNIQUE,
    theme_name_kr  VARCHAR(50) NOT NULL,
    display_order  INTEGER NOT NULL DEFAULT 0,
    is_dynamic     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE theme_stock (
    theme_id    BIGINT REFERENCES theme(theme_id),
    stock_code  VARCHAR(10) REFERENCES stock(stock_code),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (theme_id, stock_code)
);

CREATE INDEX idx_theme_stock_stock_code ON theme_stock (stock_code);
