package com.ledgerpilot.report;

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

/** 仅测试用启动类：装配 common+voucher+report 模块。 */
@SpringBootApplication(scanBasePackages = "com.ledgerpilot", exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@MapperScan("com.ledgerpilot.**.mapper")
public class ReportSmokeTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportSmokeTestApplication.class, args);
    }

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

    public static void loginAsTenant1() {
        TenantContext.set(1L);
    }

    public static void logout() {
        TenantContext.clear();
    }
}
