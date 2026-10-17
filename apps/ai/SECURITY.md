# 安全政策

## 报告漏洞

请通过 Gitee/GitHub issue（标题前缀 `[security]`，细节可留空待私下沟通）联系维护者。
**请勿公开披露细节**。我们会在 72 小时内确认。

## 安全机制速览

- `LEDGERPILOT_API_TOKEN`：static token（`X-API-Token` 头），豁免仅 /ping、/health、文档路径
- `ALLOWED_MODELS`：options.model 白名单，防指定任意模型烧 key
- `LEDGERPILOT_INTERNAL_TOKEN`：与 Java 后端（apps/backend）共享的服务间密钥，未配置=内部费率端点禁用
- 密钥一律 env 注入、代码默认空值；CI gitleaks + 双模式扫描
