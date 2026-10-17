package com.ledgerpilot.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 生产档 JWT 密钥门禁：
 * 当激活 prod/production profile 时，jwt.secret 未配置、长度不足 32 字节、
 * 或等于任何历史公开默认串 → 上下文初始化失败，应用拒绝启动。
 *
 * 历史背景：旧版本曾把 "ledgerpilot-jwt-secret-key-2024(-change-in-production)"
 * 提交在配置文件中，开源后即为公开常量——凡出现即视为未配置。
 */
@Component
@org.springframework.context.annotation.Profile({"prod", "production"})
public class JwtSecretGuard implements InitializingBean, EnvironmentAware {

    static final List<String> BANNED_DEFAULTS = List.of(
            "ledgerpilot-jwt-secret-key-2024",
            "ledgerpilot-jwt-secret-key-2024-change-in-production"
    );

    private Environment environment;

    @Value("${jwt.secret:}")
    private String secret;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        validate(secret, profiles.contains("prod") || profiles.contains("production"));
    }

    /** 可单测的校验核心：仅生产档强制；dev/h2 档由 AuthController 的告警语义承担 */
    static void validate(String secret, boolean production) {
        if (!production) {
            return;
        }
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("生产档启动失败：JWT_SECRET 未配置（必须 env 注入 ≥32 字符随机密钥）");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("生产档启动失败：JWT_SECRET 长度不足 32 字节（HS256 要求）");
        }
        if (BANNED_DEFAULTS.contains(secret)) {
            throw new IllegalStateException("生产档启动失败：JWT_SECRET 为历史公开默认值，禁止使用");
        }
    }
}
