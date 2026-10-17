package com.ledgerpilot.common.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JwtUtil（jjwt 0.11.5 迁移后）行为锁：密钥门禁 + 生成/解析往返 + 租户 claim。 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-jwt-secret-0123456789abcdef-XYZ";

    @BeforeEach
    void setUp() {
        JwtUtil.setSecret(SECRET);
    }

    @Test
    void weakSecretRejected() {
        assertThrows(IllegalStateException.class, () -> JwtUtil.setSecret("short"));
        assertThrows(IllegalStateException.class, () -> JwtUtil.setSecret(null));
    }

    @Test
    void generateParseRoundTrip() throws Exception {  // parse 声明受检异常
        String token = JwtUtil.generate(42L, 7L, 604800000L, "ADMIN");
        Claims claims = JwtUtil.parse(token);
        assertEquals("42", claims.getSubject());
        assertEquals(7L, claims.get("tenantId", Long.class));
    }

    @Test
    void extractorsAndValidation() {
        String token = JwtUtil.generate(42L, 7L, 604800000L, "USER");
        assertEquals(42L, JwtUtil.getUserId(token));
        assertEquals(7L, JwtUtil.getTenantId(token));
        assertTrue(JwtUtil.validate(token));
        assertFalse(JwtUtil.validate("forged-token"));
        assertNull(JwtUtil.getUserId("garbage"));
    }

    @Test
    void uninitializedFailsFast() throws Exception {
        // 清空 signingKey 后再生成必须失败（服务端密钥未就绪的防御）
        // 注意：静态状态影响其它用例，故最后恢复
        try {
            FieldReset.clear();
            assertThrows(IllegalStateException.class, () -> JwtUtil.generate(1L, 1L, 1000L, "USER"));
        } finally {
            JwtUtil.setSecret(SECRET);
        }
    }

    /** 反射清空静态 signingKey */
    private static final class FieldReset {
        static void clear() throws Exception {
            java.lang.reflect.Field f = JwtUtil.class.getDeclaredField("signingKey");
            f.setAccessible(true);
            f.set(null, null);
        }
    }
}
