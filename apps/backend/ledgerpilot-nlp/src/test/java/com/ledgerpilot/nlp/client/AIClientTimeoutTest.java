package com.ledgerpilot.nlp.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ：AIClient 读超时必须真实生效（回归锁——旧实现 @Value 字段注入晚于构造器，
 * readTimeout 恒为 0（无限等待），AI 服务变慢即拖死整个 NLP 线程池）。
 */
class AIClientTimeoutTest {

    private static RestTemplate restTemplateOf(AIClient client) throws Exception {
        Field f = AIClient.class.getDeclaredField("restTemplate");
        f.setAccessible(true);
        return (RestTemplate) f.get(client);
    }

    /** Spring 5.3 的 SimpleClientHttpRequestFactory 无 getReadTimeout，反射读私有字段 */
    private static int readTimeoutOf(SimpleClientHttpRequestFactory factory) throws Exception {
        Field f = SimpleClientHttpRequestFactory.class.getDeclaredField("readTimeout");
        f.setAccessible(true);
        return f.getInt(factory);
    }

    @Test
    void defaultTimeoutIs15s_notZero() throws Exception {
        AiServiceProperties props = new AiServiceProperties();
        assertEquals(15000, props.getTimeoutMs());
        assertEquals("http://localhost:8000", props.getBaseUrl());

        AIClient client = new AIClient(props);
        SimpleClientHttpRequestFactory factory =
                (SimpleClientHttpRequestFactory) restTemplateOf(client).getRequestFactory();
        assertEquals(15000, readTimeoutOf(factory), "读超时必须是配置值而非 0（无限等待）");
    }

    @Test
    void customTimeoutPropagates() throws Exception {
        AiServiceProperties props = new AiServiceProperties();
        props.setTimeoutMs(5000);
        AIClient client = new AIClient(props);
        SimpleClientHttpRequestFactory factory =
                (SimpleClientHttpRequestFactory) restTemplateOf(client).getRequestFactory();
        assertEquals(5000, readTimeoutOf(factory));
    }
}
