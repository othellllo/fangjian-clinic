-- 医馆 HIS 初始表结构
-- 说明：DDL 同时兼容 MySQL 8（生产）与 H2 的 MySQL 模式（开发/演示），
-- 因此不使用 ENGINE、COMMENT、ON UPDATE CURRENT_TIMESTAMP 等方言特性。

CREATE TABLE sys_user (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    real_name     VARCHAR(50)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    title         VARCHAR(50),
    dept          VARCHAR(50),
    phone         VARCHAR(20),
    status        INT          NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE sys_config (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key   VARCHAR(64)  NOT NULL,
    config_value VARCHAR(500),
    remark       VARCHAR(200),
    created_at   DATETIME NOT NULL,
    updated_at   DATETIME NOT NULL,
    CONSTRAINT uk_sys_config_key UNIQUE (config_key)
);

CREATE TABLE patient (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_no      VARCHAR(32) NOT NULL,
    name            VARCHAR(50) NOT NULL,
    gender          VARCHAR(10) NOT NULL,
    birth_date      DATE,
    phone           VARCHAR(20),
    id_card         VARCHAR(32),
    address         VARCHAR(200),
    allergy         VARCHAR(500),
    chronic_history VARCHAR(500),
    member_id       BIGINT,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    CONSTRAINT uk_patient_no UNIQUE (patient_no)
);
CREATE INDEX idx_patient_phone ON patient (phone);
CREATE INDEX idx_patient_name ON patient (name);

CREATE TABLE visit (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_no      VARCHAR(32) NOT NULL,
    patient_id    BIGINT      NOT NULL,
    doctor_id     BIGINT      NOT NULL,
    type          VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    queue_no      INT,
    register_fee  DECIMAL(10, 2) DEFAULT 0,
    register_time DATETIME    NOT NULL,
    start_time    DATETIME,
    end_time      DATETIME,
    complaint     VARCHAR(500),
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL,
    CONSTRAINT uk_visit_no UNIQUE (visit_no)
);
CREATE INDEX idx_visit_doctor_time ON visit (doctor_id, register_time);
CREATE INDEX idx_visit_patient ON visit (patient_id);
CREATE INDEX idx_visit_status ON visit (status);

CREATE TABLE medical_record (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    visit_id            BIGINT NOT NULL,
    chief_complaint     VARCHAR(1000),
    present_illness     VARCHAR(1000),
    past_history        VARCHAR(1000),
    inspection          VARCHAR(1000),
    auscultation        VARCHAR(1000),
    inquiry             VARCHAR(1000),
    palpation           VARCHAR(1000),
    tongue              VARCHAR(500),
    pulse               VARCHAR(500),
    syndrome            VARCHAR(500),
    treatment_principle VARCHAR(500),
    advice              VARCHAR(1000),
    version             INT    NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL,
    updated_at          DATETIME NOT NULL,
    CONSTRAINT uk_medical_record_visit UNIQUE (visit_id)
);

CREATE TABLE drug (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    pinyin     VARCHAR(50),
    category   VARCHAR(20)  NOT NULL,
    spec       VARCHAR(100),
    unit       VARCHAR(20)  NOT NULL,
    price      DECIMAL(10, 2) NOT NULL DEFAULT 0,
    cost_price DECIMAL(10, 2) DEFAULT 0,
    stock_warn DECIMAL(12, 3) DEFAULT 0,
    status     INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_drug_code UNIQUE (code)
);
CREATE INDEX idx_drug_name ON drug (name);
CREATE INDEX idx_drug_pinyin ON drug (pinyin);

CREATE TABLE drug_batch (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    drug_id         BIGINT       NOT NULL,
    batch_no        VARCHAR(50)  NOT NULL,
    production_date DATE,
    expiry_date     DATE,
    quantity        DECIMAL(12, 3) NOT NULL DEFAULT 0,
    cost_price      DECIMAL(10, 2) DEFAULT 0,
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL
);
CREATE INDEX idx_drug_batch_drug ON drug_batch (drug_id);
CREATE INDEX idx_drug_batch_expiry ON drug_batch (expiry_date);

CREATE TABLE stock_ledger (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    drug_id       BIGINT       NOT NULL,
    drug_name     VARCHAR(100),
    batch_no      VARCHAR(50),
    change_type   VARCHAR(20)  NOT NULL,
    quantity      DECIMAL(12, 3) NOT NULL,
    before_qty    DECIMAL(12, 3) NOT NULL,
    after_qty     DECIMAL(12, 3) NOT NULL,
    ref_type      VARCHAR(32),
    ref_id        BIGINT,
    operator_id   BIGINT,
    operator_name VARCHAR(50),
    remark        VARCHAR(200),
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL
);
CREATE INDEX idx_stock_ledger_drug ON stock_ledger (drug_id, id);

CREATE TABLE prescription (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    prescription_no   VARCHAR(32) NOT NULL,
    visit_id          BIGINT      NOT NULL,
    patient_id        BIGINT      NOT NULL,
    doctor_id         BIGINT      NOT NULL,
    type              VARCHAR(20) NOT NULL,
    decoction_method  VARCHAR(50),
    doses             INT         NOT NULL DEFAULT 1,
    total_amount      DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status            VARCHAR(20) NOT NULL,
    remark            VARCHAR(200),
    charge_order_id   BIGINT,
    created_at        DATETIME NOT NULL,
    updated_at        DATETIME NOT NULL,
    CONSTRAINT uk_prescription_no UNIQUE (prescription_no)
);
CREATE INDEX idx_prescription_visit ON prescription (visit_id);
CREATE INDEX idx_prescription_status ON prescription (status);

CREATE TABLE prescription_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    prescription_id BIGINT NOT NULL,
    drug_id         BIGINT NOT NULL,
    drug_name       VARCHAR(100) NOT NULL,
    spec            VARCHAR(100),
    unit            VARCHAR(20),
    dose            DECIMAL(12, 3),
    quantity        DECIMAL(12, 3) NOT NULL,
    price           DECIMAL(10, 2) NOT NULL DEFAULT 0,
    amount          DECIMAL(12, 2) NOT NULL DEFAULT 0,
    usage_text      VARCHAR(200),
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL
);
CREATE INDEX idx_prescription_item_prescription ON prescription_item (prescription_id);

CREATE TABLE charge_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(32) NOT NULL,
    visit_id        BIGINT,
    patient_id      BIGINT      NOT NULL,
    type            VARCHAR(20) NOT NULL,
    total_amount    DECIMAL(12, 2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(12, 2) NOT NULL DEFAULT 0,
    payable_amount  DECIMAL(12, 2) NOT NULL DEFAULT 0,
    paid_amount     DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL,
    pay_method      VARCHAR(20),
    paid_at         DATETIME,
    remark          VARCHAR(200),
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    CONSTRAINT uk_charge_order_no UNIQUE (order_no)
);
CREATE INDEX idx_charge_order_status ON charge_order (status, id);
CREATE INDEX idx_charge_order_visit ON charge_order (visit_id);
CREATE INDEX idx_charge_order_patient ON charge_order (patient_id);

CREATE TABLE charge_item (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT NOT NULL,
    item_type  VARCHAR(20) NOT NULL,
    item_name  VARCHAR(100) NOT NULL,
    drug_id    BIGINT,
    quantity   DECIMAL(12, 3) NOT NULL DEFAULT 1,
    unit_price DECIMAL(10, 2) NOT NULL DEFAULT 0,
    amount     DECIMAL(12, 2) NOT NULL DEFAULT 0,
    ref_id     BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
CREATE INDEX idx_charge_item_order ON charge_item (order_id);

CREATE TABLE payment (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT      NOT NULL,
    method        VARCHAR(20) NOT NULL,
    amount        DECIMAL(12, 2) NOT NULL,
    trade_no      VARCHAR(64),
    operator_id   BIGINT,
    operator_name VARCHAR(50),
    remark        VARCHAR(200),
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL
);
CREATE INDEX idx_payment_order ON payment (order_id);
CREATE INDEX idx_payment_created ON payment (created_at);

CREATE TABLE member_card (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_no  VARCHAR(32) NOT NULL,
    patient_id BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL,
    phone      VARCHAR(20),
    level      INT NOT NULL DEFAULT 1,
    balance    DECIMAL(12, 2) NOT NULL DEFAULT 0,
    points     INT NOT NULL DEFAULT 0,
    status     INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_member_no UNIQUE (member_no)
);
CREATE INDEX idx_member_patient ON member_card (patient_id);
CREATE INDEX idx_member_phone ON member_card (phone);

CREATE TABLE member_txn (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT      NOT NULL,
    txn_type      VARCHAR(20) NOT NULL,
    amount        DECIMAL(12, 2) NOT NULL,
    balance_after DECIMAL(12, 2) NOT NULL,
    ref_type      VARCHAR(32),
    ref_id        BIGINT,
    operator_id   BIGINT,
    remark        VARCHAR(200),
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL
);
CREATE INDEX idx_member_txn_member ON member_txn (member_id, id);

CREATE TABLE insurance_settlement (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    settlement_no    VARCHAR(32) NOT NULL,
    order_id         BIGINT      NOT NULL,
    provider_code    VARCHAR(32) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    self_pay_amount  DECIMAL(12, 2) DEFAULT 0,
    insurance_amount DECIMAL(12, 2) DEFAULT 0,
    message          VARCHAR(500),
    created_at       DATETIME NOT NULL,
    updated_at       DATETIME NOT NULL,
    CONSTRAINT uk_settlement_no UNIQUE (settlement_no)
);
CREATE INDEX idx_settlement_order ON insurance_settlement (order_id);
