# 贡献指南

1. `pnpm install` 后 `pnpm build`（含 vue-tsc 类型检查）必须通过；
2. 新页面需挂路由 + 菜单；金额展示遵守字符串十进制契约（schema v2）；
3. 提交信息说明「改了什么+为什么」；DCO：`git commit -s`；
4. 禁止提交密钥/真实财务数据（CI gitleaks + 模式扫描）。
