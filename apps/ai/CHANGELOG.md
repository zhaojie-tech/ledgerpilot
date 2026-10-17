# Changelog

## 0.1.0（未发布）

首个公开版本：

### 安全
- 两把内置默认 API key 移除（env 注入 + CI gitleaks/双模式扫描）
- static token 鉴权 + CORS 白名单 + model 白名单 + 输入限长
- knowledge 端点加固（to_thread + 导入锁 + add-then-swap + 白名单目录）

### 契约
- schema v2 契约：有符号 Decimal 字符串序列化（标度 2 HALF_UP）、schema_version=2、
  红字=负数、精确借贷平衡（无容差）、AMOUNT_MISSING 逐条隔离

### 可靠性
- 熔断 L4（超时/5xx/429 计数，4xx 不计；embedding 独立熔断实例；半开原子探针）
- 密钥三态（lifespan fail-fast）；提供商目录与密钥三态；全局并发信号量；token 用量拆分
- /healthz 与 /readyz 拆分（真实探活）

### 会计正确性
- PK-01~08 闸集（白名单/性质方向/辅助核算/价税轧差/金额契约/日期/摘要）
- few-shot 科目码修正（2241→2211、2182→2241、房租 6602→6603）
- 规则层切片（收款/工资/报销，无 key 离线可用）

### 工程与生态
- pydantic-settings 迁移；依赖注入点；ruff 全绿
- bench CLI（rubric 固定四维）+ 留出集 10 档（与调优语料分离）
- 语料贡献格式 + issue 模板；双仓契约 K1-K8（Java 先行 BigDecimal 迁移闭环）
