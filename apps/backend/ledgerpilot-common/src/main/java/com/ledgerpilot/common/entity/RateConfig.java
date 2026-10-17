package com.ledgerpilot.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费率配置表
 * 支持社保、公积金、增值税、企业所得税、固定资产折旧等多种费率类型
 */
@TableName("rate_config")
public class RateConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /**
     * 费率分类
     * - social: 社保（五险）
     * - housing: 住房公积金
     * - vat: 增值税
     * - corp_tax: 企业所得税
     * - depreciation: 固定资产折旧
     */
    private String category;

    /**
     * 子分类
     * - social: pension(养老保险), medical(医疗保险), unemployment(失业保险),
     *           work_injury(工伤保险), maternity(生育保险), disability(残疾人就业保障金)
     * - housing: housing_fund(住房公积金)
     * - vat: general_13(一般纳税人13%), general_9(一般纳税人9%), general_6(一般纳税人6%),
     *        small_3(小规模3%), small_5(小规模5%)
     * - corp_tax: standard_25(标准25%), small_profit_20(小型微利20%), high_tech_15(高新15%), etc.
     * - depreciation: building(房屋), structure(构筑物), machinery(机械设备), vehicle(运输设备),
     *                 electronic(电子设备), furniture(办公家具)
     */
    private String subCategory;

    /**
     * 个人/从动费率（百分比形式存储，如 0.08 表示 8%）
     * - 社保/公积金：个人费率
     * - 折旧：折旧年限
     */
    private BigDecimal personalRate;

    /**
     * 单位/主费率（百分比形式存储，如 0.16 表示 16%）
     * - 社保/公积金：单位费率
     * - 折旧：残值率
     */
    private BigDecimal employerRate;

    /**
     * 固定费率（百分比形式存储，如 0.13 表示 13%）
     * - 增值税：使用 fixedRate
     * - 企业所得税：使用 fixedRate
     * - 折旧：进项税额税率
     */
    private BigDecimal fixedRate;

    /**
     * 单位：PERCENT（百分比）或 FIXED（固定金额）
     */
    private String unit;

    /** 描述/说明 */
    private String description;

    /** 启用状态：1=启用，0=禁用 */
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记 */
    private Integer deleted;

    private Long createdBy;
    private Long updatedBy;

    // ---- 手动 Getters & Setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }

    public BigDecimal getPersonalRate() { return personalRate; }
    public void setPersonalRate(BigDecimal personalRate) { this.personalRate = personalRate; }

    public BigDecimal getEmployerRate() { return employerRate; }
    public void setEmployerRate(BigDecimal employerRate) { this.employerRate = employerRate; }

    public BigDecimal getFixedRate() { return fixedRate; }
    public void setFixedRate(BigDecimal fixedRate) { this.fixedRate = fixedRate; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
