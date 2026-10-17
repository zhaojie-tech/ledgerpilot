# 安全政策

- 报告：Gitee/GitHub issue 前缀 `[security]`，勿公开细节，72h 内响应。
- 本仓不存储任何密钥；`VITE_` 前缀变量会打进浏览器产物，禁止放置 token/密钥。
- AI 服务的 X-API-Token 仅在 dev 代理与 nginx 反代层服务端注入，不进浏览器。
