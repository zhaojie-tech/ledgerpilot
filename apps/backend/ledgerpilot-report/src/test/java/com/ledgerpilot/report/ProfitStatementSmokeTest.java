package com.ledgerpilot.report;

import com.ledgerpilot.common.util.TenantContext;
import com.ledgerpilot.report.service.ReportService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 报表只读冒烟：已过账的损益凭证后，利润表收入/费用与凭证发生额一致
 * （利润表借贷汇总恒等的只读断言）。
 */
@SpringBootTest(classes = ReportSmokeTestApplication.class, properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:report_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/h2/schema.sql",
        "spring.sql.init.data-locations=classpath:db/h2/data.sql",
})
class ProfitStatementSmokeTest {

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        ReportSmokeTestApplication.loginAsTenant1();
    }

    @AfterEach
    void tearDown() {
        ReportSmokeTestApplication.logout();
    }

    private Long createAndPost(String debitSubject, String creditSubject, String amount) {
        VoucherCreateRequest req = new VoucherCreateRequest();
        req.setVoucherDate(LocalDate.now().toString());
        req.setBusinessType("TEST");
        VoucherCreateRequest.EntryDTO d = new VoucherCreateRequest.EntryDTO();
        d.setSubjectCode(debitSubject);
        d.setSubjectName("借");
        d.setDirection("DEBIT");
        d.setAmount(new BigDecimal(amount));
        d.setSummary("报表冒烟");
        VoucherCreateRequest.EntryDTO c = new VoucherCreateRequest.EntryDTO();
        c.setSubjectCode(creditSubject);
        c.setSubjectName("贷");
        c.setDirection("CREDIT");
        c.setAmount(new BigDecimal(amount));
        c.setSummary("报表冒烟");
        List<VoucherCreateRequest.EntryDTO> entries = new ArrayList<>();
        entries.add(d);
        entries.add(c);
        req.setEntries(entries);
        Long id = voucherService.createVoucher(req);
        voucherService.approveVoucher(id);
        voucherService.postVoucher(id);
        return id;
    }

    @Test
    void profitStatementReflectsPostedPnl() {
        // 收入 100（6001 贷）、成本 40（6401 借）→ 利润表收入合计 100、成本合计 40
        createAndPost("1002", "6001", "100.00");
        createAndPost("6401", "1002", "40.00");

        Object obj = reportService.getProfitStatement(LocalDate.now().getYear(), LocalDate.now().getMonthValue());
        assertNotNull(obj);
        com.ledgerpilot.report.vo.ProfitStatementVO vo =
                (com.ledgerpilot.report.vo.ProfitStatementVO) obj;
        // 只读冒烟断言：利润表反映已过账损益发生额
        assertEquals(new BigDecimal("100.00"), vo.getOperatingRevenue());
        assertEquals(new BigDecimal("100.00"), vo.getMainBusinessRevenue());
        assertEquals(new BigDecimal("40.00"), vo.getOperatingCost());
        assertEquals(new BigDecimal("40.00"), vo.getMainBusinessCost());
    }
}
