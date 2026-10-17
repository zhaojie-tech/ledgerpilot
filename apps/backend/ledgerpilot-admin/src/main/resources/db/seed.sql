-- 【合成数据声明】本文件全部为演示用合成数据，不含任何真实主体信息。
USE `ledgerpilot`;

-- 第1步：更新租户
UPDATE `sys_tenant` SET `company_name`='演示科技有限公司（合成数据）',`taxpayer_type`='SMALL_SCALE',`industry`='TRADING',`plan_type`='PRO',`status`=1,`expired_at`='2027-12-31 23:59:59',`llm_quota_monthly`=1000,`llm_quota_used`=0 WHERE `id`=1;

-- 第2步：用户（密码123456，BCrypt $2b$10$）
INSERT INTO `sys_user` (`id`,`tenant_id`,`username`,`password`,`real_name`,`phone`,`email`,`role`,`status`) VALUES (1,1,'admin','$2b$10$W9plrqKQVKP2m1ISG1I0UuH4k5YBqaEdxQEwb3cZp/dfjww70Owle','演示管理员','13800000000','admin@example.com','SUPER_ADMIN',1) ON DUPLICATE KEY UPDATE `updated_at`=CURRENT_TIMESTAMP;
INSERT INTO `sys_user` (`id`,`tenant_id`,`username`,`password`,`real_name`,`phone`,`email`,`role`,`status`) VALUES (2,1,'wangkefeng','$2b$10$W9plrqKQVKP2m1ISG1I0UuH4k5YBqaEdxQEwb3cZp/dfjww70Owle','演示会计','13800000000','wang@example.com','TENANT_ADMIN',1) ON DUPLICATE KEY UPDATE `updated_at`=CURRENT_TIMESTAMP;
INSERT INTO `sys_user` (`id`,`tenant_id`,`username`,`password`,`real_name`,`phone`,`email`,`role`,`status`) VALUES (3,1,'liuxiaomei','$2b$10$W9plrqKQVKP2m1ISG1I0UuH4k5YBqaEdxQEwb3cZp/dfjww70Owle','演示出纳','13800000000','liu@example.com','USER',1) ON DUPLICATE KEY UPDATE `updated_at`=CURRENT_TIMESTAMP;

-- 第3步：科目（30个常用科目）
INSERT INTO `acc_subject` (`id`,`tenant_id`,`subject_code`,`subject_name`,`subject_type`,`level`,`parent_code`,`direction`,`is_detail`,`aux_dimensions`,`sys_sort`) VALUES
(1001,1,'1001','库存现金','ASSET',1,NULL,'DEBIT',1,NULL,10),
(1002,1,'1002','银行存款','ASSET',1,NULL,'DEBIT',1,'bank',11),
(1012,1,'1012','其他货币资金','ASSET',1,NULL,'DEBIT',1,NULL,12),
(1122,1,'1122','应收账款','ASSET',1,NULL,'DEBIT',1,'customer',13),
(1123,1,'1123','预付账款','ASSET',1,NULL,'DEBIT',1,'supplier',14),
(1221,1,'1221','其他应收款','ASSET',1,NULL,'DEBIT',1,'employee,other',15),
(1405,1,'1405','库存商品','ASSET',1,NULL,'DEBIT',1,'product,warehouse',16),
(1601,1,'1601','固定资产','ASSET',1,NULL,'DEBIT',1,'department',17),
(1602,1,'1602','累计折旧','ASSET',1,NULL,'CREDIT',1,NULL,18),
(1901,1,'1901','长期待摊费用','ASSET',1,NULL,'DEBIT',1,NULL,19),
(2001,1,'2001','短期借款','LIABILITY',1,NULL,'CREDIT',1,'bank',20),
(2201,1,'2201','应付票据','LIABILITY',1,NULL,'CREDIT',1,'supplier',21),
(2202,1,'2202','应付账款','LIABILITY',1,NULL,'CREDIT',1,'supplier',22),
(2203,1,'2203','预收账款','LIABILITY',1,NULL,'CREDIT',1,'customer',23),
(2211,1,'2211','应付职工薪酬','LIABILITY',1,NULL,'CREDIT',1,'employee',24),
(2221,1,'2221','应交税费','LIABILITY',1,NULL,'CREDIT',1,NULL,25),
(2241,1,'2241','其他应付款','LIABILITY',1,NULL,'CREDIT',1,'employee,other',26),
(4001,1,'4001','实收资本','EQUITY',1,NULL,'CREDIT',1,NULL,30),
(4002,1,'4002','资本公积','EQUITY',1,NULL,'CREDIT',1,NULL,31),
(4103,1,'4103','本年利润','PROFIT_LOSS',1,NULL,'CREDIT',1,NULL,32),
(4104,1,'4104','利润分配','PROFIT_LOSS',1,NULL,'CREDIT',1,NULL,33),
(5001,1,'5001','生产成本','COST',1,NULL,'DEBIT',1,'department,project',40),
(5101,1,'5101','制造费用','COST',1,NULL,'DEBIT',1,'department',41),
(6001,1,'6001','主营业务收入','PROFIT_LOSS',1,NULL,'CREDIT',1,'customer',50),
(6051,1,'6051','其他业务收入','PROFIT_LOSS',1,NULL,'CREDIT',1,NULL,51),
(6401,1,'6401','主营业务成本','PROFIT_LOSS',1,NULL,'DEBIT',1,'product',52),
(6402,1,'6402','其他业务成本','PROFIT_LOSS',1,NULL,'DEBIT',1,NULL,53),
(6602,1,'6602','销售费用','PROFIT_LOSS',1,NULL,'DEBIT',1,'department',54),
(6603,1,'6603','管理费用','PROFIT_LOSS',1,NULL,'DEBIT',1,'department',55),
(6604,1,'6604','财务费用','PROFIT_LOSS',1,NULL,'DEBIT',1,NULL,56),
(6801,1,'6801','所得税费用','PROFIT_LOSS',1,NULL,'DEBIT',1,NULL,57)
ON DUPLICATE KEY UPDATE `updated_at`=CURRENT_TIMESTAMP;

-- 第4步：辅助核算维度（无updated_by，只有updated_at）
INSERT INTO `acc_aux_dimension` (`tenant_id`,`dimension_type`,`dimension_name`,`dimension_code`,`status`) VALUES
(1,'customer','客户','CUSTOMER',1),
(1,'supplier','供应商','SUPPLIER',1),
(1,'department','部门','DEPT',1),
(1,'project','项目','PROJECT',1),
(1,'employee','职员','EMPLOYEE',1),
(1,'product','商品','PRODUCT',1),
(1,'warehouse','仓库','WAREHOUSE',1),
(1,'bank','银行账户','BANK',1)
ON DUPLICATE KEY UPDATE `updated_at`=CURRENT_TIMESTAMP;

-- 第5步：科目余额（2026年3期）- 含期初+本期发生
INSERT INTO `acc_subject_balance` (`tenant_id`,`subject_code`,`fiscal_year`,`period_no`,`begin_debit`,`begin_credit`,`current_debit`,`current_credit`,`end_debit`,`end_credit`) VALUES
(1,'1001',2026,3,5000.00,0.00,0.00,3800.00,1200.00,0.00),
(1,'1002',2026,3,800000.00,0.00,58715.00,0.00,858715.00,0.00),
(1,'1122',2026,3,50000.00,0.00,113000.00,58500.00,104500.00,0.00),
(1,'1405',2026,3,200000.00,0.00,30000.00,0.00,230000.00,0.00),
(1,'1601',2026,3,150000.00,0.00,0.00,0.00,150000.00,0.00),
(1,'1602',2026,3,0.00,30000.00,0.00,0.00,0.00,30000.00),
(1,'2001',2026,3,0.00,100000.00,0.00,0.00,0.00,100000.00),
(1,'2202',2026,3,0.00,80000.00,0.00,30000.00,0.00,110000.00),
(1,'2211',2026,3,0.00,20000.00,0.00,80000.00,0.00,100000.00),
(1,'2221',2026,3,0.00,5000.00,0.00,16900.00,0.00,21900.00),
(1,'4001',2026,3,0.00,700000.00,0.00,0.00,0.00,700000.00)
ON DUPLICATE KEY UPDATE `updated_at`=CURRENT_TIMESTAMP;

-- 第6步：凭证（6张）+ 分录
-- #1 3月5日 收到深圳腾达公司货款 58,500
INSERT INTO `acc_voucher` (`id`,`tenant_id`,`voucher_no`,`voucher_date`,`business_type`,`business_no`,`total_debit`,`total_credit`,`entry_count`,`status`,`source_type`,`summary`,`created_by`) VALUES (1,1,'202603000001','2026-03-05','RECEIVE','AR-202603001',58500.00,58500.00,2,'POSTED','NLP','收到深圳腾达公司货款',1);
INSERT INTO `acc_voucher_entry` (`tenant_id`,`voucher_id`,`seq`,`subject_id`,`subject_code`,`subject_name`,`direction`,`amount`,`summary`,`aux_json`) VALUES
(1,1,1,1002,'1002','银行存款','DEBIT',58500.00,'收到深圳腾达公司货款','{"bank":"中国银行深圳分行","customer":"深圳腾达公司"}'),
(1,1,2,1122,'1122','应收账款','CREDIT',58500.00,'收到深圳腾达公司货款','{"customer":"深圳腾达公司"}');

-- #2 3月8日 采购商品一批（含税33,900 = 商品30,000 + 税额3,900）
INSERT INTO `acc_voucher` (`id`,`tenant_id`,`voucher_no`,`voucher_date`,`business_type`,`business_no`,`total_debit`,`total_credit`,`entry_count`,`status`,`source_type`,`summary`,`created_by`) VALUES (2,1,'202603000002','2026-03-08','PURCHASE','PO-202603002',33900.00,33900.00,3,'POSTED','MANUAL','采购商品一批（增值税专用发票）',1);
INSERT INTO `acc_voucher_entry` (`tenant_id`,`voucher_id`,`seq`,`subject_id`,`subject_code`,`subject_name`,`direction`,`amount`,`summary`,`aux_json`) VALUES
(1,2,1,1405,'1405','库存商品','DEBIT',30000.00,'采购商品-电脑配件','{"supplier":"广州华联商贸"}'),
(1,2,2,2221,'2221','应交税费','DEBIT',3900.00,'进项税额（税率13%）',NULL),
(1,2,3,2202,'2202','应付账款','CREDIT',33900.00,'采购商品-电脑配件','{"supplier":"广州华联商贸"}');

-- #3 3月10日 计提本月工资 80,000
INSERT INTO `acc_voucher` (`id`,`tenant_id`,`voucher_no`,`voucher_date`,`business_type`,`business_no`,`total_debit`,`total_credit`,`entry_count`,`status`,`source_type`,`summary`,`created_by`) VALUES (3,1,'202603000003','2026-03-10','EXPENSE','SAL-202603003',80000.00,80000.00,2,'APPROVED','MANUAL','计提2026年3月职工薪酬',1);
INSERT INTO `acc_voucher_entry` (`tenant_id`,`voucher_id`,`seq`,`subject_id`,`subject_code`,`subject_name`,`direction`,`amount`,`summary`,`aux_json`) VALUES
(1,3,1,6603,'6603','管理费用','DEBIT',80000.00,'计提3月工资','{"department":"管理部门"}'),
(1,3,2,2211,'2211','应付职工薪酬','CREDIT',80000.00,'计提3月工资',NULL);

-- #4 3月15日 销售商品给北京中科公司（含税113,000 = 商品100,000 + 税额13,000）
INSERT INTO `acc_voucher` (`id`,`tenant_id`,`voucher_no`,`voucher_date`,`business_type`,`business_no`,`total_debit`,`total_credit`,`entry_count`,`status`,`source_type`,`summary`,`created_by`) VALUES (4,1,'202603000004','2026-03-15','SALE','INV-202603004',113000.00,113000.00,3,'POSTED','OCR','销售商品一批（增值税发票）',2);
INSERT INTO `acc_voucher_entry` (`tenant_id`,`voucher_id`,`seq`,`subject_id`,`subject_code`,`subject_name`,`direction`,`amount`,`summary`,`aux_json`) VALUES
(1,4,1,1122,'1122','应收账款','DEBIT',113000.00,'销售商品给北京中科公司','{"customer":"北京中科公司"}'),
(1,4,2,6001,'6001','主营业务收入','CREDIT',100000.00,'销售商品-办公设备','{"customer":"北京中科公司"}'),
(1,4,3,2221,'2221','应交税费','CREDIT',13000.00,'销项税额（税率13%）',NULL);

-- #5 3月20日 报销刘小美差旅费 3,800（现金）
INSERT INTO `acc_voucher` (`id`,`tenant_id`,`voucher_no`,`voucher_date`,`business_type`,`business_no`,`total_debit`,`total_credit`,`entry_count`,`status`,`source_type`,`summary`,`created_by`) VALUES (5,1,'202603000005','2026-03-20','EXPENSE','EXP-202603005',3800.00,3800.00,2,'POSTED','NLP','报销刘小美深圳出差差旅费',3);
INSERT INTO `acc_voucher_entry` (`tenant_id`,`voucher_id`,`seq`,`subject_id`,`subject_code`,`subject_name`,`direction`,`amount`,`summary`,`aux_json`) VALUES
(1,5,1,6602,'6602','销售费用','DEBIT',3800.00,'报销刘小美差旅费','{"employee":"刘小美","department":"销售部"}'),
(1,5,2,1001,'1001','库存现金','CREDIT',3800.00,'报销刘小美差旅费',NULL);

-- #6 3月25日 收到银行利息 215
INSERT INTO `acc_voucher` (`id`,`tenant_id`,`voucher_no`,`voucher_date`,`business_type`,`business_no`,`total_debit`,`total_credit`,`entry_count`,`status`,`source_type`,`summary`,`created_by`) VALUES (6,1,'202603000006','2026-03-25','RECEIVE','BANK-INT-202603006',215.00,215.00,2,'POSTED','MANUAL','银行活期存款利息',1);
INSERT INTO `acc_voucher_entry` (`tenant_id`,`voucher_id`,`seq`,`subject_id`,`subject_code`,`subject_name`,`direction`,`amount`,`summary`,`aux_json`) VALUES
(1,6,1,1002,'1002','银行存款','DEBIT',215.00,'银行活期存款利息','{"bank":"中国银行深圳分行"}'),
(1,6,2,6604,'6604','财务费用','CREDIT',215.00,'银行活期存款利息（冲减）',NULL);

-- 第7步：费率配置（rate_config）
-- 社保（养老、医疗、失业、工伤、生育）+ 住房公积金
-- 比例均为小数形式，如 0.08 = 8%
INSERT INTO `rate_config` (`tenant_id`,`category`,`sub_category`,`personal_rate`,`employer_rate`,`fixed_rate`,`unit`,`description`,`enabled`) VALUES
-- 养老保险
(1,'social','pension',0.08,0.16,NULL,'PERCENT','养老保险：个人8%，单位16%',1),
-- 医疗保险
(1,'social','medical',0.02,0.08,NULL,'PERCENT','医疗保险（含生育）：个人2%，单位8%',1),
-- 失业保险
(1,'social','unemployment',0.005,0.005,NULL,'PERCENT','失业保险：个人0.5%，单位0.5%',1),
-- 工伤保险
(1,'social','work_injury',NULL,0.004,NULL,'PERCENT','工伤保险：单位0.4%（个人不缴）',1),
-- 生育保险（已并入医疗，但保留）
(1,'social','maternity',NULL,NULL,NULL,'PERCENT','生育保险：已并入医疗保险',0),
-- 残疾人就业保障金
(1,'social','disability',0.015,NULL,NULL,'PERCENT','残疾人就业保障金：单位按职工工资总额1.5%',1),
-- 住房公积金
(1,'housing','housing_fund',0.07,0.07,NULL,'PERCENT','住房公积金：个人7%，单位7%（可调整）',1)
ON DUPLICATE KEY UPDATE `updated_at`=CURRENT_TIMESTAMP;

-- 增值税税率（固定税率）
INSERT INTO `rate_config` (`tenant_id`,`category`,`sub_category`,`personal_rate`,`employer_rate`,`fixed_rate`,`unit`,`description`,`enabled`) VALUES
(1,'vat','general_13',NULL,NULL,0.13,'PERCENT','一般纳税人13%税率',1),
(1,'vat','general_9',NULL,NULL,0.09,'PERCENT','一般纳税人9%税率（农产品、生活服务等）',1),
(1,'vat','general_6',NULL,NULL,0.06,'PERCENT','一般纳税人6%税率（现代服务等）',1),
(1,'vat','small_3',NULL,NULL,0.03,'PERCENT','小规模纳税人3%征收率（减按1%政策，实际用0.01）',1),
(1,'vat','small_5',NULL,NULL,0.05,'PERCENT','小规模纳税人5%征收率（不动产租赁等）',1),
(1,'vat','output_tax_rate',NULL,NULL,0.13,'PERCENT','默认销项税率（生成凭证用）',1)
ON DUPLICATE KEY UPDATE `updated_at`=CURRENT_TIMESTAMP;

-- 企业所得税税率
INSERT INTO `rate_config` (`tenant_id`,`category`,`sub_category`,`personal_rate`,`employer_rate`,`fixed_rate`,`unit`,`description`,`enabled`) VALUES
(1,'corp_tax','standard_25',NULL,NULL,0.25,'PERCENT','标准税率25%（一般企业）',1),
(1,'corp_tax','small_profit_20',NULL,NULL,0.20,'PERCENT','小型微利企业20%优惠税率',1),
(1,'corp_tax','high_tech_15',NULL,NULL,0.15,'PERCENT','高新技术企业15%优惠税率',1)
ON DUPLICATE KEY UPDATE `updated_at`=CURRENT_TIMESTAMP;

-- 固定资产折旧年限（personal_rate=折旧年限，employer_rate=残值率，fixed_rate=进项税额抵扣税率）
INSERT INTO `rate_config` (`tenant_id`,`category`,`sub_category`,`personal_rate`,`employer_rate`,`fixed_rate`,`unit`,`description`,`enabled`) VALUES
(1,'depreciation','building',20,0.05,0.13,'PERCENT','房屋折旧：年限20年，残值率5%，进项税率13%',1),
(1,'depreciation','structure',20,0.05,0.13,'PERCENT','构筑物折旧：年限20年，残值率5%',1),
(1,'depreciation','machinery',10,0.05,0.13,'PERCENT','机械设备折旧：年限10年，残值率5%',1),
(1,'depreciation','vehicle',4,0.05,0.13,'PERCENT','运输设备折旧：年限4年，残值率5%',1),
(1,'depreciation','electronic',3,0.05,0.13,'PERCENT','电子设备折旧：年限3年，残值率5%',1),
(1,'depreciation','furniture',5,0.05,NULL,'PERCENT','办公家具折旧：年限5年，残值率5%（无进项税）',1)
ON DUPLICATE KEY UPDATE `updated_at`=CURRENT_TIMESTAMP;

-- 第8步：NLP解析日志（nlp_parse_log无updated_at，只做INSERT）
INSERT INTO `nlp_parse_log` (`tenant_id`,`request_id`,`user_input`,`parsed_json`,`biz_item_count`,`confidence_avg`,`status`,`duration_ms`,`llm_model`,`created_at`) VALUES
(1,'nlp-001','3月20日，报销刘小美差旅费3800元，现金支付','{"bizDate":"2026-03-20","bizType":"EXPENSE","items":[{"subjectCode":"6602","direction":"DEBIT","amount":3800,"summary":"差旅费报销"}]}',1,95.00,'SUCCESS',1200,'qwen-plus','2026-03-20 14:30:00'),
(1,'nlp-002','3月25日，收到银行利息215元','{"bizDate":"2026-03-25","bizType":"RECEIVE","items":[{"subjectCode":"6604","direction":"CREDIT","amount":215,"summary":"银行利息"}]}',1,88.00,'SUCCESS',980,'qwen-plus','2026-03-25 09:15:00');
