package com.ledgerpilot.nlp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * AI 侧以 schema v2 姿态（金额=字符串十进制 + 新增 schema_version/evidence 字段）
 * 发样本，Java 端解析必须通过且金额精确。
 */
class SchemaV2CompatTest {

    private static RestTemplate restTemplateOf(AIClient client) throws Exception {
        Field f = AIClient.class.getDeclaredField("restTemplate");
        f.setAccessible(true);
        return (RestTemplate) f.get(client);
    }

    @Test
    void stringAmountsAndUnknownFieldsAccepted() throws Exception {
        AIClient client = new AIClient(new AiServiceProperties());
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplateOf(client)).build();

        String schemaV2Body = "{"
                + "\"request_id\":\"r1\","
                + "\"schema_version\":2,"
                + "\"evidence\":{\"origin\":\"llm\",\"prompt_version\":\"v1\"},"
                + "\"voucher_date\":\"2026-06-12\","
                + "\"items\":[{"
                + "  \"biz_type\":\"RECEIVE\",\"summary\":\"收 demo 货款\","
                + "  \"entries\":["
                + "    {\"direction\":\"DEBIT\",\"subject_code\":\"1002\",\"subject_name\":\"银行存款\",\"amount\":\"10000.00\",\"summary\":\"s\"},"
                + "    {\"direction\":\"CREDIT\",\"subject_code\":\"1122\",\"subject_name\":\"应收账款\",\"amount\":\"10000.00\",\"summary\":\"s\"}"
                + "  ]"
                + "}],"
                + "\"confidence\":0.9,\"confidence_level\":\"HIGH\","
                + "\"metadata\":{\"model\":\"m\",\"duration_ms\":5,\"token_used\":10,\"source\":\"llm\"}}";

        server.expect(requestTo("http://localhost:8000/api/v1/parse"))
                .andRespond(withSuccess(schemaV2Body, MediaType.APPLICATION_JSON));

        AIClient.AIParseRequest req = new AIClient.AIParseRequest();
        req.setUserInput("测试输入");
        AIClient.AIParseResponse resp = client.parse(req);

        assertEquals("r1", resp.getRequestId());
        assertEquals(1, resp.getItems().size());
        AIClient.AIEntry debit = resp.getItems().get(0).getEntries().get(0);
        assertEquals(new BigDecimal("10000.00"), debit.getAmount(), "字符串十进制金额必须精确承接");
        assertTrue(debit.getSubjectCode().equals("1002"));
    }

    @Test
    void jacksonBigDecimalAcceptsBothJsonNumberAndString() throws Exception {
        // 契约核心事实：Jackson BigDecimal 对 JSON 数字与字符串均可反序列化——
        // 这是 AI 服务切换字符串序列化「零停机」的依据
        ObjectMapper mapper = new ObjectMapper();
        BigDecimal fromString = mapper.readValue("\"10000.00\"", BigDecimal.class);
        BigDecimal fromNumber = mapper.readValue("10000.00", BigDecimal.class);
        assertEquals(0, fromString.compareTo(fromNumber));
    }
}
