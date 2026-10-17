-- H2 演示数据（与 db/h2/schema.sql 配套；全部为合成数据）
-- 演示账号：admin / 123456（BCrypt 哈希与 MySQL seed 同源）

INSERT INTO sys_user (id, tenant_id, username, password, real_name, role, status, deleted)
VALUES (1, 1, 'admin', '$2b$10$W9plrqKQVKP2m1ISG1I0UuH4k5YBqaEdxQEwb3cZp/dfjww70Owle', '演示管理员', 'SUPER_ADMIN', 1, 0);

INSERT INTO acc_subject (id, tenant_id, subject_code, subject_name, subject_type, level, parent_code, direction, is_detail, sys_sort, is_enabled, deleted) VALUES
(1,  1, '1001', '库存现金',     'ASSET',     1, NULL, 'DEBIT',  1, 10, 1, 0),
(2,  1, '1002', '银行存款',     'ASSET',     1, NULL, 'DEBIT',  1, 11, 1, 0),
(3,  1, '1122', '应收账款',     'ASSET',     1, NULL, 'DEBIT',  1, 13, 1, 0),
(4,  1, '1405', '库存商品',     'ASSET',     1, NULL, 'DEBIT',  1, 16, 1, 0),
(5,  1, '1601', '固定资产',     'ASSET',     1, NULL, 'DEBIT',  1, 17, 1, 0),
(6,  1, '2202', '应付账款',     'LIABILITY', 1, NULL, 'CREDIT', 1, 22, 1, 0),
(7,  1, '2211', '应付职工薪酬', 'LIABILITY', 1, NULL, 'CREDIT', 1, 24, 1, 0),
(8,  1, '2221', '应交税费',     'LIABILITY', 1, NULL, 'CREDIT', 1, 25, 1, 0),
(9,  1, '2241', '其他应付款',   'LIABILITY', 1, NULL, 'CREDIT', 1, 26, 1, 0),
(10, 1, '4001', '实收资本',     'EQUITY',    1, NULL, 'CREDIT', 1, 30, 1, 0),
(11, 1, '4103', '本年利润',     'EQUITY',    1, NULL, 'CREDIT', 1, 31, 1, 0),
(12, 1, '6001', '主营业务收入', 'PROFIT_LOSS', 1, NULL, 'CREDIT', 1, 40, 1, 0),
(13, 1, '6051', '其他业务收入', 'PROFIT_LOSS', 1, NULL, 'CREDIT', 1, 41, 1, 0),
(14, 1, '6401', '主营业务成本', 'PROFIT_LOSS', 1, NULL, 'DEBIT',  1, 50, 1, 0),
(15, 1, '6602', '销售费用',     'PROFIT_LOSS', 1, NULL, 'DEBIT',  1, 51, 1, 0),
(16, 1, '6603', '管理费用',     'PROFIT_LOSS', 1, NULL, 'DEBIT',  1, 52, 1, 0),
(17, 1, '6604', '财务费用',     'PROFIT_LOSS', 1, NULL, 'DEBIT',  1, 53, 1, 0);

INSERT INTO rate_config (id, tenant_id, category, sub_category, personal_rate, employer_rate, fixed_rate, unit, description, enabled, deleted) VALUES
(1, 1, 'social', 'pension',      0.08,  0.16, NULL, 'PERCENT', '养老保险（演示费率）', 1, 0),
(2, 1, 'social', 'medical',      0.02,  0.08, NULL, 'PERCENT', '医疗保险（演示费率）', 1, 0),
(3, 1, 'social', 'unemployment', 0.005, 0.005, NULL, 'PERCENT', '失业保险（演示费率）', 1, 0),
(4, 1, 'housing', 'housing_fund', 0.12,  0.12, NULL, 'PERCENT', '住房公积金（演示费率）', 1, 0),
(5, 1, 'vat', 'small_3', NULL, NULL, 0.03, 'PERCENT', '小规模纳税人3%（演示费率）', 1, 0),
(6, 1, 'corp_tax', 'small_profit_20', NULL, NULL, 0.20, 'PERCENT', '小型微利企业20%（演示费率）', 1, 0);
