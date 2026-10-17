package com.ledgerpilot.voucher;

import com.ledgerpilot.common.exception.LedgerPilotException;
import com.ledgerpilot.common.util.TenantContext;
import com.ledgerpilot.voucher.dto.VoucherCreateRequest;
import com.ledgerpilot.voucher.entity.AccSubjectBalance;
import com.ledgerpilot.voucher.mapper.AccSubjectBalanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 点：并发正确性负例——同一凭证并发双过账，必须恰一笔成功、余额只累计一次；
 * 以及余额原子累计的基本守恒（过账/作废往返后余额归零）。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(classes = VoucherTestApplication.class, properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:voucher_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:db/h2/schema.sql",
        "spring.sql.init.data-locations=classpath:db/h2/data.sql",
})
class VoucherPostingConcurrencyTest {

    @Autowired
    private com.ledgerpilot.voucher.service.VoucherService voucherService;

    @Autowired
    private AccSubjectBalanceMapper balanceMapper;

    @BeforeEach
    void setUp() {
        VoucherTestApplication.loginAsTenant1();
    }

    @AfterEach
    void tearDown() {
        VoucherTestApplication.logout();
    }

    private Long createAndApproveVoucher(String debitSubject, String creditSubject, String amount) {
        return createAndApproveVoucher(LocalDate.now().toString(), debitSubject, creditSubject, amount);
    }

    private Long createAndApproveVoucher(String date, String debitSubject, String creditSubject, String amount) {
        VoucherCreateRequest req = new VoucherCreateRequest();
        req.setVoucherDate(date);
        req.setBusinessType("TEST");
        req.setSourceType("TEST");
        VoucherCreateRequest.EntryDTO d = new VoucherCreateRequest.EntryDTO();
        d.setSubjectCode(debitSubject);
        d.setSubjectName("测试借方");
        d.setDirection("DEBIT");
        d.setAmount(new java.math.BigDecimal(amount));
        d.setSummary("并发测试");
        VoucherCreateRequest.EntryDTO c = new VoucherCreateRequest.EntryDTO();
        c.setSubjectCode(creditSubject);
        c.setSubjectName("测试贷方");
        c.setDirection("CREDIT");
        c.setAmount(new java.math.BigDecimal(amount));
        c.setSummary("并发测试");
        List<VoucherCreateRequest.EntryDTO> entries = new ArrayList<>();
        entries.add(d);
        entries.add(c);
        req.setEntries(entries);

        Long id = voucherService.createVoucher(req);
        voucherService.approveVoucher(id);
        return id;
    }

    private AccSubjectBalance balanceOf(String subjectCode) {
        return balanceMapper.selectOne(new LambdaQueryWrapper<AccSubjectBalance>()
                .eq(AccSubjectBalance::getTenantId, 1L)
                .eq(AccSubjectBalance::getSubjectCode, subjectCode)
                .eq(AccSubjectBalance::getFiscalYear, LocalDate.now().getYear())
                .eq(AccSubjectBalance::getPeriodNo, LocalDate.now().getMonthValue()));
    }

    @Test
    @Order(2)
    void postThenBalanceAccruedOnce() {
        Long id = createAndApproveVoucher("1001", "6001", "100.00");
        voucherService.postVoucher(id);

        AccSubjectBalance debit = balanceOf("1001");
        AccSubjectBalance credit = balanceOf("6001");
        assertEquals(new BigDecimal("100.00"), debit.getCurrentDebit());
        assertEquals(new BigDecimal("100.00"), credit.getCurrentCredit());
        assertEquals(new BigDecimal("100.00"), debit.getEndDebit());
        assertEquals(BigDecimal.ZERO.compareTo(credit.getEndDebit()), 0);
    }

    @Test
    @Order(1)
    void concurrentDoublePost_exactlyOneWins_balanceAccruedOnce() throws Exception {
        Long id = createAndApproveVoucher("1002", "6051", "200.00");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<String> failureMessages = java.util.Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                // TenantContext 是 ThreadLocal：worker 线程需自行建立上下文（生产由认证过滤器在每个请求线程建立）
                VoucherTestApplication.loginAsTenant1();
                ready.countDown();
                try {
                    go.await(5, TimeUnit.SECONDS);
                    voucherService.postVoucher(id);
                    success.incrementAndGet();
                } catch (LedgerPilotException e) {
                    conflict.incrementAndGet();
                    failureMessages.add(e.getMessage());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    VoucherTestApplication.logout();
                }
            }));
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(1, success.get(), "并发双过账必须恰一笔成功，实际失败原因: " + failureMessages);
        assertEquals(1, conflict.get(), "另一笔必须以状态冲突失败");
        AccSubjectBalance debit = balanceOf("1002");
        assertEquals(new BigDecimal("200.00"), debit.getCurrentDebit(), "余额只允许累计一次");
    }

    @Test
    @Order(5)
    void openingBalances_saveAndRead() {
        // 期初余额：往期期间（1 月）无凭证，可保存并回读（C2 统一口径后 end 公式验证）
        int year = LocalDate.now().getYear();
        com.ledgerpilot.voucher.dto.OpeningBalanceDTO dto = new com.ledgerpilot.voucher.dto.OpeningBalanceDTO();
        dto.setFiscalYear(year);
        dto.setPeriodNo(1);
        List<com.ledgerpilot.voucher.dto.OpeningBalanceDTO.SubjectBalance> balances = new ArrayList<>();
        com.ledgerpilot.voucher.dto.OpeningBalanceDTO.SubjectBalance b1 =
                new com.ledgerpilot.voucher.dto.OpeningBalanceDTO.SubjectBalance();
        b1.setSubjectCode("1001");
        b1.setDebitBalance(new BigDecimal("500.00"));
        balances.add(b1);
        com.ledgerpilot.voucher.dto.OpeningBalanceDTO.SubjectBalance b2 =
                new com.ledgerpilot.voucher.dto.OpeningBalanceDTO.SubjectBalance();
        b2.setSubjectCode("2202");
        b2.setCreditBalance(new BigDecimal("300.00"));
        balances.add(b2);
        dto.setBalances(balances);
        voucherService.saveOpeningBalances(dto);

        var vo = voucherService.getOpeningBalances(year, 1);
        assertTrue(vo.isInitialized());
        var cash = vo.getSubjects().stream().filter(x -> "1001".equals(x.getSubjectCode())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("500.00"), cash.getBeginDebit());
        // C2 统一口径（net = begin_debit - begin_credit，正落借方负落贷方）验证 end 落位（直查余额表）
        AccSubjectBalance cashBal = balanceMapper.selectOne(new LambdaQueryWrapper<AccSubjectBalance>()
                .eq(AccSubjectBalance::getTenantId, 1L).eq(AccSubjectBalance::getSubjectCode, "1001")
                .eq(AccSubjectBalance::getFiscalYear, year).eq(AccSubjectBalance::getPeriodNo, 1));
        assertEquals(new BigDecimal("500.00"), cashBal.getEndDebit());
        var payable = vo.getSubjects().stream().filter(x -> "2202".equals(x.getSubjectCode())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("300.00"), payable.getBeginCredit());
        AccSubjectBalance payableBal = balanceMapper.selectOne(new LambdaQueryWrapper<AccSubjectBalance>()
                .eq(AccSubjectBalance::getTenantId, 1L).eq(AccSubjectBalance::getSubjectCode, "2202")
                .eq(AccSubjectBalance::getFiscalYear, year).eq(AccSubjectBalance::getPeriodNo, 1));
        assertEquals(new BigDecimal("300.00"), payableBal.getEndCredit());
    }

    @Test
    @Order(6)
    void closePeriod_transfersProfitAndLoss_andLocks() {
        // 两张已过账凭证：收入 6001 贷 100；费用 6603 借 60 → 结转后 4103 贷方净额 40
        String today = LocalDate.now().toString();
        Long income = createAndApproveVoucher("1002", "6001", "100.00");
        voucherService.postVoucher(income);
        Long expense = createAndApproveVoucher("6603", "1002", "60.00");
        voucherService.postVoucher(expense);

        Long transferId = voucherService.closePeriod(LocalDate.now().getYear(), LocalDate.now().getMonthValue(), today);
        assertTrue(transferId != null && transferId > 0);

        // 重复结转必须被拒绝
        assertThrows(LedgerPilotException.class, () ->
                voucherService.closePeriod(LocalDate.now().getYear(), LocalDate.now().getMonthValue(), today));

        // 关账后原期间禁止新增凭证
        VoucherCreateRequest req = new VoucherCreateRequest();
        req.setVoucherDate(today);
        VoucherCreateRequest.EntryDTO d = new VoucherCreateRequest.EntryDTO();
        d.setSubjectCode("1001");
        d.setSubjectName("库存现金");
        d.setDirection("DEBIT");
        d.setAmount(new java.math.BigDecimal("1.00"));
        VoucherCreateRequest.EntryDTO c = new VoucherCreateRequest.EntryDTO();
        c.setSubjectCode("6001");
        c.setSubjectName("主营业务收入");
        c.setDirection("CREDIT");
        c.setAmount(new java.math.BigDecimal("1.00"));
        List<VoucherCreateRequest.EntryDTO> entries = new ArrayList<>();
        entries.add(d);
        entries.add(c);
        req.setEntries(entries);
        assertThrows(LedgerPilotException.class, () -> voucherService.createVoucher(req));
    }

    @Test
    @Order(4)
    void pageAndDetail() {
        Long id = createAndApproveVoucher("1001", "6001", "77.00");
        voucherService.postVoucher(id);
        var page = voucherService.pageVouchers(new com.ledgerpilot.voucher.dto.VoucherQueryRequest());
        assertTrue(page.getTotal() >= 1);
        var detail = voucherService.getVoucherDetail(id);
        assertEquals(2, detail.getEntries().size());
        assertEquals("POSTED", detail.getStatus());
    }

    @Test
    // ──────────凭证核心状态机与校验负例 ──────────

    private VoucherCreateRequest balancedRequest(String date, String amount) {
        VoucherCreateRequest req = new VoucherCreateRequest();
        req.setVoucherDate(date);
        VoucherCreateRequest.EntryDTO d = new VoucherCreateRequest.EntryDTO();
        d.setSubjectCode("1001");
        d.setSubjectName("库存现金");
        d.setDirection("DEBIT");
        d.setAmount(new java.math.BigDecimal(amount));
        d.setSummary("t");
        VoucherCreateRequest.EntryDTO c = new VoucherCreateRequest.EntryDTO();
        c.setSubjectCode("6001");
        c.setSubjectName("主营业务收入");
        c.setDirection("CREDIT");
        c.setAmount(new java.math.BigDecimal(amount));
        c.setSummary("t");
        List<VoucherCreateRequest.EntryDTO> entries = new ArrayList<>();
        entries.add(d);
        entries.add(c);
        req.setEntries(entries);
        return req;
    }

    @Order(8)
    @Test
    void unbalancedRejected() {
        // 借贷不平衡（借 100 / 贷 90）必须拒绝
        VoucherCreateRequest req = balancedRequest(LocalDate.now().plusMonths(1).toString(), "100.00");
        req.getEntries().get(1).setAmount(new java.math.BigDecimal("90.00"));
        assertThrows(LedgerPilotException.class, () -> voucherService.createVoucher(req));
    }

    @Order(9)
    @Test
    void emptyEntriesRejected() {
        VoucherCreateRequest req = balancedRequest(LocalDate.now().plusMonths(1).toString(), "1.00");
        req.setEntries(new ArrayList<>());
        assertThrows(LedgerPilotException.class, () -> voucherService.createVoucher(req));
    }

    @Order(10)
    @Test
    void voucherNoSequentialWithinMonth() {
        // 凭证号按月连续：同一请求两张，第二张号 = 第一张号 + 1
        String date = LocalDate.now().plusMonths(2).toString();
        Long first = voucherService.createVoucher(balancedRequest(date, "10.00"));
        Long second = voucherService.createVoucher(balancedRequest(date, "20.00"));
        var d1 = voucherService.getVoucherDetail(first);
        var d2 = voucherService.getVoucherDetail(second);
        String no1 = d1.getVoucherNo();
        String no2 = d2.getVoucherNo();
        assertEquals(no1.substring(0, 6), no2.substring(0, 6), "同月前缀一致");
        assertEquals(Integer.parseInt(no1.substring(6)) + 1, Integer.parseInt(no2.substring(6)), "序号连续");
    }

    @Order(11)
    @Test
    void draftCannotPostDirectly() {
        // 状态机：草稿不能直接过账
        Long id = voucherService.createVoucher(balancedRequest(LocalDate.now().plusMonths(2).toString(), "5.00"));
        assertThrows(LedgerPilotException.class, () -> voucherService.postVoucher(id));
    }

    @Order(12)
    @Test
    void approveThenUnapproveThenPostFails() {
        // 状态机：审核→取消审核→过账必须失败（已回草稿）
        Long id = createAndApproveVoucher(LocalDate.now().plusMonths(2).toString(), "1001", "6001", "3.00");
        voucherService.unapproveVoucher(id);
        assertThrows(LedgerPilotException.class, () -> voucherService.postVoucher(id));
    }

    @Order(13)
    @Test
    void doubleVoidConflicts() {
        Long id = createAndApproveVoucher(LocalDate.now().plusMonths(2).toString(), "1405", "1122", "8.00");
        voucherService.voidVoucher(id);
        assertThrows(LedgerPilotException.class, () -> voucherService.voidVoucher(id));
    }

    @Order(14)
    @Test
    void voidedVoucherExcludedFromPageByStatus() {
        // 作废凭证可按状态过滤（查询口径验证）
        Long id = createAndApproveVoucher(LocalDate.now().plusMonths(2).toString(), "1405", "1122", "6.00");
        voucherService.voidVoucher(id);
        var q = new com.ledgerpilot.voucher.dto.VoucherQueryRequest();
        q.setStatus("VOIDED");
        var page = voucherService.pageVouchers(q);
        assertTrue(page.getRecords().stream().anyMatch(v -> v.getId().equals(id)));
    }

    @Order(15)
    @Test
    void auxDisplayRendered() {
        // 辅助核算 JSON 渲染为可读文本
        VoucherCreateRequest req = balancedRequest(LocalDate.now().plusMonths(2).toString(), "12.00");
        req.getEntries().get(0).setAuxJson("{\"customer\":\"演示客户\"}");
        Long id = voucherService.createVoucher(req);
        var detail = voucherService.getVoucherDetail(id);
        assertEquals("customer:演示客户",
                detail.getEntries().get(0).getAuxDisplay().replace(" ", ""));
    }

    @Order(16)
    @Test
    void unknownSubjectRejected() {
        // 科目存在性闸：科目表之外的科目建凭证必须业务报错（防错账从报表蒸发）。
        // 用下月日期：本测试 @Order(7) 晚于 closePeriod（会锁当期）
        VoucherCreateRequest req = new VoucherCreateRequest();
        req.setVoucherDate(LocalDate.now().plusMonths(1).toString());
        VoucherCreateRequest.EntryDTO d = new VoucherCreateRequest.EntryDTO();
        d.setSubjectCode("9999");
        d.setSubjectName("幽灵科目");
        d.setDirection("DEBIT");
        d.setAmount(new java.math.BigDecimal("1.00"));
        VoucherCreateRequest.EntryDTO c = new VoucherCreateRequest.EntryDTO();
        c.setSubjectCode("1001");
        c.setSubjectName("库存现金");
        c.setDirection("CREDIT");
        c.setAmount(new java.math.BigDecimal("1.00"));
        List<VoucherCreateRequest.EntryDTO> entries = new ArrayList<>();
        entries.add(d);
        entries.add(c);
        req.setEntries(entries);

        LedgerPilotException e = assertThrows(LedgerPilotException.class, () -> voucherService.createVoucher(req));
        assertTrue(e.getMessage().contains("9999"));
    }

    @Test
    @Order(3)
    void postThenVoid_balanceReversedToZero() {
        // 独立科目（1405 仅本测试使用）断言归零，保证测试间隔离
        Long id = createAndApproveVoucher("1405", "1122", "50.00");
        voucherService.postVoucher(id);
        AccSubjectBalance before = balanceOf("1405");
        assertEquals(new BigDecimal("50.00"), before.getCurrentDebit());
        voucherService.voidVoucher(id);

        AccSubjectBalance debit = balanceOf("1405");
        assertEquals(new BigDecimal("0.00"), debit.getCurrentDebit());
        assertTrue(debit.getEndDebit().compareTo(BigDecimal.ZERO) == 0);
    }
}
