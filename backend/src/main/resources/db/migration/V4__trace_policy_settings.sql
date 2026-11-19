-- 第三版：药品追溯码、医保政策库、药品库存上下限与追溯标识

ALTER TABLE drug ADD COLUMN stock_max DECIMAL(12, 3) DEFAULT 0;
ALTER TABLE drug ADD COLUMN need_trace INT NOT NULL DEFAULT 0;

-- 按公告口径：中药饮片、院内制剂暂不强制采集追溯码；中成药/西药/耗材需要
UPDATE drug SET need_trace = 1 WHERE category IN ('PATENT', 'WESTERN', 'MATERIAL');

CREATE TABLE drug_trace (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    drug_id        BIGINT       NOT NULL,
    drug_name      VARCHAR(100),
    batch_no       VARCHAR(50),
    code           VARCHAR(64)  NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    prescription_id BIGINT,
    patient_id     BIGINT,
    patient_name   VARCHAR(50),
    bind_time      DATETIME,
    dispense_time  DATETIME,
    return_time    DATETIME,
    operator_id    BIGINT,
    operator_name  VARCHAR(50),
    upload_status  VARCHAR(20) DEFAULT 'PENDING',
    upload_time    DATETIME,
    created_at     DATETIME NOT NULL,
    updated_at     DATETIME NOT NULL,
    CONSTRAINT uk_drug_trace_code UNIQUE (code)
);
CREATE INDEX idx_drug_trace_drug ON drug_trace (drug_id, batch_no);
CREATE INDEX idx_drug_trace_status ON drug_trace (status);
CREATE INDEX idx_drug_trace_prescription ON drug_trace (prescription_id);

CREATE TABLE insurance_policy (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    issuer         VARCHAR(100),
    doc_no         VARCHAR(100),
    category       VARCHAR(20)  NOT NULL,
    publish_date   DATE,
    effective_date DATE,
    expire_date    DATE,
    summary        VARCHAR(1000),
    content        VARCHAR(20000),
    source_url     VARCHAR(500),
    status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at     DATETIME NOT NULL,
    updated_at     DATETIME NOT NULL
);
CREATE INDEX idx_policy_category ON insurance_policy (category);
CREATE INDEX idx_policy_effective ON insurance_policy (effective_date, expire_date);
