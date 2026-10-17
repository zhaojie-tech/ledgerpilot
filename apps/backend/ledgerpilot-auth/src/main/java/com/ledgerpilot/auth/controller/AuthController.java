package com.ledgerpilot.auth.controller;

import com.ledgerpilot.auth.dto.LoginRequest;
import com.ledgerpilot.auth.dto.LoginResponse;
import com.ledgerpilot.auth.dto.RegisterRequest;
import com.ledgerpilot.auth.service.AuthService;
import com.ledgerpilot.common.core.R;
import com.ledgerpilot.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

/**
 * 认证接口
 * 负责用户注册、登录、Token刷新
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // 确保 JwtUtil 在处理任何请求前被初始化；密钥缺失/过短时记录严重告警并保持未初始化
        // （登录/鉴权不可用），不阻断进程——生产环境的 fail-fast 由 prod profile 的启动校验承担
        try {
            JwtUtil.setSecret(jwtSecret);
        } catch (IllegalStateException e) {
            log.error("JWT 密钥未就绪：{} —— 登录/鉴权功能不可用；生产环境请通过 JWT_SECRET 注入 ≥32 字符密钥",
                    e.getMessage());
        }
    }

    /**
     * 用户登录
     * POST /api/auth/login
     * Body: { "username": "xxx", "password": "xxx" }
     * Response: { "token": "xxx", "userId": 1, "tenantId": 1, "expiresIn": 604800 }
     */
    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    /**
     * 用户注册
     * POST /api/auth/register
     * Body: { "username": "xxx", "password": "xxx", "companyName": "xxx", "taxpayerType": "SMALL_SCALE" }
     */
    @PostMapping("/register")
    public R<LoginResponse> register(@RequestBody RegisterRequest request) {
        return R.ok(authService.register(request));
    }

    /**
     * 刷新Token
     * POST /api/auth/refresh
     * Header: Authorization: Bearer <token>
     */
    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@RequestHeader(value = "Authorization", required = false) String token) {
        return R.ok(authService.refresh(token));
    }

    /**
     * 获取当前用户信息
     * GET /api/auth/me（需要 Bearer token）
     */
    @GetMapping("/me")
    public R<LoginResponse.UserInfo> me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return R.ok(authService.getCurrentUser(authorization));
    }
}
