package com.ledgerpilot.common.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.ledgerpilot.common.util.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * MyBatis-Plus 租户拦截器实现
 *
 * 功能：
 * 1. 自动在所有租户隔离表的SQL中追加 tenant_id 条件
 * 2. 自动排除全局共享表（不追加tenant_id）
 *
 * ⚠️ 重要约束：
 * - 所有租户隔离表查询必须走 MyBatis-Plus 的 BaseMapper 或 XML Mapper
 * - 禁止使用 @Select 原生SQL查询租户表（拦截器不生效）
 * - 如必须使用原生SQL，需手动加 WHERE tenant_id = #{tenantId}
 */
@Component
public class LedgerPilotTenantHandler implements TenantLineHandler {

    /**
     * 全局共享表（不追加tenant_id）
     * 这些表数据全局唯一，不按租户隔离；
     * 其中 sys_user 特殊：登录/注册是认证前行为（无 JWT → 无租户上下文），
     * 按用户名查找必须跨租户，行级隔离由显式 username 条件与业务层保证。
     */
    private static final Set<String> IGNORE_TABLES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "sys_dict",
                    "sys_region",
                    "sys_user",
                    "acc_subject_system",
                    "voucher_template",
                    "voucher_template_entry",
                    "voucher_template_condition"
            ))
    );

    /**
     * 获取当前租户ID
     * 从ThreadLocal中获取（由拦截器在请求入口处设置）
     */
    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            // 非租户场景（如系统初始化）返回-1，不影响正常查询
            return new LongValue(-1L);
        }
        return new LongValue(tenantId);
    }

    /**
     * 获取租户列名
     */
    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    /**
     * 判断是否忽略租户隔离
     * 全局共享表返回true（不追加tenant_id）
     */
    @Override
    public boolean ignoreTable(String tableName) {
        // 忽略表名前缀（如果有的话）
        String name = tableName;
        if (tableName.contains(".")) {
            name = tableName.substring(tableName.lastIndexOf(".") + 1);
        }
        return IGNORE_TABLES.contains(name.toLowerCase());
    }
}
