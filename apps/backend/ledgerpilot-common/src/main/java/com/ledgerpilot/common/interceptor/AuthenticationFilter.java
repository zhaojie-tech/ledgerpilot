package com.ledgerpilot.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerpilot.common.core.R;
import com.ledgerpilot.common.exception.UnauthorizedException;
import com.ledgerpilot.common.util.JwtUtil;
import com.ledgerpilot.common.util.TenantContext;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 认证实闸：业务端点一律要求有效 Bearer JWT，租户上下文只能来自 JWT claims。
 *
 * 作用域二分：
 * - 业务端点（/api/**，除豁免清单）：只认 JWT；
 * - 服务间内部端点（/api/rateconfig/internal/*）：只认 X-Internal-Token，在本过滤器放行，
 *   由 RateConfigController.InternalTokenGuard 校验；业务 JWT 对 internal 端点无意义。
 *
 * 豁免清单（仅此四个）：/api/auth/login、/api/auth/register、/api/auth/refresh、/api/auth/me 之外的
 * /api/auth/** 不存在；actuator 不在 /api/** 之下，天然不受本过滤器约束。
 */
@Slf4j
public class AuthenticationFilter extends OncePerRequestFilter {

    /** 登录/注册不需要 JWT（刷新与 me 需要） */
    private static final List<String> EXEMPT_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/register"
    );

    /** 服务间内部端点前缀：不走 JWT（由控制器校验 X-Internal-Token） */
    private static final String INTERNAL_PREFIX = "/api/rateconfig/internal/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (EXEMPT_PATHS.contains(path) || path.startsWith(INTERNAL_PREFIX)) {
            try {
                chain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new UnauthorizedException("UNAUTHORIZED", "缺少 Bearer token");
            }
            Claims claims;
            try {
                claims = JwtUtil.parse(authorization.substring(7));
            } catch (IllegalStateException e) {
                // JwtUtil 未初始化（服务端密钥未配置）：按 503 语义处理，提示部署问题
                writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        R.fail("JWT_NOT_READY", "服务端鉴权组件未就绪（JWT 密钥未配置），请联系部署方"));
                return;
            } catch (Exception e) {
                throw new UnauthorizedException("UNAUTHORIZED", "token 无效或已过期");
            }

            Long tenantId = claims.get("tenantId", Long.class);
            if (tenantId == null) {
                throw new UnauthorizedException("UNAUTHORIZED", "token 缺少租户信息");
            }
            TenantContext.set(tenantId);
            chain.doFilter(request, response);
        } catch (UnauthorizedException e) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, R.fail(e.getMessage(), "认证失败或未授权"));
        } finally {
            TenantContext.clear();
        }
    }

    private void writeJson(HttpServletResponse response, int status, R<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
