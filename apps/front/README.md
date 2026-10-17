# LedgerPilot Front（apps/front）

**LedgerPilot 智能做账平台的 Web 控制台**——Vue 3 + TypeScript + Element Plus + Vite。

三仓组成完整网站：本仓（Web 控制台）→ [apps/backend](../backend)（Java 凭证引擎，:8080）→ [apps/ai 的 AI 服务](../ai)（Python AI 解析服务，:8000）。**LLM 在整条链路上没有直接落账能力**：解析结果只是草稿凭证，须经本控制台人工审核过账。

## 快速开始（开发模式）

```bash
pnpm install --registry=https://registry.npmmirror.com
pnpm dev            # http://localhost:3000，/api 与 /ai-api 自动代理到本机 8080/8000
```

登录：`admin / 123456`（后端 H2 演示库；生产请改用户表并轮换）。

## 生产构建

```bash
pnpm build          # 产物 dist/，SPA（history 路由需 nginx fallback）
```

生产部署由 nginx 同源反代（见 `deploy/nginx.conf.template`）：`/api` → 后端、
`/ai-api` → AI 服务（由环境变量注入 `X-API-Token`，token 不进浏览器产物）。

## 页面

登录 · 智能做账（NL 解析/一键建凭）· 凭证（列表/创建/详情/过账）· 期初余额 · 科目 ·
报表中心（利润表/资产负债表/科目余额）· 税务中心 · 知识库管理 · 设置。

## 我们不宣称

1. 不宣称「生产级」——与其余两仓统一判据（全部功能验收通过且已知问题清零）；
2. 不宣称完整覆盖后端全部端点（部分后端能力未出 UI）；
3. 不宣称 OCR/发票识别可用（与后端  对齐，vNext）；
4. 不宣称适配移动端（桌面优先）。

## 免责声明

1. 平台输出为**草稿凭证**，须经持证会计人员复核后方可记账。
2. 科目、费率、演示数据均为合成样例。

## License

[Apache-2.0](../../LICENSE)（仓库内各应用统一）。
