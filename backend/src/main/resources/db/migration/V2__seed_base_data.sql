-- 演示用基础数据：常用中药饮片、中成药与西药，以及初始批次库存。
-- 生产环境可清空本文件内容，或通过「药品字典」页面自行维护。

INSERT INTO drug (code, name, pinyin, category, spec, unit, price, cost_price, stock_warn, status, created_at, updated_at) VALUES
('H001', '黄芪', 'HQ', 'HERB', '饮片', 'g', 0.35, 0.25, 2000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H002', '当归', 'DG', 'HERB', '饮片', 'g', 1.20, 0.90, 1500, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H003', '白芍', 'BS', 'HERB', '饮片', 'g', 0.80, 0.60, 1500, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H004', '川芎', 'CX', 'HERB', '饮片', 'g', 0.60, 0.45, 1000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H005', '熟地黄', 'SDH', 'HERB', '饮片', 'g', 0.90, 0.70, 1200, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H006', '党参', 'DS', 'HERB', '饮片', 'g', 1.50, 1.10, 1500, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H007', '白术', 'BZ', 'HERB', '饮片', 'g', 0.95, 0.70, 1200, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H008', '茯苓', 'FL', 'HERB', '饮片', 'g', 0.70, 0.50, 1500, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H009', '甘草', 'GC', 'HERB', '饮片', 'g', 0.45, 0.30, 1500, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H010', '陈皮', 'CP', 'HERB', '饮片', 'g', 0.55, 0.40, 800, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H011', '法半夏', 'FBX', 'HERB', '饮片', 'g', 1.10, 0.85, 600, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H012', '柴胡', 'CH', 'HERB', '饮片', 'g', 1.30, 1.00, 800, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H013', '黄芩', 'HQN', 'HERB', '饮片', 'g', 0.85, 0.65, 800, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H014', '桂枝', 'GZ', 'HERB', '饮片', 'g', 0.50, 0.35, 800, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H015', '生姜', 'SJ', 'HERB', '饮片', 'g', 0.30, 0.20, 500, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H016', '大枣', 'DZ', 'HERB', '饮片', 'g', 0.40, 0.30, 500, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H017', '枸杞子', 'GQZ', 'HERB', '饮片', 'g', 1.60, 1.20, 1000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H018', '山药', 'SY', 'HERB', '饮片', 'g', 0.90, 0.70, 1000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H019', '丹参', 'DSG', 'HERB', '饮片', 'g', 0.95, 0.75, 1000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('H020', '酸枣仁', 'SZR', 'HERB', '饮片', 'g', 3.20, 2.60, 500, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('P001', '六味地黄丸', 'LWDHW', 'PATENT', '200丸/瓶', '瓶', 28.00, 20.00, 20, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('P002', '逍遥丸', 'XYW', 'PATENT', '200丸/瓶', '瓶', 22.00, 15.00, 20, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('P003', '补中益气丸', 'BZYQW', 'PATENT', '200丸/瓶', '瓶', 26.00, 18.00, 20, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('P004', '藿香正气水', 'HXZQS', 'PATENT', '10ml*10支', '盒', 15.00, 10.00, 30, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('W001', '布洛芬缓释胶囊', 'BLFHSJN', 'WESTERN', '0.3g*20粒', '盒', 18.50, 12.00, 20, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('W002', '阿莫西林胶囊', 'AMXLJN', 'WESTERN', '0.25g*24粒', '盒', 12.80, 8.00, 20, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('W003', '维生素C片', 'WSSCP', 'WESTERN', '100片/瓶', '瓶', 9.90, 6.00, 20, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 饮片初始批次（5000g / 批）
INSERT INTO drug_batch (drug_id, batch_no, production_date, expiry_date, quantity, cost_price, created_at, updated_at)
SELECT d.id, 'Y2026001', DATE '2026-01-10', DATE '2027-12-31', 5000, d.cost_price, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM drug d WHERE d.category = 'HERB';

-- 成药与西药初始批次（100 单位 / 批）
INSERT INTO drug_batch (drug_id, batch_no, production_date, expiry_date, quantity, cost_price, created_at, updated_at)
SELECT d.id, 'C2026001', DATE '2026-02-01', DATE '2027-06-30', 100, d.cost_price, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM drug d WHERE d.category IN ('PATENT', 'WESTERN');
