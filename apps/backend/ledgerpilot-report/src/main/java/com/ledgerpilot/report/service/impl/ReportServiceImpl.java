package com.ledgerpilot.report.service.impl;

import com.ledgerpilot.common.exception.LedgerPilotException;
import com.ledgerpilot.common.util.TenantContext;
import com.ledgerpilot.report.entity.SubjectBalance;
import com.ledgerpilot.report.mapper.SubjectBalanceMapper;
import com.ledgerpilot.report.service.ReportService;
import com.ledgerpilot.report.vo.BalanceSheetVO;
import com.ledgerpilot.report.vo.ProfitStatementVO;
import com.ledgerpilot.report.vo.SubjectBalanceReportVO;
import com.ledgerpilot.voucher.entity.AccSubject;
import com.ledgerpilot.voucher.mapper.SubjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报表服务实现
 *
 * 利润表：基于利润表科目本期发生额计算（收入类贷方为正，成本费用类借方为负）
 * 资产负债表：基于期末余额数据（end_debit / end_credit）
 * 科目余额表：展示每个科目的期初+本期+期末
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final SubjectBalanceMapper subjectBalanceMapper;
    private final SubjectMapper subjectMapper;

    // ==================== 利润表 ====================
    @Override
    public Object getProfitStatement(Integer year, Integer period) {
        Long tenantId = getTenantId();
        ProfitStatementVO vo = new ProfitStatementVO();
        vo.setYear(year);
        vo.setPeriod(period);

        List<Map<String, Object>> plData = subjectBalanceMapper.selectPLCurrentPeriod(tenantId, year, period);

        // 营业收入：贷方发生（6001 主营业务收入、6051 其他业务收入）
        BigDecimal mainBusRevenue = BigDecimal.ZERO;
        BigDecimal otherBusRevenue = BigDecimal.ZERO;

        // 营业成本：借方发生（6401 主营业务成本、6402 其他业务成本）
        BigDecimal mainBusCost = BigDecimal.ZERO;
        BigDecimal otherBusCost = BigDecimal.ZERO;

        // 税金及附加：借方发生（6405）
        BigDecimal taxes = BigDecimal.ZERO;

        // 销售费用：借方发生（6601）
        BigDecimal selling = BigDecimal.ZERO;

        // 管理费用：借方发生（6602/6603）
        BigDecimal admin = BigDecimal.ZERO;

        // 财务费用：借方发生（6604，正数表示费用，负数表示利息收入/汇兑收益）
        BigDecimal financial = BigDecimal.ZERO;

        // 营业外收支（6901 营业外收入、6711 营业外支出）
        BigDecimal nonOpIncome = BigDecimal.ZERO;
        BigDecimal nonOpExpense = BigDecimal.ZERO;

        for (Map<String, Object> row : plData) {
            String code = (String) row.get("subject_code");
            String dir = (String) row.get("direction");
            BigDecimal debit = toDecimal(row.get("current_debit"));
            BigDecimal credit = toDecimal(row.get("current_credit"));

            if (dir == null) dir = "DEBIT";

            if (code != null && code.startsWith("6001")) {
                // 主营业务收入
                if ("CREDIT".equals(dir)) mainBusRevenue = mainBusRevenue.add(credit);
                else mainBusRevenue = mainBusRevenue.subtract(debit);
            } else if (code != null && code.startsWith("6051")) {
                // 其他业务收入
                if ("CREDIT".equals(dir)) otherBusRevenue = otherBusRevenue.add(credit);
                else otherBusRevenue = otherBusRevenue.subtract(debit);
            } else if (code != null && code.startsWith("6401")) {
                // 主营业务成本（借方为正）
                if ("DEBIT".equals(dir)) mainBusCost = mainBusCost.add(debit);
                else mainBusCost = mainBusCost.subtract(credit);
            } else if (code != null && code.startsWith("6402")) {
                // 其他业务成本（借方为正）
                if ("DEBIT".equals(dir)) otherBusCost = otherBusCost.add(debit);
                else otherBusCost = otherBusCost.subtract(credit);
            } else if (code != null && code.startsWith("6405")) {
                // 税金及附加（借方为正）
                if ("DEBIT".equals(dir)) taxes = taxes.add(debit);
            } else if (code != null && code.startsWith("6601")) {
                // 销售费用（借方为正）
                if ("DEBIT".equals(dir)) selling = selling.add(debit);
            } else if (code != null && code.startsWith("6602") || code != null && code.startsWith("6603")) {
                // 管理费用（借方为正）
                if ("DEBIT".equals(dir)) admin = admin.add(debit);
            } else if (code != null && code.startsWith("6604")) {
                // 财务费用（借方为正；贷方表示利息收入/汇兑收益，冲减费用）
                if ("DEBIT".equals(dir)) financial = financial.add(debit);
                else financial = financial.subtract(credit);
            } else if (code != null && code.startsWith("6901")) {
                // 营业外收入
                if ("CREDIT".equals(dir)) nonOpIncome = nonOpIncome.add(credit);
                else nonOpIncome = nonOpIncome.subtract(debit);
            } else if (code != null && code.startsWith("6711")) {
                // 营业外支出
                if ("DEBIT".equals(dir)) nonOpExpense = nonOpExpense.add(debit);
                else nonOpExpense = nonOpExpense.subtract(credit);
            }
        }

        vo.setMainBusinessRevenue(mainBusRevenue);
        vo.setOtherBusinessRevenue(otherBusRevenue);
        vo.setOperatingRevenue(mainBusRevenue.add(otherBusRevenue));

        vo.setMainBusinessCost(mainBusCost);
        vo.setOtherBusinessCost(otherBusCost);
        vo.setOperatingCost(mainBusCost.add(otherBusCost));

        vo.setTaxesAndSurcharges(taxes);
        vo.setSellingExpense(selling);
        vo.setAdministrativeExpense(admin);
        vo.setFinancialExpense(financial);
        vo.setPeriodExpenses(selling.add(admin).add(financial));

        vo.setNonOperatingIncome(nonOpIncome);
        vo.setNonOperatingExpense(nonOpExpense);

        // 计算
        // 毛利润 = 营业收入 - 营业成本
        vo.setGrossProfit(vo.getOperatingRevenue().subtract(vo.getOperatingCost()));
        // 营业利润 = 毛利润 - 税金及附加 - 期间费用（+其他收益，简化处理为0）
        vo.setOperatingProfit(
            vo.getGrossProfit()
              .subtract(vo.getTaxesAndSurcharges())
              .subtract(vo.getPeriodExpenses())
        );
        // 利润总额 = 营业利润 + 营业外收支
        vo.setTotalProfit(vo.getOperatingProfit().add(nonOpIncome).subtract(nonOpExpense));
        // 净利润 = 利润总额（简化：暂不计所得税，6902所得税费用为0）
        vo.setNetProfit(vo.getTotalProfit());

        // 明细行
        List<ProfitStatementVO.ProfitStatementRow> rows = buildProfitRows(plData);
        vo.setRows(rows);

        return vo;
    }

    private List<ProfitStatementVO.ProfitStatementRow> buildProfitRows(List<Map<String, Object>> plData) {
        List<ProfitStatementVO.ProfitStatementRow> rows = new ArrayList<>();
        // 提取有发生额的科目
        Set<String> printedCodes = new HashSet<>();
        for (Map<String, Object> row : plData) {
            String code = (String) row.get("subject_code");
            if (printedCodes.contains(code)) continue;
            printedCodes.add(code);
            ProfitStatementVO.ProfitStatementRow r = new ProfitStatementVO.ProfitStatementRow();
            r.setSubjectCode(code);
            r.setSubjectName(getSubjectName(code));
            r.setDirection((String) row.get("direction"));
            BigDecimal debit = toDecimal(row.get("current_debit"));
            BigDecimal credit = toDecimal(row.get("current_credit"));
            String dir = r.getDirection() == null ? "DEBIT" : r.getDirection();
            if ("CREDIT".equals(dir)) {
                r.setAmount(credit);
                r.setCategory("revenue");
            } else {
                r.setAmount(debit.negate()); // 费用类显示为负数
                r.setCategory("expense");
            }
            rows.add(r);
        }
        return rows;
    }

    // ==================== 资产负债表 ====================
    @Override
    public Object getBalanceSheet(Integer year, Integer period) {
        Long tenantId = getTenantId();
        BalanceSheetVO vo = new BalanceSheetVO();
        vo.setYear(year);
        vo.setPeriod(period);

        List<Map<String, Object>> data = subjectBalanceMapper.selectBalanceByYearAndPeriod(tenantId, year, period);

        // 按科目编码建立 map
        Map<String, Map<String, Object>> balanceMap = new HashMap<>();
        for (Map<String, Object> row : data) {
            String code = (String) row.get("subject_code");
            balanceMap.put(code, row);
        }

        // 货币资金：1001 + 1002 + 1012（取 end_debit）
        vo.setMonetaryFunds(
            sumEndBalance(balanceMap, "1001", "DEBIT")
            .add(sumEndBalance(balanceMap, "1002", "DEBIT"))
            .add(sumEndBalance(balanceMap, "1012", "DEBIT"))
        );
        // 应收账款：1122
        vo.setAccountsReceivable(sumEndBalance(balanceMap, "1122", "DEBIT"));
        // 存货：1403 + 1405
        vo.setInventory(
            sumEndBalance(balanceMap, "1403", "DEBIT")
            .add(sumEndBalance(balanceMap, "1405", "DEBIT"))
        );

        // 流动资产合计 = 货币资金 + 应收账款 + 存货 + 其他流动资产
        // 简化：只汇总显示的三大项
        BigDecimal currentAssets = vo.getMonetaryFunds()
            .add(vo.getAccountsReceivable())
            .add(vo.getInventory());
        // 加上其他应收款（1221）
        currentAssets = currentAssets.add(sumEndBalance(balanceMap, "1221", "DEBIT"));
        // 预付款项（1123）
        currentAssets = currentAssets.add(sumEndBalance(balanceMap, "1123", "DEBIT"));
        // 应收票据（1121）
        currentAssets = currentAssets.add(sumEndBalance(balanceMap, "1121", "DEBIT"));
        // 其他流动资产
        vo.setOtherCurrentAssets(BigDecimal.ZERO);
        vo.setCurrentAssets(currentAssets);

        // 非流动资产
        // 固定资产（1601）
        vo.setFixedAssets(sumEndBalance(balanceMap, "1601", "DEBIT"));
        // 无形资产（1701）
        vo.setIntangibleAssets(sumEndBalance(balanceMap, "1701", "DEBIT"));
        // 长期待摊费用（1801）
        vo.setLongTermDeferredAssets(sumEndBalance(balanceMap, "1801", "DEBIT"));
        // 其他非流动资产
        vo.setOtherNonCurrentAssets(BigDecimal.ZERO);
        vo.setNonCurrentAssets(
            vo.getFixedAssets()
              .add(vo.getIntangibleAssets())
              .add(vo.getLongTermDeferredAssets())
        );

        vo.setTotalAssets(vo.getCurrentAssets().add(vo.getNonCurrentAssets()));

        // 负债
        // 短期借款（2001）
        vo.setShortTermBorrowings(sumEndBalance(balanceMap, "2001", "CREDIT"));
        // 应付票据（2201）
        vo.setNotesPayable(sumEndBalance(balanceMap, "2201", "CREDIT"));
        // 应付账款（2202）
        vo.setAccountsPayable(sumEndBalance(balanceMap, "2202", "CREDIT"));
        // 应付职工薪酬（2211）
        vo.setEmployeeCompensationPayable(sumEndBalance(balanceMap, "2211", "CREDIT"));
        // 应交税费（2221）
        vo.setTaxesPayable(sumEndBalance(balanceMap, "2221", "CREDIT"));
        // 其他应付款（2241）
        vo.setOtherPayables(sumEndBalance(balanceMap, "2241", "CREDIT"));
        // 预收款项（2203）
        vo.setAdvanceReceipts(sumEndBalance(balanceMap, "2203", "CREDIT"));
        // 合同负债（2204）
        vo.setContractLiabilities(sumEndBalance(balanceMap, "2204", "CREDIT"));

        vo.setCurrentLiabilities(
            vo.getShortTermBorrowings()
              .add(vo.getNotesPayable())
              .add(vo.getAccountsPayable())
              .add(vo.getEmployeeCompensationPayable())
              .add(vo.getTaxesPayable())
              .add(vo.getOtherPayables())
              .add(vo.getAdvanceReceipts())
              .add(vo.getContractLiabilities())
        );

        // 长期借款（2501）
        vo.setLongTermBorrowings(sumEndBalance(balanceMap, "2501", "CREDIT"));
        vo.setNonCurrentLiabilities(vo.getLongTermBorrowings());
        vo.setTotalLiabilities(vo.getCurrentLiabilities().add(vo.getNonCurrentLiabilities()));

        // 所有者权益
        // 实收资本（4001）
        vo.setPaidInCapital(sumEndBalance(balanceMap, "4001", "CREDIT"));
        // 资本公积（4002）
        vo.setCapitalReserve(sumEndBalance(balanceMap, "4002", "CREDIT"));
        // 盈余公积（4101/4102/4103）
        vo.setSurplusReserve(
            sumEndBalance(balanceMap, "4101", "CREDIT")
            .add(sumEndBalance(balanceMap, "4102", "CREDIT"))
            .add(sumEndBalance(balanceMap, "4103", "CREDIT"))
        );
        // 未分配利润（4104）
        vo.setRetainedEarnings(sumEndBalance(balanceMap, "4104", "CREDIT"));

        vo.setTotalEquity(
            vo.getPaidInCapital()
              .add(vo.getCapitalReserve())
              .add(vo.getSurplusReserve())
              .add(vo.getRetainedEarnings())
        );

        vo.setTotalLiabilitiesAndEquity(
            vo.getTotalLiabilities().add(vo.getTotalEquity())
        );

        vo.setBalanced(
            vo.getTotalAssets().subtract(vo.getTotalLiabilitiesAndEquity()).abs().compareTo(BigDecimal.ONE) < 0
        );

        // 注册资金 = 实收资本期初数（简化：直接用期末数）
        vo.setRegisteredCapital(vo.getPaidInCapital());

        return vo;
    }

    private BigDecimal sumEndBalance(Map<String, Map<String, Object>> map, String code, String direction) {
        Map<String, Object> row = map.get(code);
        if (row == null) return BigDecimal.ZERO;
        if ("DEBIT".equals(direction)) {
            return toDecimal(row.get("end_debit"));
        } else {
            return toDecimal(row.get("end_credit"));
        }
    }

    // ==================== 现金流量表（简化版） ====================
    @Override
    public Object getCashFlowStatement(Integer year, Integer period) {
        Long tenantId = getTenantId();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportName", "现金流量表");
        report.put("year", year);
        report.put("period", period);
        report.put("note", "简化版：基于货币资金科目（1001/1002/1012）本期变动估算");

        // 从科目余额表取货币资金科目
        List<Map<String, Object>> data = subjectBalanceMapper.selectBalanceByYearAndPeriod(tenantId, year, period);
        BigDecimal cashInflow = BigDecimal.ZERO;
        BigDecimal cashOutflow = BigDecimal.ZERO;

        for (Map<String, Object> row : data) {
            String code = (String) row.get("subject_code");
            if ("1001".equals(code) || "1002".equals(code) || "1012".equals(code)) {
                BigDecimal debit = toDecimal(row.get("current_debit"));
                BigDecimal credit = toDecimal(row.get("current_credit"));
                cashInflow = cashInflow.add(debit);
                cashOutflow = cashOutflow.add(credit);
            }
        }

        BigDecimal netIncrease = cashInflow.subtract(cashOutflow);

        Map<String, Object> operating = new LinkedHashMap<>();
        operating.put("name", "经营活动现金流量（估算）");
        operating.put("cashInflow", cashInflow);
        operating.put("cashOutflow", cashOutflow);
        operating.put("netCashFlow", netIncrease);

        Map<String, Object> investing = new LinkedHashMap<>();
        investing.put("name", "投资活动现金流量");
        investing.put("cashInflow", BigDecimal.ZERO);
        investing.put("cashOutflow", BigDecimal.ZERO);
        investing.put("netCashFlow", BigDecimal.ZERO);

        Map<String, Object> financing = new LinkedHashMap<>();
        financing.put("name", "筹资活动现金流量");
        financing.put("cashInflow", BigDecimal.ZERO);
        financing.put("cashOutflow", BigDecimal.ZERO);
        financing.put("netCashFlow", BigDecimal.ZERO);

        report.put("operating", operating);
        report.put("investing", investing);
        report.put("financing", financing);
        report.put("netIncrease", netIncrease);
        report.put("note", "说明：现金流量表完整实现需从凭证分录中按现金流量科目明细统计，本版为简化估算");

        return report;
    }

    // ==================== 科目余额表 ====================
    @Override
    public Object getSubjectBalanceReport(Integer year, Integer period) {
        Long tenantId = getTenantId();

        int count = subjectBalanceMapper.countByYearAndPeriod(tenantId, year, period);
        if (count == 0) {
            throw new LedgerPilotException("NO_DATA",
                "第 " + period + " 期暂无余额数据，请先录入凭证并过账");
        }

        List<Map<String, Object>> data = subjectBalanceMapper.selectBalanceByYearAndPeriod(tenantId, year, period);

        // 查所有科目信息（构建 name/type map）
        List<AccSubject> subjects = subjectMapper.selectTreeList();
        Map<String, AccSubject> subjectMap = new HashMap<>();
        for (AccSubject s : subjects) {
            subjectMap.put(s.getSubjectCode(), s);
        }

        SubjectBalanceReportVO vo = new SubjectBalanceReportVO();
        vo.setYear(year);
        vo.setPeriod(period);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        BigDecimal netBalance = BigDecimal.ZERO;

        List<SubjectBalanceReportVO.SubjectBalanceRow> rows = new ArrayList<>();
        for (Map<String, Object> row : data) {
            String code = (String) row.get("subject_code");
            AccSubject subject = subjectMap.get(code);
            if (subject == null) {
                // 科目表之外的余额行不再静默跳过（错账会从报表"蒸发"），告警定位数据问题
                log.warn("科目余额表存在科目表之外的科目，已跳过展示: code={}", code);
                continue;
            }

            SubjectBalanceReportVO.SubjectBalanceRow r =
                new SubjectBalanceReportVO.SubjectBalanceRow();
            r.setSubjectCode(code);
            r.setSubjectName(subject.getSubjectName());
            r.setSubjectType(subject.getSubjectType());
            r.setDirection(subject.getDirection());
            r.setIsDetail(subject.getIsDetail() == 1);
            r.setLevel(subject.getLevel());

            BigDecimal bd = toDecimal(row.get("begin_debit"));
            BigDecimal bc = toDecimal(row.get("begin_credit"));
            BigDecimal cd = toDecimal(row.get("current_debit"));
            BigDecimal cc = toDecimal(row.get("current_credit"));
            BigDecimal ed = toDecimal(row.get("end_debit"));
            BigDecimal ec = toDecimal(row.get("end_credit"));

            r.setBeginDebit(bd);
            r.setBeginCredit(bc);
            r.setCurrentDebit(cd);
            r.setCurrentCredit(cc);
            r.setEndDebit(ed);
            r.setEndCredit(ec);

            // 借贷方合计（取绝对值合计）
            totalDebit = totalDebit.add(cd.abs()).add(bd.abs());
            totalCredit = totalCredit.add(cc.abs()).add(bc.abs());

            // 净余额
            if ("DEBIT".equals(subject.getDirection())) {
                netBalance = netBalance.add(ed.subtract(ec));
            } else {
                netBalance = netBalance.add(ec.subtract(ed));
            }

            rows.add(r);
        }

        // 排序：按科目编码
        rows.sort(Comparator.comparing(SubjectBalanceReportVO.SubjectBalanceRow::getSubjectCode));

        vo.setRows(rows);
        vo.setTotalDebit(totalDebit);
        vo.setTotalCredit(totalCredit);
        vo.setNetBalance(netBalance);

        return vo;
    }

    // ==================== 导出 ====================
    @Override
    public String exportReport(String type, Integer year, Integer period) {
        log.info("导出报表: type={}, year={}, period={}", type, year, period);
        // TODO: EasyExcel 模板导出
        return "/exports/report_" + type + "_" + year + "_" + period + ".xlsx";
    }

    // ==================== 工具方法 ====================
    private Long getTenantId() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new LedgerPilotException("TENANT_ERROR", "无法获取租户ID，请重新登录");
        }
        return tenantId;
    }

    private BigDecimal toDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal) return (BigDecimal) val;
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String getSubjectName(String code) {
        if (code == null) return "";
        // 常用科目名称映射
        Map<String, String> nameMap = new HashMap<>();
        nameMap.put("6001", "主营业务收入");
        nameMap.put("6051", "其他业务收入");
        nameMap.put("6401", "主营业务成本");
        nameMap.put("6402", "其他业务成本");
        nameMap.put("6405", "税金及附加");
        nameMap.put("6601", "销售费用");
        nameMap.put("6602", "管理费用");
        nameMap.put("6603", "管理费用");
        nameMap.put("6604", "财务费用");
        nameMap.put("6711", "营业外支出");
        nameMap.put("6901", "营业外收入");
        return nameMap.getOrDefault(code, code);
    }
}
