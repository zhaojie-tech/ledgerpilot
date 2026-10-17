package com.ledgerpilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * LedgerPilot 智能做账SaaS平台 启动类
 *
 * 整合所有子模块，提供统一的REST API入口
 */
@SpringBootApplication(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
})
@ComponentScan(basePackages = {"com.ledgerpilot"})
@MapperScan("com.ledgerpilot.**.mapper")
public class LedgerPilotAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerPilotAdminApplication.class, args);
    }
}
