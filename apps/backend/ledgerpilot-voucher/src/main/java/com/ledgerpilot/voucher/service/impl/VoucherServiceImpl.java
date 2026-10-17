package com.ledgerpilot.voucher.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerpilot.common.entity.BaseEntity;
import com.ledgerpilot.common.exception.LedgerPilotException;
import com.ledgerpilot.common.util.TenantContext;
import com.ledgerpilot.voucher.dto.OpeningBalanceDTO;
import com.ledgerpilot.voucher.dto.OpeningBalanceVO;

import java.util.ArrayList;
import com.ledgerpilot.voucher.dto.VoucherCreateRequest;
import com.ledgerpilot.voucher.dto.VoucherDetailVO;
import com.ledgerpilot.voucher.dto.VoucherQueryRequest;
import com.ledgerpilot.voucher.entity.AccPeriodClose;
import com.ledgerpilot.voucher.mapper.AccPeriodCloseMapper;
import com.ledgerpilot.voucher.entity.AccSubject;
import com.ledgerpilot.voucher.entity.AccSubjectBalance;
import com.ledgerpilot.voucher.entity.AccVoucher;
import com.ledgerpilot.voucher.entity.AccVoucherEntry;
import com.ledgerpilot.voucher.mapper.AccSubjectBalanceMapper;
import com.ledgerpilot.voucher.mapper.AccVoucherEntryMapper;
import com.ledgerpilot.voucher.mapper.AccVoucherMapper;
import com.ledgerpilot.voucher.mapper.SubjectMapper;
import com.ledgerpilot.voucher.service.VoucherService;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 凭证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final AccVoucherMapper voucherMapper;
    private final AccVoucherEntryMapper entryMapper;
    private final AccSubjectBalanceMapper balanceMapper;
    private final SubjectMapper subjectMapper;
    private final AccPeriodCloseMapper periodCloseMapper;
    private final ObjectMapper objectMapper;

    @Override
    public Page<AccVoucher> pageVouchers(VoucherQueryRequest request) {
        Page<AccVoucher> page = new Page<>(
                request.getCurrent() != null ? request.getCurrent() : 1,
                request.getSize() != null ? request.getSize() : 20
        );

        LambdaQueryWrapper<AccVoucher> wrapper = new LambdaQueryWrapper<>();

        if (request.getVoucherNo() != null && StrUtil.isNotBlank(request.getVoucherNo())) {
            wrapper.like(AccVoucher::getVoucherNo, request.getVoucherNo());
        }
        if (request.getDateFrom() != null) {
            wrapper.ge(AccVoucher::getVoucherDate, request.getDateFrom());
        }
        if (request.getDateTo() != null) {
            wrapper.le(AccVoucher::getVoucherDate, request.getDateTo());
        }
        if (request.getBusinessType() != null && StrUtil.isNotBlank(request.getBusinessType())) {
            wrapper.eq(AccVoucher::getBusinessType, request.getBusinessType());
        }
        if (request.getStatus() != null && StrUtil.isNotBlank(request.getStatus())) {
            wrapper.eq(AccVoucher::getStatus, request.getStatus());
        }
        if (request.getSourceType() != null && StrUtil.isNotBlank(request.getSourceType())) {
            wrapper.eq(AccVoucher::getSourceType, request.getSourceType());
        }

        // 按凭证日期倒序
        wrapper.orderByDesc(AccVoucher::getVoucherDate, AccVoucher::getVoucherNo);

        return voucherMapper.selectPage(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVoucher(VoucherCreateRequest request) {
        requireTenant();
        // 1. 校验
        if (request.getEntries() == null || request.getEntries().isEmpty()) {
            throw new LedgerPilotException("分录不能为空");
        }
        // 关账期间禁止新增凭证
        if (StrUtil.isNotBlank(request.getVoucherDate())) {
            int[] ym = getYearMonthFromDate(request.getVoucherDate());
            checkPeriodNotLocked(ym[0], ym[1]);
        }

        // 2. 计算借贷合计
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (VoucherCreateRequest.EntryDTO entry : request.getEntries()) {
            BigDecimal amount = normalizeAmount(entry.getAmount());
            if ("DEBIT".equals(entry.getDirection())) {
                totalDebit = totalDebit.add(amount);
            } else {
                totalCredit = totalCredit.add(amount);
            }
        }

        // 3. 借贷平衡校验
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new LedgerPilotException(
                    String.format("借贷不平衡: 借方合计=%s, 贷方合计=%s",
                            totalDebit.toPlainString(), totalCredit.toPlainString()));
        }

        // 3.1 科目存在性闸：分录科目必须存在于租户科目表——
        // 否则错账科目会从借贷平衡与报表中同时"蒸发"（凭证有、报表无）
        for (VoucherCreateRequest.EntryDTO entry : request.getEntries()) {
            Long cnt = subjectMapper.selectCount(new LambdaQueryWrapper<com.ledgerpilot.voucher.entity.AccSubject>()
                    .eq(com.ledgerpilot.voucher.entity.AccSubject::getSubjectCode, entry.getSubjectCode())
                    .eq(com.ledgerpilot.voucher.entity.AccSubject::getDeleted, 0));
            if (cnt == null || cnt == 0) {
                throw new LedgerPilotException("SUBJECT_NOT_FOUND",
                        String.format("科目不存在（请先在科目表中创建）: %s %s",
                                entry.getSubjectCode(), entry.getSubjectName() == null ? "" : entry.getSubjectName()));
            }
        }

        // 4. 租户上下文（认证过滤器保证，纵深防御见 requireTenant）
        Long tenantId = TenantContext.get();

        // 5. 生成凭证号（并发冲突由唯一键兜底：首次冲突重取一次，再失败报业务错误）
        Long voucherId;
        try {
            voucherId = insertVoucherWithEntries(tenantId, request, totalDebit, totalCredit);
        } catch (DuplicateKeyException e) {
            log.warn("凭证号并发冲突，重取一次: prefix={}", request.getVoucherDate().replace("-", "").substring(0, 6));
            try {
                voucherId = insertVoucherWithEntries(tenantId, request, totalDebit, totalCredit);
            } catch (DuplicateKeyException e2) {
                throw new LedgerPilotException("VOUCHER_NO_CONFLICT",
                        "凭证号生成并发冲突，请重试");
            }
        }

        return voucherId;
    }

    /** 插入凭证主表+分录，返回凭证ID（供凭证号冲突重试） */
    private Long insertVoucherWithEntries(Long tenantId, VoucherCreateRequest request,
                                          BigDecimal totalDebit, BigDecimal totalCredit) {
        String voucherNo = generateNextVoucherNo(request.getVoucherDate());

        // 摘要：取请求中的 summary，或回退第一条分录摘要
        String summary = request.getSummary();
        if (StrUtil.isBlank(summary) && !request.getEntries().isEmpty()) {
            summary = request.getEntries().get(0).getSummary();
        }

        AccVoucher voucher = new AccVoucher();
        voucher.setTenantId(tenantId);
        voucher.setVoucherNo(voucherNo);
        voucher.setVoucherDate(LocalDate.parse(request.getVoucherDate()));
        voucher.setBusinessType(request.getBusinessType());
        voucher.setBusinessNo(request.getBusinessNo());
        voucher.setTotalDebit(totalDebit);
        voucher.setTotalCredit(totalCredit);
        voucher.setEntryCount(request.getEntries().size());
        voucher.setStatus("DRAFT");
        voucher.setSourceType(
                StrUtil.isNotBlank(request.getSourceType()) ? request.getSourceType() : "MANUAL");
        voucher.setSummary(summary);

        voucherMapper.insert(voucher);
        Long voucherId = voucher.getId();

        int seq = 1;
        for (VoucherCreateRequest.EntryDTO entryDTO : request.getEntries()) {
            AccVoucherEntry entry = new AccVoucherEntry();
            entry.setTenantId(tenantId);
            entry.setVoucherId(voucherId);
            entry.setSeq(seq++);
            entry.setSubjectCode(entryDTO.getSubjectCode());
            entry.setSubjectName(entryDTO.getSubjectName());
            entry.setDirection(entryDTO.getDirection());
            entry.setAmount(normalizeAmount(entryDTO.getAmount()));
            entry.setSummary(entryDTO.getSummary());
            entry.setAuxJson(entryDTO.getAuxJson());

            entryMapper.insert(entry);
        }
        return voucherId;
    }

    /** 金额归一：标度固定 2 位、HALF_UP（schema v2 契约，消灭浮点尾巴） */
    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** 租户上下文必须存在（认证过滤器保证；此处为纵深防御） */
    private void requireTenant() {
        if (TenantContext.get() == null) {
            throw new LedgerPilotException("TENANT_CONTEXT_MISSING", "缺少租户上下文，请重新登录");
        }
    }


    @Override
    public VoucherDetailVO getVoucherDetail(Long id) {
        // 1. 查询凭证主表
        AccVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) {
            throw new LedgerPilotException("凭证不存在");
        }

        // 2. 查询分录列表
        LambdaQueryWrapper<AccVoucherEntry> ew = new LambdaQueryWrapper<>();
        ew.eq(AccVoucherEntry::getVoucherId, id).orderByAsc(AccVoucherEntry::getSeq);
        List<AccVoucherEntry> entries = entryMapper.selectList(ew);

        // 3. 组装VO
        VoucherDetailVO vo = new VoucherDetailVO();
        vo.setId(voucher.getId());
        vo.setTenantId(voucher.getTenantId());
        vo.setVoucherNo(voucher.getVoucherNo());
        vo.setVoucherDate(voucher.getVoucherDate());
        vo.setBusinessType(voucher.getBusinessType());
        vo.setBusinessNo(voucher.getBusinessNo());
        vo.setTotalDebit(voucher.getTotalDebit());
        vo.setTotalCredit(voucher.getTotalCredit());
        vo.setEntryCount(voucher.getEntryCount());
        vo.setStatus(voucher.getStatus());
        vo.setSourceType(voucher.getSourceType());
        vo.setSummary(voucher.getSummary());
        vo.setApproveBy(voucher.getApproveBy());
        vo.setApproveAt(voucher.getApproveAt());
        vo.setPostBy(voucher.getPostBy());
        vo.setPostAt(voucher.getPostAt());
        vo.setCreatedAt(voucher.getCreatedAt());
        vo.setUpdatedAt(voucher.getUpdatedAt());

        // 4. 转换分录
        List<VoucherDetailVO.VoucherEntryVO> entryVOList = entries.stream().map(e -> {
            VoucherDetailVO.VoucherEntryVO ev = new VoucherDetailVO.VoucherEntryVO();
            ev.setId(e.getId());
            ev.setSeq(e.getSeq());
            ev.setSubjectId(e.getSubjectId());
            ev.setSubjectCode(e.getSubjectCode());
            ev.setSubjectName(e.getSubjectName());
            ev.setDirection(e.getDirection());
            ev.setAmount(e.getAmount());
            ev.setSummary(e.getSummary());
            ev.setAuxJson(e.getAuxJson());
            ev.setAuxDisplay(parseAuxDisplay(e.getAuxJson()));
            return ev;
        }).collect(Collectors.toList());

        vo.setEntries(entryVOList);

        return vo;
    }

    /**
     * 解析辅助核算JSON为可读文本
     */
    private String parseAuxDisplay(String auxJson) {
        if (StrUtil.isBlank(auxJson)) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> map = objectMapper.readValue(auxJson, Map.class);
            return map.entrySet().stream()
                    .map(e -> e.getKey() + ":" + e.getValue())
                    .collect(Collectors.joining(", "));
        } catch (Exception e) {
            return auxJson;
        }
    }

    @Override
    public void approveVoucher(Long id) {
        requireTenant();
        AccVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) {
            throw new LedgerPilotException("凭证不存在");
        }
        int[] ym = getYearMonthFromDate(voucher.getVoucherDate());
        checkPeriodNotLocked(ym[0], ym[1]);
        // CAS：防并发双审核/状态漂移
        if (voucherMapper.casStatus(id, "DRAFT", "APPROVED", java.time.LocalDateTime.now()) != 1) {
            throw new LedgerPilotException("VOUCHER_STATE_CONFLICT", "只有草稿状态才能审核（或状态已变化）");
        }
        log.info("审核凭证: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unapproveVoucher(Long id) {
        requireTenant();
        AccVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) {
            throw new LedgerPilotException("凭证不存在");
        }
        int[] ym = getYearMonthFromDate(voucher.getVoucherDate());
        checkPeriodNotLocked(ym[0], ym[1]);
        if (voucherMapper.casStatus(id, "APPROVED", "DRAFT", java.time.LocalDateTime.now()) != 1) {
            throw new LedgerPilotException("VOUCHER_STATE_CONFLICT", "只有已审核状态才能取消审核（或状态已变化）");
        }
        log.info("取消审核凭证: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void postVoucher(Long id) {
        requireTenant();
        AccVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) {
            throw new LedgerPilotException("凭证不存在");
        }
        if (!"APPROVED".equals(voucher.getStatus())) {
            throw new LedgerPilotException("只有已审核状态才能过账");
        }
        int[] ym = getYearMonthFromDate(voucher.getVoucherDate());
        checkPeriodNotLocked(ym[0], ym[1]);

        Long tenantId = TenantContext.get();

        // 先 CAS 认领过账权（防并发双过账：并发第二笔 rows=0 直接失败，余额只被累计一次）；
        // 事务内后续余额累计失败会连同状态一起回滚。
        if (voucherMapper.casStatus(id, "APPROVED", "POSTED", LocalDateTime.now()) != 1) {
            throw new LedgerPilotException("VOUCHER_STATE_CONFLICT", "过账状态冲突（可能已被并发过账）");
        }

        List<AccVoucherEntry> entries = entryMapper.selectList(
                new LambdaQueryWrapper<AccVoucherEntry>()
                        .eq(AccVoucherEntry::getVoucherId, id));

        // 逐行原子累计科目余额
        for (AccVoucherEntry entry : entries) {
            postEntry(entry, voucher.getVoucherDate());
        }

        log.info("过账凭证: id={}, 条目数={}", id, entries.size());
    }

    /**
     * 过账单条分录：原子累计科目余额。
     * 版本冲突重试最多 5 次；耗尽即抛异常回滚整笔过账——绝不静默失败留下 POSTED 假账。
     */
    private void postEntry(AccVoucherEntry entry, LocalDate voucherDate) {
        int year = voucherDate.getYear();
        int period = voucherDate.getMonthValue();
        Long tenantId = TenantContext.get();

        boolean debit = "DEBIT".equals(entry.getDirection());
        BigDecimal debitDelta = debit ? entry.getAmount() : BigDecimal.ZERO;
        BigDecimal creditDelta = debit ? BigDecimal.ZERO : entry.getAmount();

        for (int retry = 0; retry < 5; retry++) {
            AccSubjectBalance bal = getOrCreateBalance(tenantId, entry.getSubjectCode(), year, period);
            int rows = balanceMapper.accrue(bal.getId(), debitDelta, creditDelta, bal.getVersion());
            if (rows == 1) {
                return;
            }
            log.warn("科目余额并发冲突，subject={}, year={}, period={}, 重试={}",
                    entry.getSubjectCode(), year, period, retry + 1);
        }
        throw new LedgerPilotException("BALANCE_CONFLICT",
                String.format("科目余额并发冲突，过账已回滚: subject=%s", entry.getSubjectCode()));
    }

    /**
     * 获取或创建当期科目余额记录
     * - 若当期无记录，从上期结转期初余额
     */
    private AccSubjectBalance getOrCreateBalance(Long tenantId, String subjectCode,
                                                 int year, int period) {
        AccSubjectBalance bal = balanceMapper.selectOne(
                new LambdaQueryWrapper<AccSubjectBalance>()
                        .eq(AccSubjectBalance::getTenantId, tenantId)
                        .eq(AccSubjectBalance::getSubjectCode, subjectCode)
                        .eq(AccSubjectBalance::getFiscalYear, year)
                        .eq(AccSubjectBalance::getPeriodNo, period)
        );

        if (bal != null) {
            return bal;
        }


        // 查询上期期末余额作为期初
        int priorPeriod = period - 1;
        int priorYear = year;
        if (priorPeriod < 1) {
            priorPeriod = 12;
            priorYear = year - 1;
        }
        AccSubjectBalance priorBal = balanceMapper.selectOne(
                new LambdaQueryWrapper<AccSubjectBalance>()
                        .eq(AccSubjectBalance::getTenantId, tenantId)
                        .eq(AccSubjectBalance::getSubjectCode, subjectCode)
                        .eq(AccSubjectBalance::getFiscalYear, priorYear)
                        .eq(AccSubjectBalance::getPeriodNo, priorPeriod)
        );

        bal = new AccSubjectBalance();
        bal.setTenantId(tenantId);
        bal.setSubjectCode(subjectCode);
        bal.setFiscalYear(year);
        bal.setPeriodNo(period);
        bal.setCurrentDebit(BigDecimal.ZERO);
        bal.setCurrentCredit(BigDecimal.ZERO);
        bal.setVersion(0);

        if (priorBal != null) {
            bal.setBeginDebit(priorBal.getEndDebit());
            bal.setBeginCredit(priorBal.getEndCredit());
        } else {
            // 系统第一笔分录，期初为0
            bal.setBeginDebit(BigDecimal.ZERO);
            bal.setBeginCredit(BigDecimal.ZERO);
        }

        // 初算期末（未含本次entry，待postEntry中再累加）
        recalcEndBalance(bal);

        // 首建并发竞态：唯一键兜底，冲突则重查（对方已建好，直接复用）
        try {
            balanceMapper.insert(bal);
        } catch (DuplicateKeyException e) {
            log.info("科目余额首建并发冲突，改用既有行: subject={}, year={}, period={}",
                    subjectCode, year, period);
            return balanceMapper.selectOne(
                    new LambdaQueryWrapper<AccSubjectBalance>()
                            .eq(AccSubjectBalance::getTenantId, tenantId)
                            .eq(AccSubjectBalance::getSubjectCode, subjectCode)
                            .eq(AccSubjectBalance::getFiscalYear, year)
                            .eq(AccSubjectBalance::getPeriodNo, period));
        }
        return bal;
    }

    /**
     * 重新计算期末余额
     * 公式：end = begin + current_debit - current_credit
     * 结果放在 endDebit（正数）或 endCredit（正数）中，另一方为0
     */
    private void recalcEndBalance(AccSubjectBalance bal) {
        BigDecimal endRaw = bal.getBeginDebit()
                .add(bal.getCurrentDebit())
                .subtract(bal.getCurrentCredit());
        if (endRaw.compareTo(BigDecimal.ZERO) >= 0) {
            bal.setEndDebit(endRaw);
            bal.setEndCredit(BigDecimal.ZERO);
        } else {
            bal.setEndDebit(BigDecimal.ZERO);
            bal.setEndCredit(endRaw.abs());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidVoucher(Long id) {
        requireTenant();
        AccVoucher voucher = voucherMapper.selectById(id);
        if (voucher == null) {
            throw new LedgerPilotException("凭证不存在");
        }
        // 关账期间禁止作废
        int[] ym = getYearMonthFromDate(voucher.getVoucherDate());
        checkPeriodNotLocked(ym[0], ym[1]);
        boolean wasPosted = "POSTED".equals(voucher.getStatus());

        // 先 CAS 认领作废权（防并发双作废/双逆运算）
        if (voucherMapper.casVoid(id) != 1) {
            throw new LedgerPilotException("VOUCHER_STATE_CONFLICT", "凭证状态已变化，作废失败");
        }

        // 已过账的凭证：逆运算余额（失败抛异常，随事务回滚作废状态）
        if (wasPosted) {
            reversePosting(id, voucher);
        }
        log.info("作废凭证: id={}, priorStatus={}", id, voucher.getStatus());
    }

    /**
     * 逆运算过账：从科目余额中扣除已过账的分录金额
     */
    private void reversePosting(Long voucherId, AccVoucher voucher) {
        Long tenantId = TenantContext.get();
        List<AccVoucherEntry> entries = entryMapper.selectList(
                new LambdaQueryWrapper<AccVoucherEntry>()
                        .eq(AccVoucherEntry::getVoucherId, voucherId));

        for (AccVoucherEntry entry : entries) {
            int year = voucher.getVoucherDate().getYear();
            int period = voucher.getVoucherDate().getMonthValue();

            boolean debit = "DEBIT".equals(entry.getDirection());
            // 逆运算 = 负增量
            BigDecimal debitDelta = debit ? entry.getAmount().negate() : BigDecimal.ZERO;
            BigDecimal creditDelta = debit ? BigDecimal.ZERO : entry.getAmount().negate();

            AccSubjectBalance bal = balanceMapper.selectOne(
                    new LambdaQueryWrapper<AccSubjectBalance>()
                            .eq(AccSubjectBalance::getTenantId, tenantId)
                            .eq(AccSubjectBalance::getSubjectCode, entry.getSubjectCode())
                            .eq(AccSubjectBalance::getFiscalYear, year)
                            .eq(AccSubjectBalance::getPeriodNo, period)
            );
            // 过账时必然建过余额行；查不到说明数据被外部破坏，静默跳过会留下假余额——必须失败
            if (bal == null) {
                throw new LedgerPilotException("BALANCE_MISSING",
                        String.format("作废失败：科目余额行缺失 subject=%s, year=%d, period=%d",
                                entry.getSubjectCode(), year, period));
            }

            for (int retry = 0; retry < 5; retry++) {
                int rows = balanceMapper.accrue(bal.getId(), debitDelta, creditDelta, bal.getVersion());
                if (rows == 1) {
                    break;
                }
                log.warn("作废逆运算并发冲突，subject={}, 重试={}", entry.getSubjectCode(), retry + 1);
                if (retry == 4) {
                    throw new LedgerPilotException("BALANCE_CONFLICT",
                            String.format("科目余额并发冲突，作废已回滚: subject=%s", entry.getSubjectCode()));
                }
                bal = balanceMapper.selectById(bal.getId());
            }
        }
    }

    /**
     * 月末结转：将当月所有已过账凭证的损益类科目结转至本年利润
     * 收入类科目（6开头，贷方发生额）→ 本年利润 贷方
     * 成本费用类科目（6开头，借方发生额）→ 本年利润 借方
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long closePeriod(Integer year, Integer period, String transferDate) {
        log.info("月末结转: {}-{}", year, period);
        requireTenant();
        Long tenantId = TenantContext.get();

        // ===== 安全检查1：检查是否已关账（防重复执行） =====
        AccPeriodClose existingClose = periodCloseMapper.selectOne(
                new LambdaQueryWrapper<AccPeriodClose>()
                        .eq(AccPeriodClose::getTenantId, tenantId)
                        .eq(AccPeriodClose::getFiscalYear, year)
                        .eq(AccPeriodClose::getPeriodNo, period)
                        .last("LIMIT 1"));
        if (existingClose != null) {
            throw new LedgerPilotException(
                    String.format("%d年%d期已做过账结转（凭证号:%s），如需重新结转请先删除原结转凭证", 
                            year, period, ""));
        }

        // ===== 1. 查询当月所有已过账凭证，排除月结转账凭证本身 =====
        LocalDate monthStart = LocalDate.of(year, period, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        List<AccVoucher> postedVouchers = voucherMapper.selectList(
                new LambdaQueryWrapper<AccVoucher>()
                        .eq(AccVoucher::getTenantId, tenantId)
                        .eq(AccVoucher::getStatus, "POSTED")
                        // 排除月结转账本身；SQL 三值逻辑：NULL business_type 用 ne 会漏，
                        // 必须显式 OR IS NULL
                        .and(w -> w.ne(AccVoucher::getBusinessType, "TRANSFER")
                                .or().isNull(AccVoucher::getBusinessType))
                        .ge(AccVoucher::getVoucherDate, monthStart)
                        .le(AccVoucher::getVoucherDate, monthEnd)
        );

        if (postedVouchers.isEmpty()) {
            throw new LedgerPilotException("当月没有已过账的业务凭证，无需结转");
        }

        // ===== 2. 汇总所有分录的发生额（按科目+方向），只处理6开头科目 =====
        Map<String, BigDecimal> debitMap = new java.util.HashMap<>();
        Map<String, BigDecimal> creditMap = new java.util.HashMap<>();

        for (AccVoucher v : postedVouchers) {
            List<AccVoucherEntry> entries = entryMapper.selectList(
                    new LambdaQueryWrapper<AccVoucherEntry>()
                            .eq(AccVoucherEntry::getVoucherId, v.getId()));
            for (AccVoucherEntry e : entries) {
                String code = e.getSubjectCode();
                if (!code.startsWith("6")) continue; // 只处理损益类科目
                if ("DEBIT".equals(e.getDirection())) {
                    debitMap.merge(code, e.getAmount(), BigDecimal::add);
                } else {
                    creditMap.merge(code, e.getAmount(), BigDecimal::add);
                }
            }
        }

        // ===== 3. 计算每个科目的净发生额，只保留有发生的科目 =====
        // 净贷方 = credit - debit（正数表示收入，负数表示费用超收）
        // 净借方 = debit - credit（正数表示费用，负数表示收入超支）
        Map<String, BigDecimal> netIncomeMap = new java.util.HashMap<>();  // 净收入科目: code → 贷方净额
        Map<String, BigDecimal> netExpenseMap = new java.util.HashMap<>(); // 净费用科目: code → 借方净额

        for (String code : new java.util.HashSet<>(creditMap.keySet())) {
            BigDecimal credit = creditMap.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal debit = debitMap.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal netCredit = credit.subtract(debit);
            if (netCredit.compareTo(BigDecimal.ZERO) > 0) {
                netIncomeMap.put(code, netCredit);
            }
        }
        for (String code : new java.util.HashSet<>(debitMap.keySet())) {
            BigDecimal debit = debitMap.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal credit = creditMap.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal netDebit = debit.subtract(credit);
            if (netDebit.compareTo(BigDecimal.ZERO) > 0) {
                netExpenseMap.put(code, netDebit);
            }
        }

        BigDecimal totalIncome = netIncomeMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = netExpenseMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        // 凭证头合计 = 分录实际合计（借 = 贷 = 收入合计 + 费用合计）。
        // 原 max(income, expense) 与分录合计不符（头≠分录）。

        log.info("月末结转汇总: 收入科目数={} 合计={}, 费用科目数={} 合计={}, 净利润={}",
                netIncomeMap.size(), totalIncome, netExpenseMap.size(), totalExpense,
                totalIncome.subtract(totalExpense));

        if (netIncomeMap.isEmpty() && netExpenseMap.isEmpty()) {
            throw new LedgerPilotException("当月没有损益发生额，无需结转");
        }

        // ===== 4. 生成结转凭证 =====
        String voucherNo = generateNextVoucherNo(transferDate);
        AccVoucher voucher = new AccVoucher();
        voucher.setTenantId(tenantId);
        voucher.setVoucherNo(voucherNo);
        voucher.setVoucherDate(LocalDate.parse(transferDate));
        voucher.setBusinessType("TRANSFER");
        BigDecimal totalAmt = totalIncome.add(totalExpense);
        voucher.setTotalDebit(totalAmt);
        voucher.setTotalCredit(totalAmt);
        voucher.setEntryCount(0);
        voucher.setStatus("DRAFT");
        voucher.setSourceType("MONTH_CLOSE");
        voucher.setSummary(String.format("%d年%d月损益结转", year, period));
        voucherMapper.insert(voucher);
        Long voucherId = voucher.getId();

        // ===== 5. 构建结转分录：收入科目→本年利润 + 本年利润→费用科目 =====
        List<AccVoucherEntry> transferEntries = new java.util.ArrayList<>();
        int seq = 1;

        // 结转收入（本年利润贷方）
        for (Map.Entry<String, BigDecimal> en : netIncomeMap.entrySet()) {
            // 借：收入科目
            transferEntries.add(buildVoucherEntry(tenantId, voucherId, seq++,
                    "DEBIT", en.getKey(), findSubjectName(en.getKey()), en.getValue(),
                    String.format("%d月结转", period)));
            // 贷：本年利润
            transferEntries.add(buildVoucherEntry(tenantId, voucherId, seq++,
                    "CREDIT", "4103", "本年利润", en.getValue(),
                    String.format("%d月结转", period)));
        }

        // 结转成本费用（本年利润借方）
        for (Map.Entry<String, BigDecimal> en : netExpenseMap.entrySet()) {
            // 借：本年利润
            transferEntries.add(buildVoucherEntry(tenantId, voucherId, seq++,
                    "DEBIT", "4103", "本年利润", en.getValue(),
                    String.format("%d月结转", period)));
            // 贷：费用科目
            transferEntries.add(buildVoucherEntry(tenantId, voucherId, seq++,
                    "CREDIT", en.getKey(), findSubjectName(en.getKey()), en.getValue(),
                    String.format("%d月结转", period)));
        }

        // 批量插入分录
        for (AccVoucherEntry e : transferEntries) {
            entryMapper.insert(e);
        }

        // 更新凭证分录数量
        AccVoucher update = new AccVoucher();
        update.setId(voucherId);
        update.setEntryCount(transferEntries.size());
        voucherMapper.updateById(update);

        log.info("月末结转凭证已生成: id={}, voucherNo={}, 分录数={}",
                voucherId, voucherNo, transferEntries.size());

        // ===== 6. 自动过账 =====
        doPostVoucher(voucherId, tenantId);

        // ===== 7. 记录关账标记 =====
        AccPeriodClose closeRecord = new AccPeriodClose();
        closeRecord.setTenantId(tenantId);
        closeRecord.setFiscalYear(year);
        closeRecord.setPeriodNo(period);
        closeRecord.setClosedAt(LocalDateTime.now());
        closeRecord.setTransferVoucherId(voucherId);
        closeRecord.setRemark(String.format("生成凭证:%s", voucherNo));
        periodCloseMapper.insert(closeRecord);

        return voucherId;
    }

    /**
     * 检查指定期间是否已关账，已关账则抛异常
     */
    private void checkPeriodNotLocked(int year, int month) {
        requireTenant();
        Long tenantId = TenantContext.get();
        AccPeriodClose closed = periodCloseMapper.selectOne(
                new LambdaQueryWrapper<AccPeriodClose>()
                        .eq(AccPeriodClose::getTenantId, tenantId)
                        .eq(AccPeriodClose::getFiscalYear, year)
                        .eq(AccPeriodClose::getPeriodNo, month));
        if (closed != null) {
            throw new LedgerPilotException(year + "年" + month + "期已关账锁定，禁止操作");
        }
    }

    /**
     * 从凭证日期字符串中提取年月
     */
    private int[] getYearMonthFromDate(String voucherDate) {
        if (StrUtil.isBlank(voucherDate) || voucherDate.length() < 7) {
            throw new LedgerPilotException("凭证日期格式错误");
        }
        int year = Integer.parseInt(voucherDate.substring(0, 4));
        int month = Integer.parseInt(voucherDate.substring(5, 7));
        return new int[]{year, month};
    }

    /**
     * 从 LocalDate 中提取年月
     */
    private int[] getYearMonthFromDate(LocalDate voucherDate) {
        if (voucherDate == null) {
            throw new LedgerPilotException("凭证日期不能为空");
        }
        return new int[]{voucherDate.getYear(), voucherDate.getMonthValue()};
    }

    private AccVoucherEntry buildVoucherEntry(Long tenantId, Long voucherId, int seq,
                                               String direction, String subjectCode,
                                               String subjectName, BigDecimal amount, String summary) {
        AccVoucherEntry e = new AccVoucherEntry();
        e.setTenantId(tenantId);
        e.setVoucherId(voucherId);
        e.setSeq(seq);
        e.setSubjectCode(subjectCode);
        e.setSubjectName(subjectName);
        e.setDirection(direction);
        e.setAmount(amount);
        e.setSummary(summary);
        return e;
    }

    private VoucherCreateRequest.EntryDTO buildEntry(String direction, String subjectCode,
                                                       String subjectName, BigDecimal amount, String summary) {
        VoucherCreateRequest.EntryDTO e = new VoucherCreateRequest.EntryDTO();
        e.setSubjectCode(subjectCode);
        e.setSubjectName(subjectName);
        e.setDirection(direction);
        e.setAmount(amount);
        e.setSummary(summary);
        return e;
    }

    private String findSubjectName(String subjectCode) {
        AccSubject sub = subjectMapper.selectOne(
                new LambdaQueryWrapper<AccSubject>()
                        .eq(AccSubject::getSubjectCode, subjectCode)
                        .last("LIMIT 1"));
        return sub != null ? sub.getSubjectName() : subjectCode;
    }

    /** 过账凭证（内部用，跳过状态校验，用于结转自动过账） */
    private void doPostVoucher(Long id, Long tenantId) {
        AccVoucher voucher = voucherMapper.selectById(id);
        List<AccVoucherEntry> entries = entryMapper.selectList(
                new LambdaQueryWrapper<AccVoucherEntry>()
                        .eq(AccVoucherEntry::getVoucherId, id));
        for (AccVoucherEntry entry : entries) {
            postEntry(entry, voucher.getVoucherDate());
        }
        AccVoucher update = new AccVoucher();
        update.setId(id);
        update.setStatus("POSTED");
        update.setPostAt(LocalDateTime.now());
        voucherMapper.updateById(update);
        log.info("月结凭证过账完成: id={}", id);
    }

    @Override
    public String generateNextVoucherNo(String voucherDate) {
        // 租户拦截器 (TenantLineInnerInterceptor) 自动追加 tenant_id 条件。
        // 并发安全不靠这里的锁：唯一键 (tenant_id, voucher_no) 兜底 + createVoucher 冲突重取，
        // 因此不再使用不可移植且脆弱的 LIMIT ... FOR UPDATE。
        String prefix = voucherDate.replace("-", "").substring(0, 6);
        AccVoucher last = voucherMapper.selectOne(new LambdaQueryWrapper<AccVoucher>()
                .likeRight(AccVoucher::getVoucherNo, prefix)
                .orderByDesc(AccVoucher::getVoucherNo)
                .last("LIMIT 1"));
        int seq = 1;
        if (last != null) {
            String lastNo = last.getVoucherNo();
            seq = Integer.parseInt(lastNo.substring(6)) + 1;
        }
        return prefix + String.format("%06d", seq);
    }

    @Override
    public OpeningBalanceVO getOpeningBalances(Integer year, Integer period) {
        requireTenant();
        Long tenantId = TenantContext.get();
        int y = year != null ? year : LocalDate.now().getYear();
        int p = period != null ? period : LocalDate.now().getMonthValue();

        // 获取全部科目
        List<AccSubject> allSubjects = subjectMapper.selectList(
                new LambdaQueryWrapper<AccSubject>()
                        .eq(AccSubject::getDeleted, 0)
                        .orderByAsc(AccSubject::getSubjectCode)
        );

        // 获取当期余额记录
        List<AccSubjectBalance> balances = balanceMapper.selectList(
                new LambdaQueryWrapper<AccSubjectBalance>()
                        .eq(AccSubjectBalance::getTenantId, tenantId)
                        .eq(AccSubjectBalance::getFiscalYear, y)
                        .eq(AccSubjectBalance::getPeriodNo, p)
        );
        Map<String, AccSubjectBalance> balanceMap = balances.stream()
                .collect(Collectors.toMap(AccSubjectBalance::getSubjectCode, b -> b));

        // 检查是否有凭证
        LocalDate startDate = LocalDate.of(y, p, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        long voucherCount = voucherMapper.selectCount(
                new LambdaQueryWrapper<AccVoucher>()
                        .eq(AccVoucher::getTenantId, tenantId)
                        .ge(AccVoucher::getVoucherDate, startDate)
                        .le(AccVoucher::getVoucherDate, endDate)
                        .ne(AccVoucher::getStatus, "VOIDED")
        );

        OpeningBalanceVO vo = new OpeningBalanceVO();
        vo.setFiscalYear(y);
        vo.setPeriodNo(p);
        vo.setInitialized(!balances.isEmpty());
        vo.setHasVouchers(voucherCount > 0);

        List<OpeningBalanceVO.SubjectBalanceVO> subjectVOs = allSubjects.stream()
                .map(s -> {
                    AccSubjectBalance bal = balanceMap.get(s.getSubjectCode());
                    OpeningBalanceVO.SubjectBalanceVO svo = new OpeningBalanceVO.SubjectBalanceVO();
                    svo.setSubjectCode(s.getSubjectCode());
                    svo.setSubjectName(s.getSubjectName());
                    svo.setDirection(s.getDirection());
                    svo.setSubjectType(s.getSubjectType());
                    svo.setBalanceDirection(s.getDirection()); // 正常余额方向
                    if (bal != null) {
                        svo.setBeginDebit(bal.getBeginDebit());
                        svo.setBeginCredit(bal.getBeginCredit());
                    } else {
                        svo.setBeginDebit(BigDecimal.ZERO);
                        svo.setBeginCredit(BigDecimal.ZERO);
                    }
                    return svo;
                })
                .collect(Collectors.toList());

        vo.setSubjects(subjectVOs);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOpeningBalances(OpeningBalanceDTO dto) {
        requireTenant();
        Long tenantId = TenantContext.get();
        int y = dto.getFiscalYear() != null ? dto.getFiscalYear() : LocalDate.now().getYear();
        int p = dto.getPeriodNo() != null ? dto.getPeriodNo() : LocalDate.now().getMonthValue();

        // 检查是否有凭证
        LocalDate startDate = LocalDate.of(y, p, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        long voucherCount = voucherMapper.selectCount(
                new LambdaQueryWrapper<AccVoucher>()
                        .eq(AccVoucher::getTenantId, tenantId)
                        .ge(AccVoucher::getVoucherDate, startDate)
                        .le(AccVoucher::getVoucherDate, endDate)
                        .ne(AccVoucher::getStatus, "VOIDED")
        );
        if (voucherCount > 0) {
            throw new LedgerPilotException("当前期间已有凭证，不可修改期初余额");
        }

        // 查询上一期间的期末余额（用于结转）
        int prevPeriod = p - 1;
        int prevYear = y;
        if (prevPeriod < 1) {
            prevPeriod = 12;
            prevYear = y - 1;
        }
        final int prevYearF = prevYear;
        final int prevPeriodF = prevPeriod;
        List<AccSubjectBalance> prevBalances = balanceMapper.selectList(
                new LambdaQueryWrapper<AccSubjectBalance>()
                        .eq(AccSubjectBalance::getTenantId, tenantId)
                        .eq(AccSubjectBalance::getFiscalYear, prevYearF)
                        .eq(AccSubjectBalance::getPeriodNo, prevPeriodF)
        );
        // 按科目编码建立上期余额 map
        final Map<String, AccSubjectBalance> prevMap = prevBalances.stream()
                .collect(Collectors.toMap(AccSubjectBalance::getSubjectCode, b -> b));

        // 查询全部科目（用于获取余额方向）
        List<AccSubject> allSubjects = subjectMapper.selectList(
                new LambdaQueryWrapper<AccSubject>()
                        .eq(AccSubject::getDeleted, 0)
                        .orderByAsc(AccSubject::getSubjectCode)
        );
        final Map<String, AccSubject> subjectMap = allSubjects.stream()
                .collect(Collectors.toMap(AccSubject::getSubjectCode, s -> s));

        // 转换为 map，key = subjectCode
        Map<String, OpeningBalanceDTO.SubjectBalance> dtoMap = dto.getBalances().stream()
                .filter(b -> b.getDebitBalance() != null || b.getCreditBalance() != null)
                .collect(Collectors.toMap(b -> b.getSubjectCode(), b -> b));

        // 遍历全部科目，构建当期余额记录
        List<AccSubjectBalance> toSave = new ArrayList<>();
        for (AccSubject subject : allSubjects) {
            String code = subject.getSubjectCode();
            // 期初 = 上期期末结转，再由用户输入的「最终期初余额值」覆盖（非增量叠加）
            AccSubjectBalance prev = prevMap.get(code);
            BigDecimal beginDebit = BigDecimal.ZERO;
            BigDecimal beginCredit = BigDecimal.ZERO;
            if (prev != null) {
                beginDebit = prev.getEndDebit() != null ? prev.getEndDebit() : BigDecimal.ZERO;
                beginCredit = prev.getEndCredit() != null ? prev.getEndCredit() : BigDecimal.ZERO;
            }
            OpeningBalanceDTO.SubjectBalance userEntry = dtoMap.get(code);
            if (userEntry != null) {
                // 用户的输入是"最终期初余额值"（直接覆盖，非增量叠加）
                if (subject.getDirection().equals("DEBIT")) {
                    if (userEntry.getDebitBalance() != null) {
                        beginDebit = userEntry.getDebitBalance();
                    }
                    // 贷方科目贷方输入
                    if (userEntry.getCreditBalance() != null && userEntry.getCreditBalance().compareTo(BigDecimal.ZERO) > 0) {
                        beginCredit = userEntry.getCreditBalance();
                    }
                } else {
                    // 贷方科目：贷方输入直接设 beginCredit
                    if (userEntry.getCreditBalance() != null) {
                        beginCredit = userEntry.getCreditBalance();
                    }
                    // 贷方科目借方输入（借方正数说明有借方余额）
                    if (userEntry.getDebitBalance() != null && userEntry.getDebitBalance().compareTo(BigDecimal.ZERO) > 0) {
                        beginDebit = userEntry.getDebitBalance();
                    }
                }
            }
            // 确保不为负
            if (beginDebit.compareTo(BigDecimal.ZERO) < 0) beginDebit = BigDecimal.ZERO;
            if (beginCredit.compareTo(BigDecimal.ZERO) < 0) beginCredit = BigDecimal.ZERO;

            // 期末 = 期初 + 本期发生（开账期间无本期发生，current=0）
            AccSubjectBalance bal = new AccSubjectBalance();
            bal.setTenantId(tenantId);
            bal.setSubjectCode(code);
            bal.setFiscalYear(y);
            bal.setPeriodNo(p);
            bal.setBeginDebit(beginDebit);
            bal.setBeginCredit(beginCredit);
            bal.setCurrentDebit(BigDecimal.ZERO);
            bal.setCurrentCredit(BigDecimal.ZERO);
            // 期末余额（C2 统一口径，与过账 accrue SQL 一致）：
            // net = begin_debit + current_debit - begin_credit - current_credit，正数落借方、负数落贷方
            BigDecimal endRaw = beginDebit.add(bal.getCurrentDebit())
                    .subtract(beginCredit).subtract(bal.getCurrentCredit());
            if (endRaw.compareTo(BigDecimal.ZERO) >= 0) {
                bal.setEndDebit(endRaw);
                bal.setEndCredit(BigDecimal.ZERO);
            } else {
                bal.setEndDebit(BigDecimal.ZERO);
                bal.setEndCredit(endRaw.abs());
            }
            bal.setVersion(0);
            toSave.add(bal);
        }

        // 删除当期余额记录（清除旧数据）
        balanceMapper.delete(
                new LambdaQueryWrapper<AccSubjectBalance>()
                        .eq(AccSubjectBalance::getTenantId, tenantId)
                        .eq(AccSubjectBalance::getFiscalYear, y)
                        .eq(AccSubjectBalance::getPeriodNo, p)
        );

        // 批量保存
        for (AccSubjectBalance bal : toSave) {
            // 跳过 begin/end 全为0的科目（避免插入大量无意义记录）
            boolean allZero = bal.getBeginDebit().compareTo(BigDecimal.ZERO) == 0
                    && bal.getBeginCredit().compareTo(BigDecimal.ZERO) == 0
                    && bal.getCurrentDebit().compareTo(BigDecimal.ZERO) == 0
                    && bal.getCurrentCredit().compareTo(BigDecimal.ZERO) == 0;
            if (allZero) continue;
            balanceMapper.insert(bal);
        }
        log.info("期初余额保存完成: tenant={}, year={}, period={}, 实际保存科目数={}",
                tenantId, y, p, toSave.stream().filter(b ->
                        b.getBeginDebit().compareTo(BigDecimal.ZERO) != 0
                                || b.getBeginCredit().compareTo(BigDecimal.ZERO) != 0
                ).count());
    }
}
