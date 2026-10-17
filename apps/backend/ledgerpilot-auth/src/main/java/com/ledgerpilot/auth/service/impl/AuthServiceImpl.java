package com.ledgerpilot.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ledgerpilot.auth.dto.LoginRequest;
import com.ledgerpilot.auth.dto.LoginResponse;
import com.ledgerpilot.auth.dto.RegisterRequest;
import com.ledgerpilot.auth.entity.SysUser;
import com.ledgerpilot.auth.mapper.SysUserMapper;
import com.ledgerpilot.auth.service.AuthService;
import com.ledgerpilot.common.exception.LedgerPilotException;
import com.ledgerpilot.common.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务实现（v0.1 最小实装，见 README「我们不宣称」第 3 条）：
 * - 登录：sys_user 表 BCrypt 校验（不再硬编码 admin/123456）
 * - 注册：用户名唯一 + 密码强度校验；新用户归属演示租户 1（无租户自助开通链路）
 * - 刷新：只认本实例 tokenStore 签发过的 token；角色沿用旧记录，无 ADMIN 回退
 * - token 存储为进程内存（重启全员掉线）；分布式存储与登录限速列 vNext
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    /** 毫秒（yml 默认 604800000 = 7 天；单位历史混乱已统一） */
    @Value("${jwt.expiration:604800000}")
    private long jwtExpiration;

    private final SysUserMapper sysUserMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 进程内 token 记录（v0.1：重启失效） */
    private final Map<String, TokenInfo> tokenStore = new ConcurrentHashMap<>();
    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    private static class TokenInfo {
        final long userId;
        final long tenantId;
        final String username;
        final String role;

        TokenInfo(long userId, long tenantId, String username, String role) {
            this.userId = userId;
            this.tenantId = tenantId;
            this.username = username;
            this.role = role;
        }
    }

    public AuthServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @PostConstruct
    public void init() {
        log.info("AuthServiceImpl initialized (memory token store, DB-backed login)");
    }

    // ============================================================
    // 登录
    // ============================================================
    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())
                .eq(SysUser::getDeleted, 0)
                .last("LIMIT 1"));

        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // 统一失败文案，不区分「用户不存在/密码错误」，防用户名枚举
            throw new LedgerPilotException("LOGIN_FAILED", "用户名或密码错误");
        }

        String role = user.getRole() == null ? "USER" : user.getRole();
        String token = issueToken(user.getId(), user.getTenantId(), user.getUsername(), role);
        return buildLoginResponse(token, user.getId(), user.getTenantId(), user.getUsername(), role);
    }

    // ============================================================
    // 注册
    // ============================================================
    @Override
    public LoginResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        if (username.isEmpty()) {
            throw new LedgerPilotException("REGISTER_INVALID", "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new LedgerPilotException("REGISTER_INVALID", "密码至少 6 位");
        }

        Long existing = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDeleted, 0));
        if (existing != null && existing > 0) {
            throw new LedgerPilotException("REGISTER_DUPLICATE", "用户名已存在");
        }

        // v0.1：无租户自助开通链路，新用户统一归属演示租户 1（README「我们不宣称」第 2 条）
        SysUser user = new SysUser();
        user.setTenantId(1L);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getCompanyName());
        user.setRole("USER");
        user.setStatus(1);
        user.setDeleted(0);
        sysUserMapper.insert(user);

        String token = issueToken(user.getId(), user.getTenantId(), user.getUsername(), "USER");
        return buildLoginResponse(token, user.getId(), user.getTenantId(), user.getUsername(), "USER");
    }

    // ============================================================
    // 刷新Token
    // ============================================================
    @Override
    public LoginResponse refresh(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            throw new LedgerPilotException("TOKEN_INVALID", "无效的Token");
        }
        String actualToken = token.substring(7);

        if (blacklistedTokens.contains(actualToken)) {
            throw new LedgerPilotException("TOKEN_INVALID", "Token已失效，请重新登录");
        }
        TokenInfo oldInfo = tokenStore.get(actualToken);
        // 只认本实例签发过的 token（防止任意签名合法 token 借 refresh 铸新 token）
        if (oldInfo == null || !JwtUtil.validate(actualToken)) {
            throw new LedgerPilotException("TOKEN_INVALID", "Token已过期，请重新登录");
        }

        blacklistedTokens.add(actualToken);
        tokenStore.remove(actualToken);

        String newToken = issueToken(oldInfo.userId, oldInfo.tenantId, oldInfo.username, oldInfo.role);
        return buildLoginResponse(newToken, oldInfo.userId, oldInfo.tenantId, oldInfo.username, oldInfo.role);
    }

    // ============================================================
    // 当前用户
    // ============================================================
    @Override
    public LoginResponse.UserInfo getCurrentUser(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new LedgerPilotException("TOKEN_INVALID", "无效的Token");
        }
        String actualToken = bearerToken.substring(7);
        TokenInfo info = tokenStore.get(actualToken);
        if (info != null) {
            return buildUserInfo(info.userId, info.tenantId, info.username, info.role);
        }
        Long userId = JwtUtil.getUserId(actualToken);
        Long tenantId = JwtUtil.getTenantId(actualToken);
        if (userId == null || tenantId == null) {
            throw new LedgerPilotException("TOKEN_INVALID", "Token解析失败");
        }
        return buildUserInfo(userId, tenantId, "user-" + userId, "USER");
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private String issueToken(long userId, long tenantId, String username, String role) {
        String token = JwtUtil.generate(userId, tenantId, jwtExpiration, role);
        tokenStore.put(token, new TokenInfo(userId, tenantId, username, role));
        return token;
    }

    private LoginResponse buildLoginResponse(String token, long userId, long tenantId,
                                             String username, String role) {
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setExpiresIn(jwtExpiration / 1000);
        resp.setUser(buildUserInfo(userId, tenantId, username, role));
        return resp;
    }

    private LoginResponse.UserInfo buildUserInfo(long userId, long tenantId, String username, String role) {
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setUserId(userId);
        userInfo.setTenantId(tenantId);
        userInfo.setUsername(username);
        userInfo.setRole(role);
        return userInfo;
    }
}
