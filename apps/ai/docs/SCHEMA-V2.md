# Schema v2 契约（v2）

本文档是响应契约的**当前**定义。变更需升 `schema_version` 并更新 Java 调用方迁移说明。

## 金额契约（PK-06）

| 项 | 值 |
|---|---|
| 字段类型 | `amount: Decimal`（有符号） |
| 符号语义 | 正 = 按 direction 常规方向；**负 = 红字冲销**（不引入独立 red 布尔） |
| 标度 | 固定 2 位（`quantize(0.01, ROUND_HALF_UP)`） |
| JSON 序列化 | **字符串型十进制**：`"10000.00"` / `"-2000.00"`（不是数字） |
| 平衡等式 | Σ(DEBIT 计 +amount / CREDIT 计 −amount) = 0，**精确、无容差** |
| null 金额 | 逐条隔离为该条 `error{code: AMOUNT_MISSING}`，不整单 FAIL |

## 版本字段

- 响应顶层新增 `schema_version: 2`（整型）。
- Java 调用方迁移配方（apps/backend 已完成）：DTO 金额 Double→BigDecimal
  （Jackson 对 JSON 数字与字符串均可反序列化，先行零停机），Python 侧后切字符串。

## 调用方兼容矩阵

| AI 序列化 | 旧 Java（Double） | 新 Java（BigDecimal） |
|---|---|---|
| 数字（v1） | ✓ | ✓（Jackson 收数字） |
| 字符串（v2） | ✗（可能窄化） | ✓ |

因此：切换时点要求 Java 已先行部署 BigDecimal 版（apps/backend 已完成此迁移）。
