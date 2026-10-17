package com.ledgerpilot.nlp.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 服务连接配置（ai-service.*，见 application.yml 与 .env 纪律）。
 *
 * 以构造注入交给 AIClient——修复旧实现「@Value 字段注入晚于构造器，readTimeout 恒为 0
 * （=无限等待）」的配置死旋钮问题。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai-service")
public class AiServiceProperties {

    /** AI 服务基址（默认对齐 apps/ai 的 AI 服务 的 8000 端口） */
    private String baseUrl = "http://localhost:8000";

    /** 读超时（毫秒） */
    private int timeoutMs = 15000;

    /** AI 服务鉴权 token（env LEDGERPILOT_API_TOKEN，与 apps/ai 的 AI 服务 共用变量名；空 = 不带头） */
    private String token = "";
}
