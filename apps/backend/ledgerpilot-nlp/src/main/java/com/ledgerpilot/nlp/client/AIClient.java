package com.ledgerpilot.nlp.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

/**
 * AI 服务客户端 — 调用 Python FastAPI 服务
 *
 * 降级策略：Python 服务不可用时返回降级响应。
 * 鉴权：AI 服务开启 static token（LEDGERPILOT_API_TOKEN）时自动携带 X-API-Token 头。
 */
@Slf4j
@Component
public class AIClient {

    private final AiServiceProperties props;
    private final RestTemplate restTemplate;

    public AIClient(AiServiceProperties props) {
        this.props = props;

        // 使用 snake_case 命名策略，匹配 Python Pydantic
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        // Long序列化为String，避免JS精度丢失
        mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                .addSerializer(Long.class, ToStringSerializer.instance)
        );

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(props.getTimeoutMs());

        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.setMessageConverters(Collections.singletonList(converter));
    }

    /** 统一请求头：Content-Type + 可选 X-API-Token */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (props.getToken() != null && !props.getToken().isEmpty()) {
            headers.set("X-API-Token", props.getToken());
        }
        return headers;
    }

    /**
     * 调用 NLP 解析
     */
    public AIParseResponse parse(AIParseRequest request) {
        String url = props.getBaseUrl() + "/api/v1/parse";
        log.info("调用 AI 服务: url={}, userInput={}", url,
                request.getUserInput().length() > 50
                        ? request.getUserInput().substring(0, 50) + "..."
                        : request.getUserInput());

        try {
            long start = System.currentTimeMillis();

            ResponseEntity<AIParseResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, buildHeaders()),
                    AIParseResponse.class
            );

            long duration = System.currentTimeMillis() - start;
            AIParseResponse body = response.getBody();

            if (body != null) {
                body.setDurationMs((int) duration);
                log.info("AI 解析完成: confidence={}, level={}, items={}, duration={}ms, source={}",
                        body.getConfidence(),
                        body.getConfidenceLevel(),
                        body.getItems() != null ? body.getItems().size() : 0,
                        duration,
                        body.getMetadata() != null ? body.getMetadata().getSource() : "unknown");
            }

            return body;

        } catch (Exception e) {
            log.warn("AI 服务调用失败: {}", e.getMessage());
            return AIParseResponse.degraded("AI 服务暂时不可用，请手动录入凭证");
        }
    }

    /**
     * 健康检查
     */
    public boolean isHealthy() {
        try {
            ResponseEntity<PingResponse> response = restTemplate.getForEntity(
                    props.getBaseUrl() + "/api/v1/ping", PingResponse.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("AI 服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取详细健康信息
     */
    public HealthInfo getHealthInfo() {
        try {
            ResponseEntity<HealthInfo> response = restTemplate.getForEntity(
                    props.getBaseUrl() + "/api/v1/health", HealthInfo.class);
            return response.getBody();
        } catch (Exception e) {
            HealthInfo info = new HealthInfo();
            info.setStatus("down");
            info.setMessage(e.getMessage());
            return info;
        }
    }

    // ─── 请求 DTO ───

    @Data
    public static class AIParseRequest {
        private String userInput;
        private Integer tenantId;
        private String requestId;
        private SubjectContext context;
        private ParseOptions options;

        @Data
        public static class SubjectContext {
            private java.util.List<String> subjectList;
            private java.util.List<String> auxDimensions;
            private String taxpayerType;
            private String industry;
        }

        @Data
        public static class ParseOptions {
            private String model;
        }
    }

    // ─── 响应 DTO ───

    @Data
    public static class AIParseResponse {
        private String requestId;
        private String originalInput;
        private String voucherDate;
        private java.util.List<AIEntryItem> items;
        private double confidence;
        private String confidenceLevel;
        private AIValidation validation;
        private AIMetadata metadata;
        private String userNote;  // AI 给用户的提示（如请录入销售成本）
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        private Integer durationMs;

        public static AIParseResponse degraded(String message) {
            AIParseResponse resp = new AIParseResponse();
            resp.setConfidence(0);
            resp.setConfidenceLevel("FAIL");
            AIValidation validation = new AIValidation();
            validation.setValid(false);
            validation.setConfidence(0);
            validation.setConfidenceLevel("FAIL");
            AIValidationError error = new AIValidationError();
            error.setLevel("FATAL");
            error.setMessage(message);
            validation.setErrors(Collections.singletonList(error));
            resp.setValidation(validation);
            AIMetadata meta = new AIMetadata();
            meta.setSource("degraded");
            resp.setMetadata(meta);
            return resp;
        }
    }

    @Data
    public static class AIEntryItem {
        private String bizType;
        private String summary;
        private String contactName;
        private String paymentMethod;
        private java.util.List<AIEntry> entries;
    }

    @Data
    public static class AIEntry {
        private String direction;
        private String subjectCode;
        private String subjectName;
        /**
         * schema v2 先行步：
         * Jackson 对 JSON 数字与字符串均可反序列化为 BigDecimal——AI 服务切换字符串序列化
         * 前后本字段都兼容；新增字段（schema_version/evidence 等）因 FAIL_ON_UNKNOWN_PROPERTIES=false 兼容。
         */
        private java.math.BigDecimal amount;
        private String summary;
        private Map<String, String> aux;
    }

    @Data
    public static class AIValidation {
        private boolean valid;
        private double confidence;
        private String confidenceLevel;
        private java.util.List<AIValidationError> errors;
    }

    @Data
    public static class AIValidationError {
        private String level;
        private String field;
        private String message;
        private String suggestion;
    }

    @Data
    public static class AIMetadata {
        private String model;
        private int durationMs;
        private int tokenUsed;
        private String source;
    }

    @Data
    public static class PingResponse {
        private String message;
        private String timestamp;
    }

    @Data
    public static class HealthInfo {
        private String status;
        private String version;
        private String pythonVersion;
        private Map<String, String> llmProviders;
        private String message;
    }
}
