-- ============================================================
-- LedgerPilot 智能做账SaaS平台 - 数据库初始化脚本
-- 数据库: ledgerpilot
-- 字符集: utf8mb4
-- 依赖: MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ledgerpilot` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `ledgerpilot`;

-- ============================================================
-- 第1部分：全局共享表（不按tenant_id隔离）
-- ============================================================

-- ----------------------------
-- 1.1 sys_dict 数据字典
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dict_type` VARCHAR(50) NOT NULL COMMENT '字典类型',
    `dict_code` VARCHAR(50) NOT NULL COMMENT '字典编码',
    `dict_label` VARCHAR(100) NOT NULL COMMENT '字典标签',
    `dict_value` VARCHAR(100) NOT NULL COMMENT '字典值',
    `dict_sort` INT DEFAULT 0 COMMENT '排序',
    `enabled` TINYINT DEFAULT 1 COMMENT '是否启用：0=否，1=是',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type_code` (`dict_type`, `dict_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典';

-- 预置数据
INSERT INTO `sys_dict` (`dict_type`, `dict_code`, `dict_label`, `dict_value`, `dict_sort`) VALUES
('taxpayer_type', 'GENERAL', '一般纳税人', 'GENERAL', 1),
('taxpayer_type', 'SMALL_SCALE', '小规模纳税人', 'SMALL_SCALE', 2),
('plan_type', 'FREE', '免费版', 'FREE', 1),
('plan_type', 'BASIC', '基础版', 'BASIC', 2),
('plan_type', 'PRO', '专业版', 'PRO', 3),
('industry', 'TRADING', '商贸企业', 'TRADING', 1),
('industry', 'SERVICE', '服务企业', 'SERVICE', 2),
('industry', 'MANUFACTURING', '制造企业', 'MANUFACTURING', 3),
('voucher_status', 'DRAFT', '草稿', 'DRAFT', 1),
('voucher_status', 'APPROVED', '已审核', 'APPROVED', 2),
('voucher_status', 'POSTED', '已过账', 'POSTED', 3),
('voucher_status', 'VOIDED', '已作废', 'VOIDED', 4),
('source_type', 'NLP', 'NLP生成', 'NLP', 1),
('source_type', 'OCR', 'OCR识别', 'OCR', 2),
('source_type', 'MANUAL', '手工录入', 'MANUAL', 3),
('direction', 'DEBIT', '借方', 'DEBIT', 1),
('direction', 'CREDIT', '贷方', 'CREDIT', 2),
('biz_type', 'PURCHASE', '采购', 'PURCHASE', 1),
('biz_type', 'SALE', '销售', 'SALE', 2),
('biz_type', 'EXPENSE', '费用报销', 'EXPENSE', 3),
('biz_type', 'RECEIVE', '收款', 'RECEIVE', 4),
('biz_type', 'PAY', '付款', 'PAY', 5),
('biz_type', 'TRANSFER', '转账', 'TRANSFER', 6);

-- ----------------------------
-- 1.2 sys_region 行政区划
-- ----------------------------
DROP TABLE IF EXISTS `sys_region`;
CREATE TABLE `sys_region` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(20) NOT NULL COMMENT '行政区划代码',
    `name` VARCHAR(100) NOT NULL COMMENT '名称',
    `level` TINYINT NOT NULL COMMENT '级别：1=省，2=市，3=区/县',
    `parent_code` VARCHAR(20) DEFAULT NULL COMMENT '父级代码',
    PRIMARY KEY (`id`),
    KEY `idx_parent` (`parent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政区划表';

-- ----------------------------
-- 1.3 acc_subject_system 标准科目体系
-- ----------------------------
DROP TABLE IF EXISTS `acc_subject_system`;
CREATE TABLE `acc_subject_system` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `system_code` VARCHAR(20) NOT NULL COMMENT '准则编码：SMALL_BUSINESS=小企业会计准则，CAS=企业会计准则',
    `subject_code` VARCHAR(20) NOT NULL COMMENT '科目编码',
    `subject_name` VARCHAR(100) NOT NULL COMMENT '科目名称',
    `subject_type` VARCHAR(20) NOT NULL COMMENT '科目类型：ASSET=资产，LIABILITY=负债，EQUITY=权益，COST=成本，PROFIT_LOSS=损益',
    `level` TINYINT NOT NULL COMMENT '级次',
    `parent_code` VARCHAR(20) DEFAULT NULL COMMENT '上级编码',
    `direction` VARCHAR(10) NOT NULL COMMENT '余额方向：DEBIT=借方，CREDIT=贷方',
    `is_detail` TINYINT DEFAULT 0 COMMENT '是否明细（末级）：0=否，1=是',
    `is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用',
    `sys_sort` INT DEFAULT 0 COMMENT '系统排序',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_system_subject` (`system_code`, `subject_code`),
    KEY `idx_parent` (`parent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标准科目体系（66+162个一级科目）';

-- ----------------------------
-- 1.4 voucher_template 凭证模板
-- ----------------------------
DROP TABLE IF EXISTS `voucher_template`;
CREATE TABLE `voucher_template` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `template_code` VARCHAR(30) NOT NULL COMMENT '模板编码',
    `template_name` VARCHAR(100) NOT NULL COMMENT '模板名称',
    `biz_type` VARCHAR(30) NOT NULL COMMENT '业务类型',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '说明',
    `enabled` TINYINT DEFAULT 1,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='凭证模板';

DROP TABLE IF EXISTS `voucher_template_entry`;
CREATE TABLE `voucher_template_entry` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `template_id` BIGINT NOT NULL COMMENT '模板ID',
    `seq` INT NOT NULL COMMENT '分录序号',
    `direction` VARCHAR(10) NOT NULL COMMENT '方向：DEBIT/CREDIT',
    `subject_code` VARCHAR(20) NOT NULL COMMENT '科目编码',
    `amount_type` VARCHAR(20) NOT NULL COMMENT '金额类型：TOTAL=全额，TAX=税额，NET=净额，FIXED=固定值',
    `amount_ratio` DECIMAL(10,4) DEFAULT 1 COMMENT '金额比例（当amount_type=FIXED时为固定值）',
    `aux_config` VARCHAR(200) DEFAULT NULL COMMENT '辅助核算配置：customer/supplier/department/project等',
    `summary` VARCHAR(200) DEFAULT NULL COMMENT '分录摘要模板',
    PRIMARY KEY (`id`),
    KEY `idx_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板分录行';

DROP TABLE IF EXISTS `voucher_template_condition`;
CREATE TABLE `voucher_template_condition` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `template_id` BIGINT NOT NULL COMMENT '模板ID',
    `condition_field` VARCHAR(50) NOT NULL COMMENT '条件字段',
    `condition_op` VARCHAR(20) NOT NULL COMMENT '条件操作符：EQ/NE/GT/LT/CONTAINS/IN',
    `condition_value` VARCHAR(200) NOT NULL COMMENT '条件值',
    `override_subject_code` VARCHAR(20) DEFAULT NULL COMMENT '覆盖科目编码',
    `override_amount_ratio` DECIMAL(10,4) DEFAULT NULL COMMENT '覆盖金额比例',
    `priority` INT DEFAULT 0 COMMENT '优先级（数字越大优先级越高）',
    PRIMARY KEY (`id`),
    KEY `idx_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板条件覆盖';

-- ============================================================
-- 第2部分：租户隔离表（必须有tenant_id）
-- ============================================================

-- ----------------------------
-- 2.1 sys_tenant 租户信息
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant`;
CREATE TABLE `sys_tenant` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键（雪花算法）',
    `tenant_code` VARCHAR(20) NOT NULL COMMENT '租户编码（唯一）',
    `company_name` VARCHAR(200) NOT NULL COMMENT '公司名称',
    `taxpayer_type` VARCHAR(20) DEFAULT 'SMALL_SCALE' COMMENT 'GENERAL=一般纳税人，SMALL_SCALE=小规模',
    `industry` VARCHAR(50) DEFAULT NULL COMMENT '行业',
    `plan_type` VARCHAR(20) DEFAULT 'FREE' COMMENT '套餐：FREE/BASIC/PRO',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0=禁用，1=正常',
    `expired_at` DATETIME DEFAULT NULL COMMENT '套餐到期时间',
    `llm_quota_monthly` INT DEFAULT 100 COMMENT '本月LLM调用配额',
    `llm_quota_used` INT DEFAULT 0 COMMENT '本月已用LLM调用次数',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- ----------------------------
-- 2.2 sys_user 用户表
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(200) NOT NULL COMMENT '密码（BCrypt加密）',
    `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色：SUPER_ADMIN/TENANT_ADMIN/USER',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0=禁用，1=正常',
    `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------
-- 2.3 acc_subject 租户科目实例
-- ----------------------------
DROP TABLE IF EXISTS `acc_subject`;
CREATE TABLE `acc_subject` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `subject_code` VARCHAR(20) NOT NULL COMMENT '科目编码',
    `subject_name` VARCHAR(100) NOT NULL COMMENT '科目名称',
    `subject_type` VARCHAR(20) NOT NULL COMMENT 'ASSET/LIABILITY/EQUITY/COST/PROFIT_LOSS',
    `level` TINYINT NOT NULL COMMENT '级次',
    `parent_code` VARCHAR(20) DEFAULT NULL COMMENT '上级编码',
    `direction` VARCHAR(10) NOT NULL COMMENT 'DEBIT/CREDIT',
    `is_detail` TINYINT DEFAULT 0 COMMENT '是否末级',
    `aux_dimensions` VARCHAR(200) DEFAULT NULL COMMENT '辅助核算维度：customer,supplier,department,project',
    `balance_direction` VARCHAR(10) DEFAULT NULL COMMENT '余额方向（自定义）',
    `is_enabled` TINYINT DEFAULT 1,
    `sys_sort` INT DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_subject` (`tenant_id`, `subject_code`),
    KEY `idx_tenant_parent` (`tenant_id`, `parent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户科目表';

-- ----------------------------
-- 2.4 acc_aux_dimension 辅助核算维度定义
-- ----------------------------
DROP TABLE IF EXISTS `acc_aux_dimension`;
CREATE TABLE `acc_aux_dimension` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `dimension_type` VARCHAR(30) NOT NULL COMMENT '维度类型：customer/supplier/department/project/employee/warehouse',
    `dimension_name` VARCHAR(50) NOT NULL COMMENT '维度名称',
    `dimension_code` VARCHAR(30) NOT NULL COMMENT '维度编码',
    `status` TINYINT DEFAULT 1,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_type_code` (`tenant_id`, `dimension_type`, `dimension_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='辅助核算维度';

-- ----------------------------
-- 2.5 acc_subject_aux_config 科目-辅助核算关联配置
-- ----------------------------
DROP TABLE IF EXISTS `acc_subject_aux_config`;
CREATE TABLE `acc_subject_aux_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL,
    `subject_id` BIGINT NOT NULL COMMENT '科目ID',
    `dimension_type` VARCHAR(30) NOT NULL COMMENT '辅助核算维度类型',
    `is_required` TINYINT DEFAULT 0 COMMENT '是否必填：0=选填，1=必填',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_subject_dim` (`tenant_id`, `subject_id`, `dimension_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科目辅助核算配置';

-- ----------------------------
-- 2.6 acc_voucher 凭证主表
-- ----------------------------
DROP TABLE IF EXISTS `acc_voucher`;
CREATE TABLE `acc_voucher` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `voucher_no` VARCHAR(30) NOT NULL COMMENT '凭证号（按月/按年生成）',
    `voucher_date` DATE NOT NULL COMMENT '凭证日期',
    `business_type` VARCHAR(30) DEFAULT NULL COMMENT '业务类型',
    `business_no` VARCHAR(100) DEFAULT NULL COMMENT '业务单号（如发票号）',
    `total_debit` DECIMAL(18,2) DEFAULT 0 COMMENT '借方合计',
    `total_credit` DECIMAL(18,2) DEFAULT 0 COMMENT '贷方合计',
    `entry_count` INT DEFAULT 0 COMMENT '分录条数',
    `status` VARCHAR(20) DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVED/POSTED/VOIDED',
    `source_type` VARCHAR(20) DEFAULT 'MANUAL' COMMENT 'NLP/OCR/MANUAL',
    `summary` VARCHAR(500) DEFAULT NULL COMMENT '凭证摘要',
    `approve_by` BIGINT DEFAULT NULL COMMENT '审核人',
    `approve_at` DATETIME DEFAULT NULL COMMENT '审核时间',
    `post_by` BIGINT DEFAULT NULL COMMENT '过账人',
    `post_at` DATETIME DEFAULT NULL COMMENT '过账时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_voucher` (`tenant_id`, `voucher_no`),
    KEY `idx_tenant_date` (`tenant_id`, `voucher_date`),
    KEY `idx_tenant_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='凭证主表';

-- ----------------------------
-- 2.7 acc_voucher_entry 凭证分录
-- ----------------------------
DROP TABLE IF EXISTS `acc_voucher_entry`;
CREATE TABLE `acc_voucher_entry` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `voucher_id` BIGINT NOT NULL COMMENT '凭证ID',
    `seq` INT NOT NULL COMMENT '分录序号',
    `subject_id` BIGINT NOT NULL COMMENT '科目ID',
    `subject_code` VARCHAR(20) NOT NULL COMMENT '科目编码（冗余存储）',
    `subject_name` VARCHAR(100) NOT NULL COMMENT '科目名称（冗余存储）',
    `direction` VARCHAR(10) NOT NULL COMMENT 'DEBIT/CREDIT',
    `amount` DECIMAL(18,2) NOT NULL COMMENT '金额',
    `summary` VARCHAR(200) DEFAULT NULL COMMENT '分录摘要',
    `aux_json` VARCHAR(500) DEFAULT NULL COMMENT '辅助核算JSON：{"customer":"xxx","project":"yyy"}',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_voucher` (`tenant_id`, `voucher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='凭证分录表';

-- ----------------------------
-- 2.8 acc_subject_balance 科目余额表（带乐观锁）
-- ----------------------------
DROP TABLE IF EXISTS `acc_subject_balance`;
CREATE TABLE `acc_subject_balance` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `subject_code` VARCHAR(20) NOT NULL COMMENT '科目编码',
    `fiscal_year` INT NOT NULL COMMENT '会计年度',
    `period_no` TINYINT NOT NULL COMMENT '会计期间（1-12）',
    `begin_debit` DECIMAL(18,2) DEFAULT 0 COMMENT '期初借方余额',
    `begin_credit` DECIMAL(18,2) DEFAULT 0 COMMENT '期初贷方余额',
    `current_debit` DECIMAL(18,2) DEFAULT 0 COMMENT '本期借方发生额',
    `current_credit` DECIMAL(18,2) DEFAULT 0 COMMENT '本期贷方发生额',
    `end_debit` DECIMAL(18,2) DEFAULT 0 COMMENT '期末借方余额',
    `end_credit` DECIMAL(18,2) DEFAULT 0 COMMENT '期末贷方余额',
    `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_subject_period` (`tenant_id`, `subject_code`, `fiscal_year`, `period_no`),
    KEY `idx_tenant_year` (`tenant_id`, `fiscal_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='科目余额表（带乐观锁version）';

-- ----------------------------
-- 2.8.1 acc_period_close 期间关账记录
-- ----------------------------
DROP TABLE IF EXISTS `acc_period_close`;
CREATE TABLE `acc_period_close` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `fiscal_year` INT NOT NULL COMMENT '会计年度',
    `period_no` TINYINT NOT NULL COMMENT '会计期间（1-12）',
    `closed_at` DATETIME DEFAULT NULL COMMENT '关账时间',
    `closed_by` BIGINT DEFAULT NULL COMMENT '关账人',
    `transfer_voucher_id` BIGINT DEFAULT NULL COMMENT '结转凭证ID',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_year_period` (`tenant_id`, `fiscal_year`, `period_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='期间关账记录';

-- ----------------------------
-- 2.9 biz_item_account_mapping 业务项-科目映射（租户自定义）
-- ----------------------------
DROP TABLE IF EXISTS `biz_item_account_mapping`;
CREATE TABLE `biz_item_account_mapping` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `biz_type` VARCHAR(30) NOT NULL COMMENT '业务类型',
    `biz_detail` VARCHAR(100) DEFAULT NULL COMMENT '业务明细（如"办公用品"）',
    `subject_code` VARCHAR(20) NOT NULL COMMENT '推荐科目编码',
    `direction` VARCHAR(10) DEFAULT NULL COMMENT '推荐方向（DEBIT/CREDIT）',
    `confidence` INT DEFAULT 100 COMMENT '置信度（0-100）',
    `enabled` TINYINT DEFAULT 1,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_biz_detail` (`tenant_id`, `biz_type`, `biz_detail`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务项-科目映射表';

-- ----------------------------
-- 2.10 nlp_parse_log NLP解析日志
-- ----------------------------
DROP TABLE IF EXISTS `nlp_parse_log`;
CREATE TABLE `nlp_parse_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL,
    `request_id` VARCHAR(50) NOT NULL COMMENT '请求ID',
    `user_input` TEXT NOT NULL COMMENT '用户原始输入',
    `parsed_json` TEXT DEFAULT NULL COMMENT 'AI解析结果JSON',
    `biz_item_count` INT DEFAULT 0 COMMENT '解析出的业务条数',
    `confidence_avg` DECIMAL(5,2) DEFAULT NULL COMMENT '平均置信度',
    `status` VARCHAR(20) DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED/PARTIAL',
    `error_msg` VARCHAR(500) DEFAULT NULL,
    `duration_ms` INT DEFAULT 0 COMMENT '耗时（毫秒）',
    `llm_model` VARCHAR(50) DEFAULT NULL COMMENT '调用的LLM模型',
    `token_used` INT DEFAULT 0 COMMENT 'Token消耗',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_request` (`tenant_id`, `request_id`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='NLP解析日志';

-- ----------------------------
-- 2.11 ocr_file_record OCR文件记录
-- ----------------------------
DROP TABLE IF EXISTS `ocr_file_record`;
CREATE TABLE `ocr_file_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `tenant_id` BIGINT NOT NULL,
    `file_name` VARCHAR(200) NOT NULL COMMENT '原始文件名',
    `file_url` VARCHAR(500) NOT NULL COMMENT 'OSS存储路径',
    `file_type` VARCHAR(20) NOT NULL COMMENT 'INVOICE=发票，BANK_STATEMENT=银行流水，RECEIPT=收据，OTHER=其他',
    `file_size` BIGINT DEFAULT NULL COMMENT '文件大小（字节）',
    `ocr_result_json` TEXT DEFAULT NULL COMMENT 'OCR识别结果JSON',
    `biz_item_count` INT DEFAULT 0 COMMENT '识别出的业务条数',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING=待处理，SUCCESS=成功，FAILED=失败',
    `verify_status` VARCHAR(20) DEFAULT NULL COMMENT '查验状态：PASS=通过，FAIL=不通过',
    `error_msg` VARCHAR(500) DEFAULT NULL,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_tenant_created` (`tenant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OCR文件记录';

-- ----------------------------
-- 2.12 rate_config 费率配置表
-- ----------------------------
DROP TABLE IF EXISTS `rate_config`;
CREATE TABLE `rate_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `category` VARCHAR(30) NOT NULL COMMENT '费率分类：social/housing/vat/corp_tax/depreciation',
    `sub_category` VARCHAR(50) NOT NULL COMMENT '子分类',
    `personal_rate` DECIMAL(10,4) DEFAULT NULL COMMENT '个人/从动费率（社保为个人费率，折旧为年限）',
    `employer_rate` DECIMAL(10,4) DEFAULT NULL COMMENT '单位/主费率（社保为单位费率，折旧为残值率）',
    `fixed_rate` DECIMAL(10,4) DEFAULT NULL COMMENT '固定税率（增值税/企业所得税/折旧进项税）',
    `unit` VARCHAR(20) DEFAULT 'PERCENT' COMMENT '单位：PERCENT=百分比，FIXED=固定金额',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '描述/说明',
    `enabled` TINYINT DEFAULT 1 COMMENT '启用状态：0=禁用，1=启用',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT DEFAULT 0,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_category_sub` (`tenant_id`, `category`, `sub_category`),
    KEY `idx_tenant_category` (`tenant_id`, `category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='费率配置表';

-- ============================================================
-- 第3部分：种子数据
-- ============================================================

-- 插入内置租户（系统管理员）
INSERT INTO `sys_tenant` (`id`, `tenant_code`, `company_name`, `taxpayer_type`, `industry`, `plan_type`, `status`) VALUES
(1, 'SYSTEM', '系统内置', 'SMALL_SCALE', 'TRADING', 'PRO', 1);

-- ============================================================
-- 第4部分：索引优化（确保跨租户查询性能）
-- ============================================================

-- 所有需要按tenant_id+其他字段查询的表已在上方DDL中创建联合索引
-- 遵循原则：tenant_id作为联合索引的第一列

-- ============================================================
-- 第5部分：注释说明
-- ============================================================
-- 凭证号生成规则：YYYYMM + 6位序号（每月从1开始，如202603000001）
-- 主键使用雪花算法（ASSIGN_ID），为未来分库分表预留
-- deleted字段用于逻辑删除（@TableLogic）
-- acc_subject_balance.version用于乐观锁（过账并发控制）
