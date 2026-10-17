package com.ledgerpilot.common.util;

/**
 * 租户上下文持有器
 * 使用ThreadLocal在当前线程中存储租户ID
 * 请求拦截器从JWT提取tenantId设置到此上下文
 */
public class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    /**
     * 获取当前线程的租户ID
     */
    public static Long get() {
        return TENANT_ID.get();
    }

    /**
     * 设置当前线程的租户ID
     */
    public static void set(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 清除当前线程的租户ID（务必在请求结束时调用）
     */
    public static void clear() {
        TENANT_ID.remove();
    }
}
