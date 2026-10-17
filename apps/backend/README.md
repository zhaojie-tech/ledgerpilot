# LedgerPilot Backend（apps/backend）

**把「会计上错在哪」变成代码可验证的服务端**——多租户凭证引擎（BigDecimal 精确借贷平衡、期间关账锁、
余额原子累计）+ 确定性工资/税务计算器 + AI 解析服务（[apps/ai 的 AI 服务](../ai)）的集成端。
自然语言进（经 AI 服务），结构化凭证草稿出（经本仓入账），**入账确认权永远在人手里**。

> **架构关系**：apps/ai 的 AI 服务 负责「自然语言 → 凭证草稿 JSON」；本仓负责「草稿 → 校验入账 → 余额/报表」
> 与确定性计算。LLM 在整条链路上没有直接落账能力：AI 输出必须通过借贷平衡、科目存在性与期间锁校验，
> 且生成的是 DRAFT 凭证，仍需人工审核过账。

## 核心特性

- **凭证引擎**：科目/凭证 CRUD、审核（DRAFT→APPROVED）、过账（余额原子累计，CAS 防并发双过账）、
  作废逆运算、月末损益结转、期初余额
- **多租户（引擎级）**：MyBatis-Plus 行级隔离，租户上下文只能来自已验证 JWT
- **确定性计算**：工资（五险一金+专项附加扣除+个税）、增值税/企业所得税/附加税——BigDecimal 全程，可审计可复现
- **AI 集成**：调用 apps/ai 的 AI 服务 做 NL 解析；服务不可用时优雅降级；schema v2 字符串金额契约兼容
- **安全实闸**：JWT 认证 + 租户上下文强制、内部接口共享密钥、高危端点默认禁用、JWT 密钥生产 fail-fast

## 快速开始（30 分钟内）

```bash
git clone <本仓库> && cd apps/backend
mvn -pl ledgerpilot-admin -am package          # 首次构建（国内网络可用 settings-mirror.xml）
java -jar ledgerpilot-admin/target/ledgerpilot-admin.jar --spring.profiles.active=h2 --server.port=8080
# H2 内存库 + 演示数据（admin/123456），无需 MySQL
```

验证：登录 → 建凭证 → 审核 → 过账 → 查科目余额表

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])")

curl -s -X POST http://localhost:8080/api/voucher -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"voucherDate":"2026-10-15","summary":"演示收款","entries":[
    {"subjectCode":"1002","subjectName":"银行存款","direction":"DEBIT","amount":"100.00","summary":"收款"},
    {"subjectCode":"6001","subjectName":"主营业务收入","direction":"CREDIT","amount":"100.00","summary":"收款"}]}'
```

生产部署：MySQL + env 注入（`SPRING_DATASOURCE_*`、`JWT_SECRET` ≥32 字符、`LEDGERPILOT_INTERNAL_TOKEN`），
模板见 `ledgerpilot-admin/src/main/resources/application-local.yml.example`，全配置表见下方配置节。

### 与 AI 服务联调（可选）

docker-compose（mysql + apps/ai 的 AI 服务 + 本服务）见 `docker-compose.yml`；
两仓共享 env：`LEDGERPILOT_API_TOKEN`（Java→AI）、`LEDGERPILOT_INTERNAL_TOKEN`（AI→Java）。

## 我们不宣称（诚实边界）

1. **不宣称「生产级」**——生产级启用判据 = 全部功能验收通过且已知问题清零（三仓统一判据），此前禁用该词；
2. **不宣称「完整多租户 SaaS」**——引擎级行隔离成立，无租户自助注册/开通链路；
3. **不宣称「认证可用于公网」**——v0.1 认证为最小实装（用户表 BCrypt + JWT），无登录限速/密码找回/审计，建议内网或反代后部署；
4. **不宣称「OCR/发票识别可用」**——该模块 v0.1 下架（原为 TODO 桩），列 vNext；
5. **不宣称「报表导出可用」**——Excel 导出为桩；在线报表含利润表/资产负债表/科目余额表；
6. **不宣称「符合企业会计准则标准科目编号」**——内置科目为演示样例（6602=销售费用/6603=管理费用为仓库自有口径，非常规编号），科目表整体可替换；
7. **不宣称「开箱即跑」之外的快速接入**——quickstart 以实测为准；接入业务系统需适配调用方契约；
8. **不宣称「税务计算经权威校验」**——计算器为确定性实现但未经会计师抽审。

## 免责声明

1. 本服务输出为**草稿凭证**，必须经持证会计人员复核后方可用于记账；因直接使用未复核输出产生的后果由使用方承担。
2. 仓库内的科目、费率与演示数据均为样例（合成数据），不构成对会计准则或税法的解释。
3. **个税算法为月度税率表换算**（非工资薪金的累计预扣法），与预扣预缴法定口径存在差异；使用时请以实际适用政策为准。
4. 工资费用科目默认计入 6603（本仓科目基线下的「管理费用」），可通过 `LEDGERPILOT_SALARY_EXPENSE_SUBJECT_*` 配置调整。

## 安全说明

- **历史版本曾在 application.yml 明文提交生产数据库凭据（阿里云 RDS/Redis）与弱 JWT 默认密钥**。
  当前版本已全部 env 化（默认空值 + 生产档 fail-fast），公开仓以净化后的全新历史发布。
  **若你使用过内部版本：请立即轮换数据库密码与 JWT 密钥。**
- 发现安全问题请通过 SECURITY.md 渠道报告，勿公开披露。

## 配置表（全量）

| env | 默认 | 说明 |
|---|---|---|
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | localhost MySQL | 数据源（H2 profile 免配置） |
| `JWT_SECRET` | 空 | **生产必须**：≥32 字符；prod profile 未设置/历史公开串 → 拒绝启动 |
| `JWT_EXPIRATION` | 604800000 | token 有效期（毫秒） |
| `LEDGERPILOT_INTERNAL_TOKEN` | 空 | 服务间内部密钥；空 = 内部费率端点禁用（401） |
| `LEDGERPILOT_API_TOKEN` | 空 | 调 AI 服务携带的 `X-API-Token`（与 apps/ai 的 AI 服务 共用变量名） |
| `AI_SERVICE_BASE_URL` | http://localhost:8000 | AI 服务地址 |
| `AI_SERVICE_TIMEOUT_MS` | 15000 | AI 读超时 |
| `LEDGERPILOT_SYSTEM_RESET_ENABLED` | false | /api/system/reset 高危开关 |
| `LEDGERPILOT_SALARY_EXPENSE_SUBJECT_CODE/NAME` | 6603/管理费用 | 工资凭证费用科目（可配置） |
| `AUTH_TEST_MODE` | false | 测试模式（仅开发） |

## 开发

```bash
mvn -pl ledgerpilot-admin -am verify    # 全量测试 + jacoco 覆盖率门（voucher/common LINE ≥60%）
```

模块：`ledgerpilot-common`（工具/租户/计算器）· `ledgerpilot-auth`（认证）· `ledgerpilot-voucher`（凭证引擎）·
`ledgerpilot-nlp`（AI 客户端）· `ledgerpilot-report`（报表）· `ledgerpilot-admin`（启动装配）。

## License

[Apache-2.0](../../LICENSE)（仓库内各应用统一）。
