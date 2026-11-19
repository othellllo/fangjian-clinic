-- 第六版：处方明细的结构化用法（给药途径 + 频次）
ALTER TABLE prescription_item ADD COLUMN usage_route VARCHAR(30);
ALTER TABLE prescription_item ADD COLUMN usage_route_code VARCHAR(20);
ALTER TABLE prescription_item ADD COLUMN usage_frequency VARCHAR(50);
ALTER TABLE prescription_item ADD COLUMN usage_frequency_code VARCHAR(20);
