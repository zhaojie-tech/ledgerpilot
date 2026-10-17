# 贡献指南

## 提交纪律

1. **改生产代码必须同步改/加单测**（`mvn -pl ledgerpilot-admin -am verify` 全绿是发版门禁）；
2. 覆盖率门：`ledgerpilot-voucher` 与 `ledgerpilot-common` LINE ≥60%（jacoco 绑定 verify）；
3. 提交信息说明「改了什么 + 为什么」；金额相关改动必须附带 BigDecimal 精度语义说明；
4. 禁止 `--no-verify`；禁止把任何密钥/真实财务数据带进仓库（CI 有 gitleaks + 双模式扫描）。

## 开发流程

1. Fork → 分支 → 改动 → `mvn -pl ledgerpilot-admin -am verify` 全绿；
2. 发 PR，描述对照「我们不宣称」8 条自查文案是否越界；
3. DCO：提交时加 `Signed-off-by:`（`git commit -s`）。

## 本地网络

国内网络可用 `settings-mirror.xml`（阿里云 Maven 镜像）：
`mvn -s settings-mirror.xml ...`。
