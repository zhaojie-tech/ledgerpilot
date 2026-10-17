package com.ledgerpilot.auth.service;

import com.ledgerpilot.auth.dto.LoginRequest;
import com.ledgerpilot.auth.dto.LoginResponse;
import com.ledgerpilot.auth.dto.RegisterRequest;

public interface AuthService {

    /**
     * 用户登录（sys_user 表 BCrypt 校验）
     * @param request 登录请求（用户名+密码）
     * @return 登录成功返回Token和用户信息
     */
    LoginResponse login(LoginRequest request);

    /**
     * 用户注册（强校验：用户名唯一、密码强度；v0.1 全部新用户归属演示租户 1）
     * @param request 注册信息（用户名+密码+公司名+纳税人类型）
     * @return 注册成功返回Token和用户信息
     */
    LoginResponse register(RegisterRequest request);

    /**
     * 刷新Token（只认本实例 tokenStore 签发过的 token；新角色沿用旧记录，无 ADMIN 回退）
     * @param oldToken 旧Token（Bearer前缀）
     * @return 新Token
     */
    LoginResponse refresh(String oldToken);

    /**
     * 获取当前登录用户信息（从 Bearer token 解析，不返回硬编码档案）
     * @param bearerToken Authorization 头（Bearer 前缀）
     */
    LoginResponse.UserInfo getCurrentUser(String bearerToken);
}
