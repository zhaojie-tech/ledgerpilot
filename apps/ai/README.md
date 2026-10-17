# LedgerPilot AI Service（apps/ai 的 AI 服务）

**把「会计上错在哪」变成代码可验证**——8 道解析侧校验闸（PK-01~08）
+ 留出纪律评测骨架 + 逐响应证据字段；自然语言进，结构化凭证 JSON 出。

本仓库的调用方 [apps/backend](../backend)（Java 凭证引擎）承接「校验入账 → 余额/报表」，
**[apps/front](../front)**（Vue3 控制台）负责人机交互与复核界面；
三仓（front/end/ai）合起来是完整的「AI 做账」网站项目：**LLM 在整条链路上没有直接落账能力**——
AI 输出必须通过借贷平衡、科目白名单、期间锁校验，且生成的是草稿凭证，仍需人工审核。

## 它是什么

FastAPI 解析服务：一句话业务描述（如「收到张三商贸货款 10000 元，银行转账」）→
结构化凭证草稿 JSON（业务条目 + 分录 + 辅助核算 + 置信度 + 逐响应证据链）。

- **6 步管线**：预处理 → RAG（可选）→ Prompt → LLM（多提供商降级）→ JSON 解析 → PK 闸集校验
- **PK-01~08 会计正确性闸**：精确借贷平衡、科目白名单、性质/方向、辅助核算、价税轧差、金额契约、日期、摘要
- **熔断与降级**：超时/5xx/429 连续失败开路（4xx 不计），失败兜底确定性规则层
- **schema v2 金额契约**：有符号 Decimal（负=红字），字符串十进制序列化，标度固定 2 位
- **规则层**：收款/工资/报销三场景确定性解析，**无 key 可离线运行**

## 快速开始

```bash
git clone <本仓库> && cd apps/ai 的 AI 服务
python3.11 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# 可选：cp .env.example .env 填入 key；不填也能启动（规则兜底 + PK 闸照常工作）
uvicorn app.main:app --host 127.0.0.1 --port 8000

curl http://127.0.0.1:8000/api/v1/ping
```

调用 `/api/v1/parse`（无 key 时走规则兜底，仅覆盖收款/工资/报销等高频场景）：

```bash
curl -s -X POST http://127.0.0.1:8000/api/v1/parse -H 'Content-Type: application/json' \
  -d '{"user_input": "收到张三商贸货款10000元，银行转账", "tenant_id": 1}'
```

注意：`tenant_id` 必须为整数；`user_input` ≤1000 字符。

### 无 key 行为

| 组件 | 未配置时 |
|---|---|
| LLM 提供商 | 自动禁用；解析走规则兜底或返回 FAIL（不崩溃、不外呼） |
| RAG（智谱 embedding） | 检索关闭，`evidence.rag_refs` 为空 |
| 费率注入 | 不注入 |

## 安全模型（生产部署必读）

| env | 默认 | 说明 |
|---|---|---|
| `LEDGERPILOT_API_TOKEN` | 空（开放+WARNING） | static token，`X-API-Token` 头；**生产必须设置** |
| `CORS_ORIGINS` | 空 | 白名单；含 `*` 强制关 credentials |
| `ALLOWED_MODELS` | deepseek-chat,qwen-plus | model 白名单防烧 key |
| `LEDGERPILOT_INTERNAL_TOKEN` | 空 | 与 Java 后端共享的服务间密钥 |
| `LLM_REQUIRED` | false | true 且零可用提供商 → 拒绝启动 |

完整配置见 [.env.example](.env.example)。

## 「我们不宣称」

1. **「生产级」**——启用判据 = 可靠性设施全部落地且已知问题清零（当前已实现并测试，但尚无生产流量验证）；
2. **「多租户费率注入超前」**——实为自家内网 HTTP 依赖 + file SPI（扩展点已就绪）；
3. **「RAG 中文嵌入开箱可用」**——依赖智谱云 key，无 key 静默关闭；
4. **「科目合法/幻觉检测」**——白名单闸已有，但内置科目为演示样例（非标准科目编号），需用户以 subject_context 传入真实科目表；
5. **「few-shot 经抽验基本正确」**——工资示例曾两处科目码错误，已修复（2241→2211、2182→2241）并以测试锁死；
6. **「权威评测榜单」**——仅 12 档 corpus + 10 档留出集骨架，不构成权威评测；
7. **「外部用户 30 分钟跑通」**——quickstart 路径已实测，但生产接入需适配调用方契约。

## 免责声明

1. 本服务输出为**草稿凭证**，必须经持证会计人员复核后方可用于记账。
2. 仓库内科目、费率、示例均为合成样例，不构成对会计准则或税法的解释。
3. 个税相关输出为**月度税率表换算口径**（非累计预扣法），仅作演示参考。

## 评测

```bash
# 留出集 10 档（与 prompt 调优语料严格分离——留出纪律）
python -m bench.bench_cli --base-url https://api.deepseek.com/v1 \
    --model deepseek-chat --api-key-env DEEPSEEK_API_KEY
```

rubric 固定：可解析率 / 平衡（精确）/ 科目方向（三键对齐）/ 金额（精确 Decimal）。

## 开发

```bash
pip install -r requirements-dev.txt
coverage run -m pytest && coverage report    # 80 tests, corpus 零外呼
ruff check app/ tests/
```

CI：fast（ruff+mypy 范围+tests+corpus）/ slow（错误注入+bench smoke+干净容器安装）。

## License

[Apache-2.0](../../LICENSE)（仓库内各应用统一）。
