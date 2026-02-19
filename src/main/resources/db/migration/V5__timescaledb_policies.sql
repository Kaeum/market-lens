-- flyway:executeInTransaction=false

-- 7일 이상 된 청크 압축
ALTER TABLE stock_price SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'stock_code',
    timescaledb.compress_orderby = 'time DESC'
);
SELECT add_compression_policy('stock_price', INTERVAL '7 days');

-- 일봉 연속 집계 (실시간 조회 최적화)
CREATE MATERIALIZED VIEW stock_price_daily
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 day', time) AS bucket,
    stock_code,
    first(current_price, time) AS open_price,
    max(high_price) AS high_price,
    min(low_price) AS low_price,
    last(current_price, time) AS close_price,
    sum(volume) AS total_volume,
    last(change_rate, time) AS change_rate
FROM stock_price
GROUP BY bucket, stock_code;

SELECT add_continuous_aggregate_policy('stock_price_daily',
    start_offset => INTERVAL '3 days',
    end_offset => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');
