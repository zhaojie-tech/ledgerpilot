package com.ledgerpilot.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 生成和解析JSON Web Token
 * Token中携带userId和tenantId
 *
 * jjwt 0.11.5 API：HS256 密钥长度必须 ≥ 32 字节（256 位），弱密钥在初始化/生成时直接
 * fail-fast——这是刻意的，见配置项 jwt.secret（生产必须 env 注入，禁止使用历史公开默认串）。
 */
@Slf4j
public class JwtUtil {

    /** HS256 最低密钥长度（字节） */
    public static final int MIN_SECRET_BYTES = 32;

    private static volatile SecretKey signingKey;

    public static void setSecret(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 未配置或长度不足 " + MIN_SECRET_BYTES + " 字节（HS256 要求），拒绝初始化");
        }
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static SecretKey key() {
        SecretKey k = signingKey;
        if (k == null) {
            throw new IllegalStateException("JwtUtil 未初始化（jwt.secret 缺失或未达到最小长度）");
        }
        return k;
    }

    /**
     * 生成Token
     *
     * @param userId       用户ID
     * @param tenantId     租户ID
     * @param expirationMs 有效期（毫秒，来自 jwt.expiration 配置）
     * @param roles        角色列表
     * @return JWT Token字符串
     */
    public static String generate(Long userId, Long tenantId, long expirationMs, String... roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("tenantId", tenantId);
        claims.put("roles", roles);

        long ttl = expirationMs > 0 ? expirationMs : 7 * 24 * 60 * 60 * 1000L;
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttl))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析Token，验证签名和有效期
     *
     * @param token JWT Token
     * @return Claims（包含userId、tenantId、roles）
     * @throws Exception 签名无效或已过期
     */
    public static Claims parse(String token) throws Exception {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 验证Token是否有效（不抛异常）
     */
    public static boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            log.debug("JWT验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从Token中提取userId
     */
    public static Long getUserId(String token) {
        try {
            Claims claims = parse(token);
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从Token中提取tenantId
     */
    public static Long getTenantId(String token) {
        try {
            Claims claims = parse(token);
            return claims.get("tenantId", Long.class);
        } catch (Exception e) {
            return null;
        }
    }
}
