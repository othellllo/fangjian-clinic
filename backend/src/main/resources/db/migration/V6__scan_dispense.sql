-- 第五版：扫码发药 / 扫码出库
-- 1) 药品增加条形码（扫码枪扫的码），为空时回退用药品编码匹配
ALTER TABLE drug ADD COLUMN barcode VARCHAR(64);
CREATE INDEX idx_drug_barcode ON drug (barcode);

-- 2) 扫码发药进度（每次扫码一条，发药完成后汇总为处方状态）
CREATE TABLE dispense_scan (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    prescription_id BIGINT      NOT NULL,
    drug_id         BIGINT      NOT NULL,
    drug_name       VARCHAR(100),
    barcode         VARCHAR(64),
    trace_code      VARCHAR(64),
    quantity        DECIMAL(12, 3) NOT NULL,
    operator_id     BIGINT,
    operator_name   VARCHAR(50),
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL
);
CREATE INDEX idx_dispense_scan_prescription ON dispense_scan (prescription_id, drug_id);
