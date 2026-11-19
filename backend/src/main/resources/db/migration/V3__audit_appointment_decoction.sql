-- 第二版：操作审计、预约挂号、代煎流程

CREATE TABLE audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT,
    username    VARCHAR(50),
    real_name   VARCHAR(50),
    role        VARCHAR(20),
    module      VARCHAR(50)  NOT NULL,
    action      VARCHAR(100) NOT NULL,
    target_type VARCHAR(50),
    target_id   BIGINT,
    detail      VARCHAR(2000),
    ip          VARCHAR(64),
    result      VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL
);
CREATE INDEX idx_audit_created ON audit_log (created_at);
CREATE INDEX idx_audit_username ON audit_log (username);
CREATE INDEX idx_audit_module ON audit_log (module);

CREATE TABLE appointment (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    appoint_no    VARCHAR(32) NOT NULL,
    patient_id    BIGINT      NOT NULL,
    patient_name  VARCHAR(50),
    patient_phone VARCHAR(20),
    doctor_id     BIGINT      NOT NULL,
    doctor_name   VARCHAR(50),
    appoint_date  DATE        NOT NULL,
    time_slot     VARCHAR(20) NOT NULL,
    complaint     VARCHAR(500),
    status        VARCHAR(20) NOT NULL,
    visit_id      BIGINT,
    source        VARCHAR(20) NOT NULL DEFAULT 'MINIAPP',
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL,
    CONSTRAINT uk_appointment_no UNIQUE (appoint_no)
);
CREATE INDEX idx_appointment_date ON appointment (appoint_date, doctor_id);
CREATE INDEX idx_appointment_patient ON appointment (patient_id);

CREATE TABLE decoction_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    decoction_no    VARCHAR(32) NOT NULL,
    prescription_id BIGINT      NOT NULL,
    prescription_no VARCHAR(32),
    patient_id      BIGINT      NOT NULL,
    patient_name    VARCHAR(50),
    doctor_id       BIGINT,
    doctor_name     VARCHAR(50),
    doses           INT         NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL,
    cook_start_time DATETIME,
    cook_end_time   DATETIME,
    expected_time   DATETIME,
    deliver_time    DATETIME,
    deliver_type    VARCHAR(20),
    address         VARCHAR(200),
    phone           VARCHAR(20),
    operator_id     BIGINT,
    remark          VARCHAR(500),
    created_at      DATETIME NOT NULL,
    updated_at      DATETIME NOT NULL,
    CONSTRAINT uk_decoction_no UNIQUE (decoction_no)
);
CREATE INDEX idx_decoction_status ON decoction_order (status, id);
CREATE INDEX idx_decoction_prescription ON decoction_order (prescription_id);
