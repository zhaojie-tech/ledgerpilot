package com.ledgerpilot.common.config;

import com.ledgerpilot.common.interceptor.AuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Web MVC全局配置
 *
 * 认证实闸：AuthenticationFilter 以 servlet filter 注册于 /api/**，
 * 取代旧的 TenantInterceptor（对无效 token 静默放行，已删除）。
 * 租户上下文唯一来源 = 业务 JWT claims；服务间内部端点由控制器校验 X-Internal-Token。
 */
@Configuration
public class WebMvcConfig {

    @Bean
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilter() {
        FilterRegistrationBean<AuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthenticationFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("authenticationFilter");
        return registration;
    }
}
