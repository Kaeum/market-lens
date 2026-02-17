-- Seed themes (기존 sector 데이터 → theme으로 이전)
INSERT INTO theme (theme_name, theme_name_kr, display_order) VALUES
('semiconductor', '반도체', 1),
('battery', '2차전지', 2),
('bio', '바이오', 3),
('auto', '자동차', 4),
('it', 'IT/소프트웨어', 5),
('finance', '금융', 6),
('chemical', '화학', 7),
('steel', '철강', 8),
('construction', '건설', 9),
('entertainment', '엔터/미디어', 10),
('retail', '유통', 11),
('food', '음식료', 12),
('telecom', '통신', 13),
('shipbuilding', '조선', 14),
('defense', '방산', 15);

-- Seed major stocks
INSERT INTO stock (stock_code, stock_name, market) VALUES
('005930', '삼성전자', 'KOSPI'),
('000660', 'SK하이닉스', 'KOSPI'),
('042700', '한미반도체', 'KOSPI'),
('373220', 'LG에너지솔루션', 'KOSPI'),
('006400', '삼성SDI', 'KOSPI'),
('051910', 'LG화학', 'KOSPI'),
('207940', '삼성바이오로직스', 'KOSPI'),
('068270', '셀트리온', 'KOSPI'),
('326030', 'SK바이오팜', 'KOSPI'),
('005380', '현대차', 'KOSPI'),
('000270', '기아', 'KOSPI'),
('012330', '현대모비스', 'KOSPI'),
('035720', '카카오', 'KOSPI'),
('035420', 'NAVER', 'KOSPI'),
('259960', '크래프톤', 'KOSPI'),
('105560', 'KB금융', 'KOSPI'),
('055550', '신한지주', 'KOSPI'),
('086790', '하나금융지주', 'KOSPI'),
('096770', 'SK이노베이션', 'KOSPI'),
('010950', 'S-Oil', 'KOSPI'),
('005490', 'POSCO홀딩스', 'KOSPI'),
('004020', '현대제철', 'KOSPI'),
('000720', '현대건설', 'KOSPI'),
('047040', '대우건설', 'KOSPI'),
('352820', '하이브', 'KOSPI'),
('041510', 'SM', 'KOSPI'),
('069960', '현대백화점', 'KOSPI'),
('004170', '신세계', 'KOSPI'),
('097950', 'CJ제일제당', 'KOSPI'),
('271560', '오리온', 'KOSPI'),
('017670', 'SK텔레콤', 'KOSPI'),
('030200', 'KT', 'KOSPI'),
('009540', 'HD한국조선해양', 'KOSPI'),
('010140', '삼성중공업', 'KOSPI'),
('012450', '한화에어로스페이스', 'KOSPI'),
('047810', '한국항공우주', 'KOSPI');

-- Map stocks to themes
INSERT INTO theme_stock (theme_id, stock_code)
SELECT t.theme_id, s.stock_code FROM theme t, stock s
WHERE (t.theme_name = 'semiconductor' AND s.stock_code IN ('005930','000660','042700'))
   OR (t.theme_name = 'battery' AND s.stock_code IN ('373220','006400','051910'))
   OR (t.theme_name = 'bio' AND s.stock_code IN ('207940','068270','326030'))
   OR (t.theme_name = 'auto' AND s.stock_code IN ('005380','000270','012330'))
   OR (t.theme_name = 'it' AND s.stock_code IN ('035720','035420','259960'))
   OR (t.theme_name = 'finance' AND s.stock_code IN ('105560','055550','086790'))
   OR (t.theme_name = 'chemical' AND s.stock_code IN ('096770','010950'))
   OR (t.theme_name = 'steel' AND s.stock_code IN ('005490','004020'))
   OR (t.theme_name = 'construction' AND s.stock_code IN ('000720','047040'))
   OR (t.theme_name = 'entertainment' AND s.stock_code IN ('352820','041510'))
   OR (t.theme_name = 'retail' AND s.stock_code IN ('069960','004170'))
   OR (t.theme_name = 'food' AND s.stock_code IN ('097950','271560'))
   OR (t.theme_name = 'telecom' AND s.stock_code IN ('017670','030200'))
   OR (t.theme_name = 'shipbuilding' AND s.stock_code IN ('009540','010140'))
   OR (t.theme_name = 'defense' AND s.stock_code IN ('012450','047810'));
