package com.ledgerpilot;

import com.ledgerpilot.common.core.R;
import com.ledgerpilot.common.exception.ForbiddenException;
import com.ledgerpilot.common.exception.LedgerPilotException;
import com.ledgerpilot.common.exception.UnauthorizedException;
import com.ledgerpilot.common.util.JwtUtil;
import com.ledgerpilot.common.util.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统运维端点（高危）。
 *
 * /api/system/reset 重做：
 * - 默认禁用（ledgerpilot.system-reset-enabled=false，未开启时 404 语义的禁用提示）；
 * - 开启后要求：admin JWT（roles 含 ADMIN）+ 显式确认参数 confirm=RESET；
 * - 清理范围 = 当前 JWT 租户（不再硬编码 tenant_id=1）。
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final JdbcTemplate jdbcTemplate;

    @Value("${ledgerpilot.system-reset-enabled:false}")
    private boolean resetEnabled;

    public SystemController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/reset")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> reset(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                         @RequestParam(required = false) String confirm) {
        if (!resetEnabled) {
            throw new ForbiddenException("系统重置端点已禁用（ledgerpilot.system-reset-enabled=false）");
        }
        if (!"RESET".equals(confirm)) {
            throw new LedgerPilotException("RESET_CONFIRM_REQUIRED", "必须显式传入 confirm=RESET");
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("UNAUTHORIZED");
        }
        List<String> roles = extractRoles(authorization.substring(7));
        // 精确集合匹配
        java.util.Set<String> adminRoles = java.util.Set.of("SUPER_ADMIN", "TENANT_ADMIN", "ADMIN");
        if (roles.stream().filter(java.util.Objects::nonNull).noneMatch(adminRoles::contains)) {
            throw new ForbiddenException("仅 ADMIN 系角色可执行系统重置");
        }

        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "缺少租户上下文");
        }
        log.warn("系统重置执行: tenantId={}（高危操作，含审计）", tenantId);
        jdbcTemplate.update("DELETE FROM acc_voucher_entry WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM acc_voucher WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM acc_subject_balance WHERE tenant_id = ?", tenantId);
        jdbcTemplate.update("DELETE FROM acc_period_close WHERE tenant_id = ?", tenantId);
        return R.ok();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractRoles(String token) {
        try {
            Object roles = JwtUtil.parse(token).get("roles");
            return roles instanceof List ? (List<String>) roles : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}
