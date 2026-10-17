package com.ledgerpilot.auth.dto;

import lombok.Data;

/**
 * 登录响应DTO
 */
@Data
public class LoginResponse {

    /** JWT Token */
    private String token;

    /** Token有效期（秒） */
    private Long expiresIn;

    /** 用户信息 */
    private UserInfo user;

    @Data
    public static class UserInfo {
        private Long userId;
        private Long tenantId;
        private String username;
        private String role; // SUPER_ADMIN / TENANT_ADMIN / USER
    }
}
