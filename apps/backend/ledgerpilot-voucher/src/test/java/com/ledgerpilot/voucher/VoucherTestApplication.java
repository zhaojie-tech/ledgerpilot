package com.ledgerpilot.voucher;

import com.ledgerpilot.common.calculator.service.RateConfigServiceInterface;
import com.ledgerpilot.common.entity.RateConfig;
import com.ledgerpilot.common.util.TenantContext;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.List;

/**
 * 仅测试用启动类：装配 common+voucher 模块（不含 admin/auth/nlp/report）。
 */
@SpringBootApplication(scanBasePackages = "com.ledgerpilot", exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@MapperScan("com.ledgerpilot.**.mapper")
public class VoucherTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoucherTestApplication.class, args);
    }

    /**
     * 费率服务桩：RateConfigServiceInterface 的实现位于 ledgerpilot-admin（不在本测试类路径），
     * common 的工资/税务计算器依赖该接口——凭证测试用不到费率，给空实现即可。
     */
    @Bean
    RateConfigServiceInterface stubRateConfigService() {
        return new RateConfigServiceInterface() {
            @Override
            public List<RateConfig> listByTenant(Long tenantId, String category) {
                return Collections.emptyList();
            }

            @Override
            public RateConfig getByCategory(Long tenantId, String category, String subCategory) {
                return null;
            }
        };
    }

    /** 测试统一使用租户 1 */
    public static void loginAsTenant1() {
        TenantContext.set(1L);
    }

    public static void logout() {
        TenantContext.clear();
    }
}
