-- 第四版：中医/西医病历分开、术语联想、用药安全规则、特殊煎法

-- 病历：类型与西医字段
ALTER TABLE medical_record ADD COLUMN record_type VARCHAR(20) NOT NULL DEFAULT 'TCM';
ALTER TABLE medical_record ADD COLUMN tcm_disease VARCHAR(200);
ALTER TABLE medical_record ADD COLUMN physical_exam VARCHAR(2000);
ALTER TABLE medical_record ADD COLUMN auxiliary_exam VARCHAR(1000);
ALTER TABLE medical_record ADD COLUMN western_diagnosis VARCHAR(500);
ALTER TABLE medical_record ADD COLUMN icd_code VARCHAR(20);

-- 患者妊娠状态：0 否 / 1 是 / 2 未知
ALTER TABLE patient ADD COLUMN pregnant INT NOT NULL DEFAULT 2;

-- 药品：特殊煎法、剂量范围、妊娠与毒性标记
ALTER TABLE drug ADD COLUMN special_handling VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE drug ADD COLUMN dose_min DECIMAL(10, 2);
ALTER TABLE drug ADD COLUMN dose_max DECIMAL(10, 2);
ALTER TABLE drug ADD COLUMN pregnancy_level VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE drug ADD COLUMN toxicity_level VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE drug ADD COLUMN functions VARCHAR(300);

-- 术语库（联想输入）
CREATE TABLE medical_term (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    category    VARCHAR(30)  NOT NULL,
    term        VARCHAR(200) NOT NULL,
    pinyin      VARCHAR(100),
    note        VARCHAR(300),
    usage_count INT NOT NULL DEFAULT 0,
    source      VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL,
    CONSTRAINT uk_medical_term UNIQUE (category, term)
);
CREATE INDEX idx_medical_term_category ON medical_term (category);
CREATE INDEX idx_medical_term_pinyin ON medical_term (pinyin);

-- 用药安全规则（十八反、十九畏、妊娠禁忌）
CREATE TABLE drug_interaction_rule (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_type VARCHAR(30) NOT NULL,
    item_a    VARCHAR(50) NOT NULL,
    item_b    VARCHAR(50),
    severity  VARCHAR(20) NOT NULL,
    advice    VARCHAR(500),
    source    VARCHAR(100),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
CREATE INDEX idx_interaction_rule_a ON drug_interaction_rule (item_a);
CREATE INDEX idx_interaction_rule_type ON drug_interaction_rule (rule_type);

-- ============ 初始术语库 ============
INSERT INTO medical_term (category, term, pinyin, note, usage_count, source, created_at, updated_at) VALUES
('SYMPTOM', '乏力', 'FL', '气虚常见', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYMPTOM', '纳差', 'NC', '脾胃虚弱', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYMPTOM', '失眠', 'SM', '心神不宁', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYMPTOM', '头晕', 'TY', '', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYMPTOM', '腹胀', 'FZ', '气滞', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYMPTOM', '便溏', 'BT', '脾虚湿盛', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYMPTOM', '口干', 'KG', '阴虚或热盛', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYMPTOM', '咳嗽', 'KS', '肺失宣降', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYMPTOM', '心悸', 'XJ', '', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYMPTOM', '畏寒', 'WH', '阳虚', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYNDROME', '脾胃气虚证', 'PWQXZ', '健脾益气', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYNDROME', '肝郁脾虚证', 'GYBXZ', '疏肝健脾', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYNDROME', '心脾两虚证', 'XPLXZ', '补益心脾', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYNDROME', '肝肾阴虚证', 'GSLYZ', '滋补肝肾', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYNDROME', '痰湿内阻证', 'TSNZZ', '化痰祛湿', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYNDROME', '气血两虚证', 'QXLXZ', '益气养血', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYNDROME', '风寒束表证', 'FHBSZ', '疏风散寒', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('SYNDROME', '风热犯肺证', 'FRFFZ', '疏风清热', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('TONGUE', '舌淡红，苔薄白', 'SDHTBB', '正常或表证', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('TONGUE', '舌淡胖，边有齿痕', 'SDPBYCH', '脾虚湿盛', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('TONGUE', '舌红，苔黄腻', 'SHTBHN', '湿热', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('TONGUE', '舌暗红，少苔', 'SAHST', '阴虚或血瘀', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PULSE', '脉细弱', 'MXR', '气血不足', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PULSE', '脉弦', 'MX', '肝郁', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PULSE', '脉滑', 'MH', '痰湿', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PULSE', '脉沉迟', 'MCX', '阳虚', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PULSE', '脉细数', 'MXS', '阴虚', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('TREATMENT', '健脾益气，和胃消食', 'JJPYHWXS', '', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('TREATMENT', '疏肝解郁，健脾养血', 'SGJYJPYX', '', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('TREATMENT', '滋阴补肾，养心安神', 'ZYBSYAXS', '', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('TREATMENT', '化痰祛湿，理气和胃', 'HTQSLQHW', '', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('TREATMENT', '疏风散寒，解表化湿', 'SFSAJBHS', '', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FORMULA', '四君子汤', 'SJZT', '益气健脾', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FORMULA', '六君子汤', 'LJZT', '益气健脾化痰', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FORMULA', '逍遥散', 'XYS', '疏肝解郁', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FORMULA', '六味地黄丸', 'LWDHW', '滋补肝肾', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FORMULA', '归脾汤', 'GPT', '补益心脾', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('FORMULA', '半夏白术天麻汤', 'BXBTFMT', '化痰息风', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DIAGNOSIS', '慢性胃炎', 'MXWY', 'ICD-10 K29.5', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DIAGNOSIS', '失眠症', 'SMZ', 'ICD-10 F51.0', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DIAGNOSIS', '高血压病', 'GXYB', 'ICD-10 I10', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DIAGNOSIS', '上呼吸道感染', 'SXHDGR', 'ICD-10 J06.9', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('DIAGNOSIS', '2型糖尿病', '2XTNB', 'ICD-10 E11.9', 0, 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============ 十八反（BLOCK）============
INSERT INTO drug_interaction_rule (rule_type, item_a, item_b, severity, advice, source, created_at, updated_at) VALUES
('EIGHTEEN_ANTAGONISM', '附子', '半夏', 'BLOCK', '十八反：乌头（附子）反半夏，原则上不宜同用，确需使用需医师填写理由', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '附子', '瓜蒌', 'BLOCK', '十八反：乌头（附子）反瓜蒌', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '附子', '浙贝母', 'BLOCK', '十八反：乌头（附子）反贝母', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '附子', '川贝母', 'BLOCK', '十八反：乌头（附子）反贝母', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '附子', '白及', 'BLOCK', '十八反：乌头（附子）反白及', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '附子', '白蔹', 'BLOCK', '十八反：乌头（附子）反白蔹', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '附子', '天花粉', 'BLOCK', '十八反：乌头（附子）反天花粉', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '川乌', '半夏', 'BLOCK', '十八反：川乌反半夏', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '草乌', '半夏', 'BLOCK', '十八反：草乌反半夏', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '甘草', '甘遂', 'BLOCK', '十八反：甘草反甘遂', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '甘草', '京大戟', 'BLOCK', '十八反：甘草反大戟', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '甘草', '海藻', 'BLOCK', '十八反：甘草反海藻', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '甘草', '芫花', 'BLOCK', '十八反：甘草反芫花', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '藜芦', '人参', 'BLOCK', '十八反：藜芦反人参', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '藜芦', '党参', 'BLOCK', '十八反：藜芦反党参', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '藜芦', '丹参', 'BLOCK', '十八反：藜芦反丹参', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '藜芦', '玄参', 'BLOCK', '十八反：藜芦反玄参', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '藜芦', '细辛', 'BLOCK', '十八反：藜芦反细辛', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '藜芦', '白芍', 'BLOCK', '十八反：藜芦反白芍', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('EIGHTEEN_ANTAGONISM', '藜芦', '赤芍', 'BLOCK', '十八反：藜芦反赤芍', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============ 十九畏（BLOCK）============
INSERT INTO drug_interaction_rule (rule_type, item_a, item_b, severity, advice, source, created_at, updated_at) VALUES
('NINETEEN_INCOMPATIBILITY', '硫黄', '朴硝', 'BLOCK', '十九畏：硫黄畏朴硝（芒硝）', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NINETEEN_INCOMPATIBILITY', '水银', '砒霜', 'BLOCK', '十九畏：水银畏砒霜', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NINETEEN_INCOMPATIBILITY', '狼毒', '密陀僧', 'BLOCK', '十九畏：狼毒畏密陀僧', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NINETEEN_INCOMPATIBILITY', '巴豆', '牵牛子', 'BLOCK', '十九畏：巴豆畏牵牛', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NINETEEN_INCOMPATIBILITY', '丁香', '郁金', 'BLOCK', '十九畏：丁香畏郁金', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NINETEEN_INCOMPATIBILITY', '牙硝', '三棱', 'BLOCK', '十九畏：牙硝畏三棱', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NINETEEN_INCOMPATIBILITY', '川乌', '犀角', 'BLOCK', '十九畏：川乌草乌畏犀角', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NINETEEN_INCOMPATIBILITY', '人参', '五灵脂', 'BLOCK', '十九畏：人参畏五灵脂', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('NINETEEN_INCOMPATIBILITY', '肉桂', '赤石脂', 'BLOCK', '十九畏：官桂（肉桂）畏赤石脂', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============ 妊娠禁忌 ============
INSERT INTO drug_interaction_rule (rule_type, item_a, item_b, severity, advice, source, created_at, updated_at) VALUES
('PREGNANCY_FORBIDDEN', '巴豆', NULL, 'BLOCK', '妊娠禁用（毒性强、峻猛）', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_FORBIDDEN', '牵牛子', NULL, 'BLOCK', '妊娠禁用', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_FORBIDDEN', '大戟', NULL, 'BLOCK', '妊娠禁用', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_FORBIDDEN', '商陆', NULL, 'BLOCK', '妊娠禁用', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_FORBIDDEN', '麝香', NULL, 'BLOCK', '妊娠禁用', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_FORBIDDEN', '三棱', NULL, 'BLOCK', '妊娠禁用', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_FORBIDDEN', '莪术', NULL, 'BLOCK', '妊娠禁用', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_FORBIDDEN', '水蛭', NULL, 'BLOCK', '妊娠禁用', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_FORBIDDEN', '斑蝥', NULL, 'BLOCK', '妊娠禁用', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_FORBIDDEN', '马钱子', NULL, 'BLOCK', '妊娠禁用', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_FORBIDDEN', '川乌', NULL, 'BLOCK', '妊娠禁用', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_FORBIDDEN', '雄黄', NULL, 'BLOCK', '妊娠禁用', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_CAUTION', '桃仁', NULL, 'WARN', '妊娠慎用（活血）', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_CAUTION', '红花', NULL, 'WARN', '妊娠慎用（活血）', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_CAUTION', '牛膝', NULL, 'WARN', '妊娠慎用', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_CAUTION', '大黄', NULL, 'WARN', '妊娠慎用（攻下）', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_CAUTION', '枳实', NULL, 'WARN', '妊娠慎用（行气）', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_CAUTION', '肉桂', NULL, 'WARN', '妊娠慎用（辛热）', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('PREGNANCY_CAUTION', '木通', NULL, 'WARN', '妊娠慎用（滑利）', '教材/广东中医药局', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- ============ 特殊煎法标注（常用饮片）============
UPDATE drug SET special_handling = 'PRE_DECOCT', dose_min = 3, dose_max = 15, functions = '回阳救逆，温补脾肾' WHERE name = '附子';
UPDATE drug SET special_handling = 'POST_ADD', dose_min = 3, dose_max = 6, functions = '疏散风热，清利头目' WHERE name = '薄荷';
UPDATE drug SET special_handling = 'POST_ADD', dose_min = 3, dose_max = 6, functions = '化湿行气，温中止泻' WHERE name = '砂仁';
UPDATE drug SET special_handling = 'WRAP_DECOCT', dose_min = 3, dose_max = 9, functions = '降气化痰，降逆止呕' WHERE name = '旋覆花';
UPDATE drug SET special_handling = 'WRAP_DECOCT', dose_min = 9, dose_max = 15, functions = '清热利尿，渗湿通淋' WHERE name = '车前子';
UPDATE drug SET special_handling = 'MELT', dose_min = 3, dose_max = 9, functions = '补血滋阴，润燥止血' WHERE name = '阿胶';
UPDATE drug SET special_handling = 'TAKE_WITH', dose_min = 1, dose_max = 3, functions = '化瘀止血，活血定痛' WHERE name = '三七';
UPDATE drug SET special_handling = 'SEPARATE_DECOCT', dose_min = 3, dose_max = 9, functions = '大补元气，复脉固脱' WHERE name = '人参';
UPDATE drug SET dose_min = 3, dose_max = 10, functions = '发汗解表，温通经脉' WHERE name = '桂枝';
UPDATE drug SET dose_min = 3, dose_max = 10, functions = '补气升阳，固表止汗' WHERE name = '黄芪';
UPDATE drug SET dose_min = 6, dose_max = 12, functions = '补血活血，调经止痛' WHERE name = '当归';
UPDATE drug SET dose_min = 6, dose_max = 15, functions = '补脾益气，清热解毒' WHERE name = '甘草';
UPDATE drug SET dose_min = 3, dose_max = 10, functions = '补脾养胃，生津益肺' WHERE name = '山药';
UPDATE drug SET dose_min = 5, dose_max = 10, functions = '理气健脾，燥湿化痰' WHERE name = '陈皮';
UPDATE drug SET dose_min = 3, dose_max = 10, functions = '健脾益气，燥湿利水' WHERE name = '白术';
UPDATE drug SET dose_min = 10, dose_max = 15, functions = '利水渗湿，健脾宁心' WHERE name = '茯苓';
UPDATE drug SET dose_min = 3, dose_max = 10, functions = '活血行气，祛风止痛' WHERE name = '川芎';
UPDATE drug SET dose_min = 6, dose_max = 15, functions = '养血调经，敛阴止汗' WHERE name = '白芍';
UPDATE drug SET dose_min = 3, dose_max = 10, functions = '清热解毒，凉血安胎' WHERE name = '黄芩';
UPDATE drug SET dose_min = 3, dose_max = 9, functions = '疏散退热，疏肝解郁' WHERE name = '柴胡';

-- 妊娠标记（按上方妊娠规则同步）
UPDATE drug SET pregnancy_level = 'FORBIDDEN' WHERE name IN ('川乌', '附子');
UPDATE drug SET pregnancy_level = 'CAUTION' WHERE name IN ('桃仁', '红花', '牛膝', '大黄', '枳实', '肉桂', '木通');
-- 毒性标记
UPDATE drug SET toxicity_level = 'TOXIC' WHERE name IN ('附子', '川乌', '草乌', '马钱子');
UPDATE drug SET toxicity_level = 'MILD' WHERE name IN ('细辛', '半夏', '苦杏仁');

-- 补充几味常用于验证配伍规则的饮片（默认启用，库存由入库操作维护）
INSERT INTO drug (code, name, pinyin, category, spec, unit, price, cost_price, stock_warn, status, created_at, updated_at)
SELECT 'H021', '附子', 'FZ', 'HERB', '饮片', 'g', 1.20, 0.90, 500, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM drug WHERE code = 'H021');
INSERT INTO drug (code, name, pinyin, category, spec, unit, price, cost_price, stock_warn, status, created_at, updated_at)
SELECT 'H022', '海藻', 'HZ', 'HERB', '饮片', 'g', 0.60, 0.40, 300, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM drug WHERE code = 'H022');
INSERT INTO drug (code, name, pinyin, category, spec, unit, price, cost_price, stock_warn, status, created_at, updated_at)
SELECT 'H023', '丁香', 'DX', 'HERB', '饮片', 'g', 0.80, 0.55, 300, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM drug WHERE code = 'H023');
INSERT INTO drug (code, name, pinyin, category, spec, unit, price, cost_price, stock_warn, status, created_at, updated_at)
SELECT 'H024', '郁金', 'YJ', 'HERB', '饮片', 'g', 0.90, 0.65, 300, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM drug WHERE code = 'H024');

UPDATE drug SET special_handling = 'PRE_DECOCT', dose_min = 3, dose_max = 15,
       pregnancy_level = 'FORBIDDEN', toxicity_level = 'TOXIC', functions = '回阳救逆，温补脾肾'
WHERE name = '附子';
UPDATE drug SET dose_min = 6, dose_max = 12, functions = '消痰软坚，利水消肿' WHERE name = '海藻';
UPDATE drug SET dose_min = 1, dose_max = 3, functions = '温中降逆，补肾助阳' WHERE name = '丁香';
UPDATE drug SET dose_min = 3, dose_max = 10, functions = '活血止痛，行气解郁' WHERE name = '郁金';

-- 妊娠禁忌补充：附子/草乌（歌诀「乌头附子配天雄」，按禁用处理）
INSERT INTO drug_interaction_rule (rule_type, item_a, item_b, severity, advice, source, created_at, updated_at)
SELECT 'PREGNANCY_FORBIDDEN', '附子', NULL, 'BLOCK', '妊娠禁用（乌头类，含乌头碱）', '教材/妊娠禁忌歌', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM drug_interaction_rule WHERE rule_type = 'PREGNANCY_FORBIDDEN' AND item_a = '附子');
INSERT INTO drug_interaction_rule (rule_type, item_a, item_b, severity, advice, source, created_at, updated_at)
SELECT 'PREGNANCY_FORBIDDEN', '草乌', NULL, 'BLOCK', '妊娠禁用（乌头类）', '教材/妊娠禁忌歌', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM drug_interaction_rule WHERE rule_type = 'PREGNANCY_FORBIDDEN' AND item_a = '草乌');
