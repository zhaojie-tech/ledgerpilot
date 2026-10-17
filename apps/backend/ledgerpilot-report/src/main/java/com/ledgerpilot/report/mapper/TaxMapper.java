package com.ledgerpilot.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 税务计算 Mapper
 * acc_voucher_entry 表结构：amount（正数）+ direction（DEBIT/CREDIT）
 */
@Mapper
public interface TaxMapper {

    /**
     * 查询指定期间的科目汇总发生额（从凭证分录表）
     * direction=DEBIT → 借方金额，direction=CREDIT → 贷方金额
     */
    @Select("<script>" +
            "SELECT e.subject_code, s.subject_name, s.direction, " +
            "       SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount ELSE 0 END) AS total_debit, " +
            "       SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE 0 END) AS total_credit " +
            "FROM acc_voucher_entry e " +
            "JOIN acc_subject s ON s.subject_code = e.subject_code AND s.tenant_id = e.tenant_id AND s.deleted = 0 " +
            "JOIN acc_voucher v ON v.id = e.voucher_id AND v.tenant_id = e.tenant_id AND v.deleted = 0 " +
            "WHERE e.tenant_id = #{tenantId} " +
            "  AND YEAR(v.voucher_date) = #{year} " +
            "  AND MONTH(v.voucher_date) = #{period} " +
            "  AND v.status = 'APPROVED' " +
            "  AND e.subject_code LIKE CONCAT(#{prefix}, '%') " +
            "GROUP BY e.subject_code, s.subject_name, s.direction " +
            "</script>")
    List<Map<String, Object>> selectSummaryBySubjectPrefix(
            @Param("tenantId") Long tenantId,
            @Param("year") Integer year,
            @Param("period") Integer period,
            @Param("prefix") String prefix
    );

    /**
     * 统计指定期间有凭证的可用期间列表
     */
    @Select("SELECT DISTINCT YEAR(v.voucher_date) AS fiscal_year, MONTH(v.voucher_date) AS period_no " +
            "FROM acc_voucher v " +
            "WHERE v.tenant_id = #{tenantId} AND v.deleted = 0 AND v.status = 'APPROVED' " +
            "ORDER BY fiscal_year DESC, period_no DESC")
    List<Map<String, Object>> selectAvailablePeriods(@Param("tenantId") Long tenantId);

    /**
     * 查询应交税费科目的贷方余额（未交税额）
     * 2221 系列科目：贷方累计表示欠交税额
     */
    @Select("<script>" +
            "SELECT e.subject_code, s.subject_name, SUM(e.amount) AS credit_sum " +
            "FROM acc_voucher_entry e " +
            "JOIN acc_subject s ON s.subject_code = e.subject_code AND s.tenant_id = e.tenant_id AND s.deleted = 0 " +
            "JOIN acc_voucher v ON v.id = e.voucher_id AND v.tenant_id = e.tenant_id AND v.deleted = 0 " +
            "WHERE e.tenant_id = #{tenantId} " +
            "  AND YEAR(v.voucher_date) &lt;= #{year} " +
            "  AND MONTH(v.voucher_date) &lt;= #{period} " +
            "  AND v.status = 'APPROVED' " +
            "  AND e.direction = 'CREDIT' " +
            "  AND e.subject_code LIKE '2221%' " +
            "GROUP BY e.subject_code, s.subject_name " +
            "</script>")
    List<Map<String, Object>> selectTaxPayableBalance(
            @Param("tenantId") Long tenantId,
            @Param("year") Integer year,
            @Param("period") Integer period
    );
}
