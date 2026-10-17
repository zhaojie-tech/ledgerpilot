package com.ledgerpilot.voucher;

import com.ledgerpilot.common.exception.LedgerPilotException;
import com.ledgerpilot.common.util.TenantContext;
import com.ledgerpilot.voucher.dto.VoucherCreateRequest;
import com.ledgerpilot.voucher.service.VoucherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * schema v2 先行步——金额 BigDecimal 承接 + 标度归一
 * （浮点尾巴如 575.2199999999999 在入库前被消灭为 575.22）。
 */
@SpringBootTest(classes = VoucherTestApplication.class, properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:amount_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/h2/schema.sql",
        "spring.sql.init.data-locations=classpath:db/h2/data.sql",
})
class AmountNormalizationTest {

    @Autowired
    private VoucherService voucherService;

    @BeforeEach
    void setUp() {
        VoucherTestApplication.loginAsTenant1();
    }

    @AfterEach
    void tearDown() {
        VoucherTestApplication.logout();
    }

    private Long createWithAmount(String date, String raw) {
        VoucherCreateRequest req = new VoucherCreateRequest();
        req.setVoucherDate(date);
        VoucherCreateRequest.EntryDTO d = new VoucherCreateRequest.EntryDTO();
        d.setSubjectCode("1405");
        d.setSubjectName("库存商品");
        d.setDirection("DEBIT");
        d.setAmount(new BigDecimal(raw));
        d.setSummary("t");
        VoucherCreateRequest.EntryDTO c = new VoucherCreateRequest.EntryDTO();
        c.setSubjectCode("1122");
        c.setSubjectName("应收账款");
        c.setDirection("CREDIT");
        c.setAmount(new BigDecimal(raw));
        c.setSummary("t");
        List<VoucherCreateRequest.EntryDTO> entries = new ArrayList<>();
        entries.add(d);
        entries.add(c);
        req.setEntries(entries);
        return voucherService.createVoucher(req);
    }

    @Test
    void floatTailNormalized() {
        // Double.toString 型浮点尾巴（575.2199999999999）→ 575.22
        String future = LocalDate.now().plusMonths(3).toString();
        Long id = createWithAmount(future, "575.2199999999999");
        var detail = voucherService.getVoucherDetail(id);
        assertEquals(new BigDecimal("575.22"), detail.getEntries().get(0).getAmount());
    }

    @Test
    void scaleNormalized() {
        // 12.3（1 位）→ 12.30（固定 2 位标度）
        String future = LocalDate.now().plusMonths(3).toString();
        Long id = createWithAmount(future, "12.3");
        var detail = voucherService.getVoucherDetail(id);
        assertEquals(new BigDecimal("12.30"), detail.getEntries().get(0).getAmount());
    }

    @Test
    void stringAmountAcceptedByJacksonAndNormalized() {
        // 前端字符串输入 "100.00"（JSON 字符串）由 Jackson 反序列化为 BigDecimal，服务端归一
        String payload = "{\"voucherDate\":\"" + LocalDate.now().plusMonths(3).toString()
                + "\",\"entries\":[{\"subjectCode\":\"1405\",\"subjectName\":\"库存商品\",\"direction\":\"DEBIT\",\"amount\":\"100.005\",\"summary\":\"s\"},"
                + "{\"subjectCode\":\"1122\",\"subjectName\":\"应收账款\",\"direction\":\"CREDIT\",\"amount\":\"100.005\",\"summary\":\"s\"}]}";
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        VoucherCreateRequest req;
        try {
            req = mapper.readValue(payload, VoucherCreateRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Long id = voucherService.createVoucher(req);
        var detail = voucherService.getVoucherDetail(id);
        // 100.005 HALF_UP → 100.01（标度 2）
        assertEquals(new BigDecimal("100.01"), detail.getEntries().get(0).getAmount());
    }

}
