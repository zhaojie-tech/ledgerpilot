package com.ledgerpilot.common.calculator.service;

import com.ledgerpilot.common.entity.RateConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 税费计算服务
 *
 * 从 rate_config 表读取实际税率，计算：
 * - 增值税（销项税、进项税、应交增值税）
 * - 企业所得税（标准税率、小型微利、高新优惠）
 * - 税金及附加（城建税、教育费附加、地方教育附加）
 *
 * 计算逻辑完全确定性，不依赖 LLM。
 */
@Service
public class TaxCalculatorService {

    private static final Logger log = LoggerFactory.getLogger(TaxCalculatorService.class);

    private static final int SCALE = 2;

    /** 收入类科目前缀 */
    private static final String[] REVENUE_PREFIXES = {"6001", "6051"};
    /** 成本类科目前缀 */
    private static final String[] COST_PREFIXES = {"6401", "6402", "6403"};
    /** 期间费用类科目前缀 */
    private static final String[] EXPENSE_PREFIXES = {"6601", "6602", "6603", "6604"};

    @Autowired
    private RateConfigServiceInterface rateConfigService;

    // ==================== 税务计算结果 ====================

    /** 增值税计算结果 */
    public static class VATResult {
        private BigDecimal outputVAT;        // 销项税额
        private BigDecimal inputVAT;         // 进项税额
        private BigDecimal vatPayable;       // 应交增值税
        private BigDecimal taxBase;          // 税基（收入合计）
        private BigDecimal taxRate;          // 适用税率
        private List<TaxItem> items = new ArrayList<>();

        public BigDecimal getOutputVAT() { return outputVAT; }
        public void setOutputVAT(BigDecimal outputVAT) { this.outputVAT = outputVAT; }
        public BigDecimal getInputVAT() { return inputVAT; }
        public void setInputVAT(BigDecimal inputVAT) { this.inputVAT = inputVAT; }
        public BigDecimal getVatPayable() { return vatPayable; }
        public void setVatPayable(BigDecimal vatPayable) { this.vatPayable = vatPayable; }
        public BigDecimal getTaxBase() { return taxBase; }
        public void setTaxBase(BigDecimal taxBase) { this.taxBase = taxBase; }
        public BigDecimal getTaxRate() { return taxRate; }
        public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
        public List<TaxItem> getItems() { return items; }
        public void setItems(List<TaxItem> items) { this.items = items; }
    }

    /** 企业所得税计算结果 */
    public static class CITResult {
        private BigDecimal taxableProfit;    // 应税利润
        private BigDecimal citAmount;        // 企业所得税
        private BigDecimal taxRate;          // 适用税率
        private BigDecimal revenue;           // 营业收入
        private BigDecimal cost;             // 营业成本
        private BigDecimal expense;          // 期间费用
        private String taxRateType;          // 税率类型（standard_25/small_profit_20/high_tech_15）
        private List<TaxItem> items = new ArrayList<>();

        public BigDecimal getTaxableProfit() { return taxableProfit; }
        public void setTaxableProfit(BigDecimal taxableProfit) { this.taxableProfit = taxableProfit; }
        public BigDecimal getCitAmount() { return citAmount; }
        public void setCitAmount(BigDecimal citAmount) { this.citAmount = citAmount; }
        public BigDecimal getTaxRate() { return taxRate; }
        public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
        public BigDecimal getCost() { return cost; }
        public void setCost(BigDecimal cost) { this.cost = cost; }
        public BigDecimal getExpense() { return expense; }
        public void setExpense(BigDecimal expense) { this.expense = expense; }
        public String getTaxRateType() { return taxRateType; }
        public void setTaxRateType(String taxRateType) { this.taxRateType = taxRateType; }
        public List<TaxItem> getItems() { return items; }
        public void setItems(List<TaxItem> items) { this.items = items; }
    }

    /** 税金及附加计算结果 */
    public static class SurtaxResult {
        private BigDecimal urbanConstruction;   // 城建税
        private BigDecimal eduSurtax;             // 教育费附加
        private BigDecimal localEduSurtax;        // 地方教育附加
        private BigDecimal total;                // 合计
        private BigDecimal taxBase;              // 税基（应交增值税）
        private List<TaxItem> items = new ArrayList<>();

        public BigDecimal getUrbanConstruction() { return urbanConstruction; }
        public void setUrbanConstruction(BigDecimal urbanConstruction) { this.urbanConstruction = urbanConstruction; }
        public BigDecimal getEduSurtax() { return eduSurtax; }
        public void setEduSurtax(BigDecimal eduSurtax) { this.eduSurtax = eduSurtax; }
        public BigDecimal getLocalEduSurtax() { return localEduSurtax; }
        public void setLocalEduSurtax(BigDecimal localEduSurtax) { this.localEduSurtax = localEduSurtax; }
        public BigDecimal getTotal() { return total; }
        public void setTotal(BigDecimal total) { this.total = total; }
        public BigDecimal getTaxBase() { return taxBase; }
        public void setTaxBase(BigDecimal taxBase) { this.taxBase = taxBase; }
        public List<TaxItem> getItems() { return items; }
        public void setItems(List<TaxItem> items) { this.items = items; }
    }

    /** 税费明细项 */
    public static class TaxItem {
        private String name;
        private BigDecimal amount;
        private String remark;

        public TaxItem() {}
        public TaxItem(String name, BigDecimal amount, String remark) {
            this.name = name; this.amount = amount; this.remark = remark;
        }
        public String getName() { return name; }
        public BigDecimal getAmount() { return amount; }
        public String getRemark() { return remark; }
    }

    // ==================== 公开计算方法 ====================

    /**
     * 计算增值税
     * @param tenantId 租户ID
     * @param totalRevenue 收入合计（不含税）
     * @param totalCost 可抵扣成本合计（不含税）
     * @param deductibleRatio 可抵扣进项税比例（默认0.6，即60%）
     * @return 增值税计算结果
     */
    public VATResult calculateVAT(Long tenantId, BigDecimal totalRevenue,
                                   BigDecimal totalCost, BigDecimal deductibleRatio) {
        VATResult result = new VATResult();
        result.setTaxBase(totalRevenue);

        // 获取增值税税率配置
        RateConfig vatConfig = rateConfigService.getByCategory(tenantId, "vat", null);
        BigDecimal vatRate = BigDecimal.ZERO;
        String subCategory = "general_13"; // 默认一般纳税人13%

        if (vatConfig != null && vatConfig.getEnabled() == 1) {
            vatRate = vatConfig.getFixedRate() != null ? vatConfig.getFixedRate() : new BigDecimal("0.13");
            if (vatConfig.getSubCategory() != null) {
                subCategory = vatConfig.getSubCategory();
            }
        } else {
            // 尝试查一般纳税人13%
            vatRate = new BigDecimal("0.13");
        }

        result.setTaxRate(vatRate);

        // 销项税额 = 收入 × 税率
        BigDecimal outputVAT = totalRevenue.multiply(vatRate)
                .setScale(SCALE, RoundingMode.HALF_UP);
        result.setOutputVAT(outputVAT);
        result.getItems().add(new TaxItem(
                "销项税额（收入 × " + vatRate.multiply(new BigDecimal("100")) + "%）",
                outputVAT,
                "收入合计 " + totalRevenue + " × " + vatRate
        ));

        // 进项税额 = 成本 × 税率 × 可抵扣比例
        BigDecimal inputVAT = totalCost.multiply(vatRate)
                .multiply(deductibleRatio != null ? deductibleRatio : new BigDecimal("0.6"))
                .setScale(SCALE, RoundingMode.HALF_UP);
        result.setInputVAT(inputVAT.negate());
        result.getItems().add(new TaxItem(
                "进项税额（成本 × " + vatRate.multiply(new BigDecimal("100")) + "% × 可抵扣比例）",
                inputVAT.negate(),
                "成本 " + totalCost + " × " + vatRate + " × " + (deductibleRatio != null ? deductibleRatio : "0.6")
        ));

        // 应交增值税 = 销项 - 进项
        BigDecimal vatPayable = outputVAT.subtract(inputVAT).max(BigDecimal.ZERO);
        result.setVatPayable(vatPayable);
        result.getItems().add(new TaxItem(
                "应交增值税",
                vatPayable,
                "如为负数则表示留抵税额"
        ));

        log.info("[增值税计算] tenantId={}, 销项={}, 进项={}, 应交={}, 税率={}",
                tenantId, outputVAT, inputVAT, vatPayable, vatRate);
        return result;
    }

    /**
     * 计算企业所得税
     * @param tenantId 租户ID
     * @param totalRevenue 营业收入
     * @param totalCost 营业成本
     * @param totalExpense 期间费用
     * @return 企业所得税计算结果
     */
    public CITResult calculateCIT(Long tenantId, BigDecimal totalRevenue,
                                   BigDecimal totalCost, BigDecimal totalExpense) {
        CITResult result = new CITResult();
        result.setRevenue(totalRevenue);
        result.setCost(totalCost);
        result.setExpense(totalExpense);

        // 应税利润 = 收入 - 成本 - 期间费用
        BigDecimal taxableProfit = totalRevenue.subtract(totalCost)
                .subtract(totalExpense).max(BigDecimal.ZERO);
        result.setTaxableProfit(taxableProfit);

        // 获取企业所得税税率配置
        RateConfig citConfig = rateConfigService.getByCategory(tenantId, "corp_tax", null);
        BigDecimal citRate = new BigDecimal("0.25");
        String subCategory = "standard_25";
        String rateType = "标准税率（25%）";

        if (citConfig != null && citConfig.getEnabled() == 1) {
            citRate = citConfig.getFixedRate() != null
                    ? citConfig.getFixedRate() : new BigDecimal("0.25");
            subCategory = citConfig.getSubCategory() != null
                    ? citConfig.getSubCategory() : "standard_25";

            if ("small_profit_20".equals(subCategory)) {
                rateType = "小型微利企业优惠税率（20%）";
            } else if ("high_tech_15".equals(subCategory)) {
                rateType = "高新技术企业优惠税率（15%）";
            } else {
                rateType = "标准税率（25%）";
            }
        }

        result.setTaxRate(citRate);
        result.setTaxRateType(subCategory);

        // 企业所得税
        BigDecimal citAmount = taxableProfit.multiply(citRate)
                .setScale(SCALE, RoundingMode.HALF_UP);
        result.setCitAmount(citAmount);

        result.getItems().add(new TaxItem("营业收入", totalRevenue, "6001+6051 贷方发生额"));
        result.getItems().add(new TaxItem("减：营业成本", totalCost.negate(), "6401+6402 借方发生额"));
        result.getItems().add(new TaxItem("减：期间费用", totalExpense.negate(), "6601~6604 借方发生额合计"));
        result.getItems().add(new TaxItem("应税利润", taxableProfit, "营业收入-成本-费用（≤0按0计算）"));
        result.getItems().add(new TaxItem("企业所得税（× " + citRate.multiply(new BigDecimal("100")) + "%）",
                citAmount, rateType));

        log.info("[企业所得税计算] tenantId={}, 应税利润={}, 税率={}({}),税额={}",
                tenantId, taxableProfit, citRate, subCategory, citAmount);
        return result;
    }

    /**
     * 计算税金及附加（城建税、教育费附加、地方教育附加）
     * @param tenantId 租户ID
     * @param vatPayable 应交增值税（基数）
     * @return 税金及附加计算结果
     */
    public SurtaxResult calculateSurtax(Long tenantId, BigDecimal vatPayable) {
        SurtaxResult result = new SurtaxResult();
        result.setTaxBase(vatPayable);

        if (vatPayable == null || vatPayable.compareTo(BigDecimal.ZERO) <= 0) {
            result.setTotal(BigDecimal.ZERO);
            return result;
        }

        // 城建税
        RateConfig urbanConfig = rateConfigService.getByCategory(tenantId, "corp_tax", "urban_construction");
        BigDecimal urbanRate = urbanConfig != null && urbanConfig.getEnabled() == 1 && urbanConfig.getFixedRate() != null
                ? urbanConfig.getFixedRate() : new BigDecimal("0.07");

        BigDecimal urban = vatPayable.multiply(urbanRate).setScale(SCALE, RoundingMode.HALF_UP);
        result.setUrbanConstruction(urban);
        result.getItems().add(new TaxItem(
                "城市维护建设税（" + urbanRate.multiply(new BigDecimal("100")) + "%）",
                urban, "以应交增值税为基数 × " + urbanRate
        ));

        // 教育费附加
        RateConfig eduConfig = rateConfigService.getByCategory(tenantId, "corp_tax", "edu_surtax");
        BigDecimal eduRate = eduConfig != null && eduConfig.getEnabled() == 1 && eduConfig.getFixedRate() != null
                ? eduConfig.getFixedRate() : new BigDecimal("0.03");

        BigDecimal edu = vatPayable.multiply(eduRate).setScale(SCALE, RoundingMode.HALF_UP);
        result.setEduSurtax(edu);
        result.getItems().add(new TaxItem(
                "教育费附加（" + eduRate.multiply(new BigDecimal("100")) + "%）",
                edu, "以应交增值税为基数 × " + eduRate
        ));

        // 地方教育附加
        RateConfig localEduConfig = rateConfigService.getByCategory(tenantId, "corp_tax", "local_edu_surtax");
        BigDecimal localEduRate = localEduConfig != null && localEduConfig.getEnabled() == 1 && localEduConfig.getFixedRate() != null
                ? localEduConfig.getFixedRate() : new BigDecimal("0.02");

        BigDecimal localEdu = vatPayable.multiply(localEduRate).setScale(SCALE, RoundingMode.HALF_UP);
        result.setLocalEduSurtax(localEdu);
        result.getItems().add(new TaxItem(
                "地方教育附加（" + localEduRate.multiply(new BigDecimal("100")) + "%）",
                localEdu, "以应交增值税为基数 × " + localEduRate
        ));

        // 合计
        BigDecimal total = urban.add(edu).add(localEdu).setScale(SCALE, RoundingMode.HALF_UP);
        result.setTotal(total);

        log.info("[税金及附加计算] tenantId={}, 增值税基数={}, 城建={}, 教育={}, 地方={}, 合计={}",
                tenantId, vatPayable, urban, edu, localEdu, total);
        return result;
    }
}
