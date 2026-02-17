-- 뉴스 (테마별 뉴스 수집 → AI 분석 파이프라인)
CREATE TABLE theme_news (
    news_id       BIGSERIAL PRIMARY KEY,
    theme_id      BIGINT REFERENCES theme(theme_id),
    title         VARCHAR(500) NOT NULL,
    summary       TEXT,
    source_url    VARCHAR(1000) NOT NULL,
    published_at  TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(source_url)
);
CREATE INDEX idx_theme_news_theme_published ON theme_news (theme_id, published_at DESC);

-- 테마 점수 히스토리 (스코어링 엔진 → Redis 원본)
CREATE TABLE theme_score (
    time             TIMESTAMPTZ NOT NULL,
    theme_id         BIGINT NOT NULL,
    score            DECIMAL(10,4) NOT NULL DEFAULT 0,
    avg_change_rate  DECIMAL(8,2),
    total_volume     BIGINT DEFAULT 0,
    trading_value    BIGINT DEFAULT 0,
    cohesion         DECIMAL(6,4) DEFAULT 0,
    stock_count      INTEGER DEFAULT 0
);
SELECT create_hypertable('theme_score', 'time');

-- 기업 프로필 (DART 기업개황 + KIS 재무지표)
CREATE TABLE stock_profile (
    stock_code     VARCHAR(10) PRIMARY KEY REFERENCES stock(stock_code),
    sector         VARCHAR(100),
    ceo_name       VARCHAR(100),
    established_at DATE,
    main_business  TEXT,
    per            DECIMAL(10,2),
    pbr            DECIMAL(10,2),
    eps            BIGINT,
    bps            BIGINT,
    dividend_yield DECIMAL(6,2),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
