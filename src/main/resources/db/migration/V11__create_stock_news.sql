-- 뉴스-종목 N:M 매핑 테이블
CREATE TABLE stock_news (
    news_id      BIGINT REFERENCES theme_news(news_id),
    stock_code   VARCHAR(10) REFERENCES stock(stock_code),
    match_type   VARCHAR(30) NOT NULL,
    PRIMARY KEY (news_id, stock_code)
);
CREATE INDEX idx_stock_news_stock ON stock_news (stock_code, news_id DESC);

-- theme_news에 sentiment_score 컬럼 추가
ALTER TABLE theme_news ADD COLUMN sentiment_score DECIMAL(5,4);
