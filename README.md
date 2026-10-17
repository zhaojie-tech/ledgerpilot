# LedgerPilot（账航）— 可自托管的「AI 做账」平台

**一句话**：浏览器输入一句话业务（「收到张三商贸货款 10000 元」），AI 解析成结构化凭证草稿，
经借贷平衡/科目白名单/期间锁校验后，由人在控制台审核过账——**LLM 全程没有直接落账能力**。

```text
front (Vue3 :3000) ──► backend (Spring Boot :8080) ──► ai (FastAPI :8000)
      人机交互/复核          凭证引擎/余额/报表            自然语言→凭证草稿
```

三栈单仓（`apps/`），契约原子提交；PK-01~08 会计正确性闸 + 熔断降级 + schema v2 金额契约（有符号 Decimal 字符串）。

## 快速开始

### 一条命令试用（all-in-one 演示镜像）

```bash
docker build -f deploy/all-in-one/Dockerfile -t ledgerpilot/all-in-one .
docker run -p 3000:3000 ledgerpilot/all-in-one
```

打开 `http://localhost:3000`，登录 `admin / 123456`。无需任何 API key——NL 解析走确定性规则层
（收款/报销/工资三场景），试试「收到张三商贸货款10000元，银行转账」。重启即重置（H2 演示库）。

### 生产部署（compose 分容器）

```bash
cp deploy/.env.example deploy/.env    # 填 JWT_SECRET（≥32字符）等
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d
```

MySQL 持久化 + 演示科目初始化；接入真实 LLM key 后 NL 解析自动升级为 LLM 路径（多提供商降级+熔断）。

### 各栈二次开发

| 栈 | 目录 | 启动 | 测试 |
|---|---|---|---|
| AI 服务 | `apps/ai` | `uvicorn app.main:app --port 8000`（详见其 README） | `pytest`（94 例，覆盖率门 80%/75%） |
| 后端 | `apps/backend` | `mvn -pl ledgerpilot-admin -am package` → `java -jar …` | `mvn verify`（73 例，jacoco 门） |
| 前端 | `apps/front` | `pnpm dev`（代理到 8080/8000） | `pnpm build`（vue-tsc） |

## 安全模型

- 后端：JWT 认证 + 租户上下文唯一来自 token claims；内部接口独立共享密钥；高危端点默认禁用
- AI：static token 鉴权、模型白名单、输入限长；密钥三态（无 key 可启动 degraded）
- 前端：token 仅存 localStorage，AI token 只在反代层服务端注入（不进浏览器产物）

## 我们不宣称

1. 「生产级」（判据=全部验收 ∧ 真实生产流量验证，当前未满足）；
2. OCR/发票识别（vNext）；Excel 导出（桩）；移动端适配；
3. 权威评测（corpus 12 档 + 留出集 10 档，仅为骨架）；
4. 标准科目编号（内置为演示样例，请用 subject_context 传入企业科目表）。

详见各 `apps/*/README.md` 的完整「我们不宣称」清单。

## 免责声明

输出为**草稿凭证**，须经持证会计复核后方可记账；科目/费率/演示数据均为合成样例；
个税为月度税率表换算口径（非累计预扣法）。

## License

[Apache-2.0](LICENSE)
