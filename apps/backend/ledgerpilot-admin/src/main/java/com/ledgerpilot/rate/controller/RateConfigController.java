package com.ledgerpilot.rate.controller;

import com.ledgerpilot.common.calculator.dto.SalaryCalculateRequest;
import com.ledgerpilot.common.calculator.dto.SalaryCalculateResponse;
import com.ledgerpilot.common.calculator.service.SalaryCalculatorService;
import com.ledgerpilot.common.core.R;
import com.ledgerpilot.common.exception.LedgerPilotException;
import com.ledgerpilot.common.exception.UnauthorizedException;
import com.ledgerpilot.common.util.TenantContext;
import com.ledgerpilot.common.entity.RateConfig;
import com.ledgerpilot.rate.service.RateConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 费率配置管理接口 + 工资计算接口
 * 支持社保、公积金、增值税、企业所得税、固定资产折旧等费率配置
 */
@RestController
public class RateConfigController {

    private final RateConfigService rateConfigService;
    private final SalaryCalculatorService salaryCalculatorService;
    private final InternalTokenGuard internalTokenGuard;

    @Autowired
    public RateConfigController(RateConfigService rateConfigService,
                                SalaryCalculatorService salaryCalculatorService,
                                InternalTokenGuard internalTokenGuard) {
        this.rateConfigService = rateConfigService;
        this.salaryCalculatorService = salaryCalculatorService;
        this.internalTokenGuard = internalTokenGuard;
    }

    /**
     * 获取当前租户的全部费率配置（可选按分类过滤）
     * @param category 费率分类：social/housing/vat/corp_tax/depreciation
     */
    @GetMapping("/api/rateconfig/list")
    public R<List<RateConfig>> list(@RequestParam(required = false) String category) {
        Long tenantId = TenantContext.get();
        List<RateConfig> result = rateConfigService.listByTenant(tenantId, category);
        return R.ok(result);
    }

    /**
     * 批量保存/更新费率配置
     * 按 tenantId + category + subCategory 唯一键判断新增或更新
     */
    @PostMapping("/api/rateconfig/save")
    public R<?> save(@RequestBody List<RateConfig> configs) {
        Long tenantId = TenantContext.get();
        rateConfigService.saveBatch(configs, tenantId);
        return R.ok();
    }

    /**
     * 工资计算接口
     * 根据 rate_config 中的实际费率，计算工资发放及相关社保公积金扣款
     *
     * @param request 工资计算请求
     * @return 完整工资条明细（个人扣款、单位缴费、个税、实发工资）
     */
    @PostMapping("/api/calculator/salary")
    public R<SalaryCalculateResponse> calculateSalary(@RequestBody SalaryCalculateRequest request) {
        Long tenantId = TenantContext.get();
        if (request.getTenantId() == null) {
            request.setTenantId(tenantId);
        }
        if (request.getGrossSalary() == null || request.getGrossSalary().compareTo(BigDecimal.ZERO) <= 0) {
            return R.fail("grossSalary（应发工资）不能为空且必须大于0");
        }
        SalaryCalculateResponse result = salaryCalculatorService.calculate(request);
        return R.ok(result);
    }

    /**
     * 内部接口：供 AI 解析服务调用，通过 query param 指定 tenantId。
     *
     * 安全模型（作用域二分）：本端点只认 X-Internal-Token 共享密钥（LEDGERPILOT_INTERNAL_TOKEN，
     * 与 AI 服务共享），业务 JWT 一律不放行；业务端点也不认内部 token。
     * 未配置密钥 = 端点禁用（403），防止开源默认部署暴露跨租户读。
     */
    @GetMapping("/api/rateconfig/internal/list")
    public R<List<RateConfig>> internalList(@RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
                                            @RequestParam Long tenantId,
                                            @RequestParam(required = false) String category) {
        if (!internalTokenGuard.verify(internalToken)) {
            throw new UnauthorizedException("INTERNAL_AUTH_FAILED");
        }
        // 信任链：X-Internal-Token 证明服务身份后，tenantId 参数即 AI 侧请求的目标租户；
        // MP 租户拦截器需要该上下文（豁免路径无 JWT，必须显式设置）
        TenantContext.set(tenantId);
        try {
            List<RateConfig> result = rateConfigService.listByTenant(tenantId, category);
            return R.ok(result);
        } finally {
            TenantContext.clear();
        }
    }

    /** 内部共享密钥校验器（常量时间比较） */
    @org.springframework.stereotype.Component
    public static class InternalTokenGuard {
        private final String configuredToken;

        public InternalTokenGuard(@org.springframework.beans.factory.annotation.Value(
                "${ledgerpilot.internal-token:}") String configuredToken) {
            this.configuredToken = configuredToken == null ? "" : configuredToken;
        }

        /** 校验请求携带的内部 token；未配置密钥时一律拒绝（端点禁用） */
        public boolean verify(String provided) {
            if (configuredToken.isEmpty() || provided == null || provided.isEmpty()) {
                return false;
            }
            return java.security.MessageDigest.isEqual(
                    configuredToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
