package com.ledgerpilot.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产档 JWT 密钥门禁——未配置/过短/历史公开默认串一律拒绝启动。
 */
class JwtSecretGuardTest {

    private static final String STRONG = "unit-test-secret-0123456789-abcdef-XYZ-0392837465";

    @Test
    void prod_missingSecret_fails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> JwtSecretGuard.validate("", true));
        assertTrue(e.getMessage().contains("未配置"));
    }

    @Test
    void prod_shortSecret_fails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> JwtSecretGuard.validate("short-secret", true));
        assertTrue(e.getMessage().contains("长度不足"));
    }

    @Test
    void prod_historicPublicDefault_fails() {
        assertThrows(IllegalStateException.class,
                () -> JwtSecretGuard.validate("ledgerpilot-jwt-secret-key-2024", true));
        assertThrows(IllegalStateException.class,
                () -> JwtSecretGuard.validate("ledgerpilot-jwt-secret-key-2024-change-in-production", true));
    }

    @Test
    void prod_strongSecret_passes() {
        assertDoesNotThrow(() -> JwtSecretGuard.validate(STRONG, true));
    }

    @Test
    void dev_weakSecret_toleratedWithWarningElsewhere() {
        // 非生产档不在此处拦截（dev 语义 = 启动可用 + 鉴权不可用告警）
        assertDoesNotThrow(() -> JwtSecretGuard.validate("", false));
    }
}
