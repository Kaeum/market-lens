-- flyway:executeInTransaction=false

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_stock_price_unique
    ON stock_price (stock_code, time);
