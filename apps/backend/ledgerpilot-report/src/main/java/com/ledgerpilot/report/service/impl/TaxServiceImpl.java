package com.ledgerpilot.report.service.impl;

import com.ledgerpilot.common.calculator.service.TaxCalculatorService;
import com.ledgerpilot.common.util.TenantContext;
import com.ledgerpilot.report.mapper.TaxMapper;
import com.ledgerpilot.report.service.TaxService;
import com.ledgerpilot.report.vo.TaxVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 税务服务实现
 *
 * 所有税率从 rate_config 表读取，不再使用硬编码。
 * 计算逻辑：
 *   增值税 = 收入 × 税率 - 成本 × 税率 × 可抵扣比例
 *   企业所得税 = (收入 - 成本 - 期间费用) × 税率（从配置读取）
 *   税金及附加 = 应交增值税 × 城建税/教育费附加/地方教育附加税率（从配置读取）
 */
@Service
public class TaxServiceImpl implements TaxService {

    @Autowired
    private TaxMapper taxMapper;

    @Autowired
    private TaxCalculatorService taxCalculatorService;

    /** 收入类科目前缀 */
    private static final String[] REVENUE_PREFIXES = {"6001", "6051"};
    /** 成本类科目前缀 */
    private static final String[] COST_PREFIXES = {"6401", "6402", "6403"};
    /** 期间费用类科目前缀 */
    private static final String[] EXPENSE_PREFIXES = {"6601", "6602", "6603", "6604"};

    private Long getTenantId() {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new RuntimeException("无法获取租户ID，请重新登录");
        }
        return tenantId;
    }

    private String periodStr(int year, int period) {
        return String.format("%d-%02d", year, period);
    }

    @Override
    public List<TaxVO> getTaxSummary(int year, int period) {
        List<TaxVO> list = new ArrayList<>();
        list.add(getVAT(year, period));
        list.add(getCorporateIncomeTax(year, period));
        list.add(getSurtax(year, period));
        return list;
    }

    @Override
    public TaxVO getVAT(int year, int period) {
        Long tenantId = getTenantId();
        Map<String, BigDecimal> revenueMap = querySubjectSummary(tenantId, year, period, REVENUE_PREFIXES);
        Map<String, BigDecimal> costMap = querySubjectSummary(tenantId, year, period, COST_PREFIXES);

        BigDecimal totalRevenue = revenueMap.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = costMap.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 使用 TaxCalculatorService 计算（从 rate_config 读取实际税率）
        TaxCalculatorService.VATResult vatResult = taxCalculatorService.calculateVAT(
                tenantId, totalRevenue, totalCost, new BigDecimal("0.6"));

        TaxVO vat = new TaxVO();
        vat.setTaxTypeName("增值税");
        vat.setTaxRate(vatResult.getTaxRate().multiply(new BigDecimal("100")));
        vat.setTaxBase(vatResult.getTaxBase());
        vat.setTaxAmount(vatResult.getVatPayable());
        vat.setTaxPaid(BigDecimal.ZERO);
        vat.setTaxUnpaid(vatResult.getVatPayable());
        vat.setPeriod(periodStr(year, period));

        List<TaxVO.TaxItemVO> items = new ArrayList<>();
        for (TaxCalculatorService.TaxItem ti : vatResult.getItems()) {
            TaxVO.TaxItemVO item = new TaxVO.TaxItemVO();
            item.setItemName(ti.getName());
            item.setAmount(ti.getAmount());
            item.setRemark(ti.getRemark());
            items.add(item);
        }
        vat.setItems(items);
        return vat;
    }

    @Override
    public TaxVO getCorporateIncomeTax(int year, int period) {
        Long tenantId = getTenantId();

        Map<String, BigDecimal> revenueMap = querySubjectSummary(tenantId, year, period, REVENUE_PREFIXES);
        Map<String, BigDecimal> costMap = querySubjectSummary(tenantId, year, period, COST_PREFIXES);
        Map<String, BigDecimal> expenseMap = querySubjectSummary(tenantId, year, period, EXPENSE_PREFIXES);

        BigDecimal totalRevenue = revenueMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCost = costMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = expenseMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // 使用 TaxCalculatorService 计算（从 rate_config 读取实际税率）
        TaxCalculatorService.CITResult citResult = taxCalculatorService.calculateCIT(
                tenantId, totalRevenue, totalCost, totalExpense);

        TaxVO result = new TaxVO();
        result.setTaxTypeName("企业所得税");
        result.setTaxRate(citResult.getTaxRate().multiply(new BigDecimal("100")));
        result.setTaxBase(citResult.getTaxableProfit());
        result.setTaxAmount(citResult.getCitAmount());
        result.setTaxPaid(BigDecimal.ZERO);
        result.setTaxUnpaid(citResult.getCitAmount());
        result.setPeriod(periodStr(year, period));

        List<TaxVO.TaxItemVO> items = new ArrayList<>();
        for (TaxCalculatorService.TaxItem ti : citResult.getItems()) {
            TaxVO.TaxItemVO item = new TaxVO.TaxItemVO();
            item.setItemName(ti.getName());
            item.setAmount(ti.getAmount());
            item.setRemark(ti.getRemark());
            items.add(item);
        }
        result.setItems(items);
        return result;
    }

    @Override
    public TaxVO getSurtax(int year, int period) {
        // 税金及附加以增值税为基数计算
        TaxVO vat = getVAT(year, period);
        BigDecimal vatPayable = vat.getTaxAmount();

        // 使用 TaxCalculatorService 计算（从 rate_config 读取实际税率）
        TaxCalculatorService.SurtaxResult surtaxResult = taxCalculatorService.calculateSurtax(
                getTenantId(), vatPayable);

        TaxVO result = new TaxVO();
        result.setTaxTypeName("税金及附加");
        result.setTaxBase(surtaxResult.getTaxBase());
        result.setTaxAmount(surtaxResult.getTotal());
        result.setTaxPaid(BigDecimal.ZERO);
        result.setTaxUnpaid(surtaxResult.getTotal());
        result.setPeriod(periodStr(year, period));

        List<TaxVO.TaxItemVO> items = new ArrayList<>();
        for (TaxCalculatorService.TaxItem ti : surtaxResult.getItems()) {
            TaxVO.TaxItemVO item = new TaxVO.TaxItemVO();
            item.setItemName(ti.getName());
            item.setAmount(ti.getAmount());
            item.setRemark(ti.getRemark());
            items.add(item);
        }
        result.setItems(items);
        return result;
    }

    @Override
    public List<Map<String, Object>> getAvailablePeriods() {
        Long tenantId = getTenantId();
        return taxMapper.selectAvailablePeriods(tenantId);
    }

    /**
     * 按多个科目前缀查询发生额汇总
     */
    private Map<String, BigDecimal> querySubjectSummary(Long tenantId, int year, int period, String[] prefixes) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (String prefix : prefixes) {
            List<Map<String, Object>> rows = taxMapper.selectSummaryBySubjectPrefix(tenantId, year, period, prefix);
            for (Map<String, Object> row : rows) {
                String code = (String) row.get("subject_code");
                String direction = (String) row.get("direction");
                Object debitObj = row.get("total_debit");
                Object creditObj = row.get("total_credit");
                BigDecimal debit = debitObj != null ? new BigDecimal(debitObj.toString()) : BigDecimal.ZERO;
                BigDecimal credit = creditObj != null ? new BigDecimal(creditObj.toString()) : BigDecimal.ZERO;
                // 贷方为收入，借方为成本/费用
                BigDecimal amount = "CREDIT".equals(direction) ? credit : debit;
                result.put(code, amount);
            }
        }
        return result;
    }
}
