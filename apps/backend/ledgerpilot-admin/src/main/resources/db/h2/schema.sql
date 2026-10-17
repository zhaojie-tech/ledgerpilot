-- H2（MODE=MySQL）最小 DDL 子集：本地启动与冒烟。
-- 完整 MySQL 生产 DDL 见 db/init.sql；两者结构保持人工同步（由方言冒烟用例守护）。
-- 注意：H2 下不使用 ENGINE/表级 COMMENT 等 MySQL 专有子句。

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    real_name VARCHAR(50),
    phone VARCHAR(20),
    email VARCHAR(100),
    role VARCHAR(20),
    status INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    CONSTRAINT uk_tenant_username UNIQUE (tenant_id, username)
);

CREATE TABLE IF NOT EXISTS acc_subject (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    subject_code VARCHAR(20) NOT NULL,
    subject_name VARCHAR(100) NOT NULL,
    subject_type VARCHAR(20),
    level INT DEFAULT 1,
    parent_code VARCHAR(20),
    direction VARCHAR(10),
    is_detail INT DEFAULT 1,
    aux_dimensions VARCHAR(200),
    balance_direction VARCHAR(10),
    is_enabled INT DEFAULT 1,
    sys_sort INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT uk_tenant_subject UNIQUE (tenant_id, subject_code)
);

CREATE TABLE IF NOT EXISTS acc_voucher (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    voucher_no VARCHAR(30) NOT NULL,
    voucher_date DATE NOT NULL,
    business_type VARCHAR(30),
    business_no VARCHAR(100),
    total_debit DECIMAL(18,2) DEFAULT 0,
    total_credit DECIMAL(18,2) DEFAULT 0,
    entry_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DRAFT',
    source_type VARCHAR(20) DEFAULT 'MANUAL',
    summary VARCHAR(500),
    approve_by BIGINT,
    approve_at TIMESTAMP,
    post_by BIGINT,
    post_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT uk_tenant_voucher UNIQUE (tenant_id, voucher_no)
);

CREATE TABLE IF NOT EXISTS acc_voucher_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    voucher_id BIGINT NOT NULL,
    seq INT NOT NULL,
    subject_id BIGINT DEFAULT 0,
    subject_code VARCHAR(20) NOT NULL,
    subject_name VARCHAR(100) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    summary VARCHAR(200),
    aux_json VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS acc_subject_balance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    subject_code VARCHAR(20) NOT NULL,
    fiscal_year INT NOT NULL,
    period_no INT NOT NULL,
    begin_debit DECIMAL(18,2) DEFAULT 0,
    begin_credit DECIMAL(18,2) DEFAULT 0,
    current_debit DECIMAL(18,2) DEFAULT 0,
    current_credit DECIMAL(18,2) DEFAULT 0,
    end_debit DECIMAL(18,2) DEFAULT 0,
    end_credit DECIMAL(18,2) DEFAULT 0,
    version INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tenant_subject_period UNIQUE (tenant_id, subject_code, fiscal_year, period_no)
);

CREATE TABLE IF NOT EXISTS acc_period_close (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    fiscal_year INT NOT NULL,
    period_no INT NOT NULL,
    closed_at TIMESTAMP,
    closed_by BIGINT,
    transfer_voucher_id BIGINT,
    remark VARCHAR(200),
    CONSTRAINT uk_period_close UNIQUE (tenant_id, fiscal_year, period_no)
);

CREATE TABLE IF NOT EXISTS rate_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    category VARCHAR(30) NOT NULL,
    sub_category VARCHAR(50),
    personal_rate DECIMAL(10,4),
    employer_rate DECIMAL(10,4),
    fixed_rate DECIMAL(10,4),
    unit VARCHAR(20) DEFAULT 'PERCENT',
    description VARCHAR(200),
    enabled INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    created_by BIGINT,
    updated_by BIGINT
);
