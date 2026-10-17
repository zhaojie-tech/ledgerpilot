# ADR-001：租户身份信任链

状态：已采纳　关联：apps/ai 的 AI 服务 仓的费率查询鉴权设计

## 决定

1. **业务端点**（/api/** 除豁免）：租户上下文唯一来源 = 认证实闸（AuthenticationFilter）校验后的
   JWT `tenantId` claim。业务层不接受任何其它租户来源（无兜底默认租户）。
2. **服务间内部端点**（/api/rateconfig/internal/*）：只认 `X-Internal-Token` 共享密钥
   （env `LEDGERPILOT_INTERNAL_TOKEN`，与 apps/ai 的 AI 服务 仓共用变量名）。业务 JWT 对其无效；
   业务端点也不认内部 token（作用域二分）。
3. apps/ai 的 AI 服务 回调本仓时：内部 token 证明「服务身份」，`tenantId` 查询参数即目标租户——
   本仓验证 token 后按该参数设置租户上下文并执行查询。
4. apps/ai 的 AI 服务 侧的租户模型演进以本 ADR 为共同边界：内部 token = 服务身份，
   tenant_id 由已认证服务显式传递。

## 后果

- 未配置 LEDGERPILOT_INTERNAL_TOKEN 时内部端点整体禁用（401），防止默认部署暴露跨租户读。
- sys_user 登录/注册为认证前行为，列入租户行级拦截器忽略表（按用户名跨租户查找是设计行为）。
