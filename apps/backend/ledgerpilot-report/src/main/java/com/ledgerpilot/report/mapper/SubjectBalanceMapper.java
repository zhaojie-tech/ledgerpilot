package com.ledgerpilot.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ledgerpilot.report.entity.SubjectBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 科目余额表 Mapper
 * 报表模块专用
 */
@Mapper
public interface SubjectBalanceMapper extends BaseMapper<SubjectBalance> {

    /**
     * 查询指定期间的科目余额（带租户过滤）
     */
    @Select("SELECT b.subject_code, b.fiscal_year, b.period_no, " +
           "b.begin_debit, b.begin_credit, b.current_debit, b.current_credit, b.end_debit, b.end_credit " +
           "FROM acc_subject_balance b " +
           "WHERE b.tenant_id = #{tenantId} AND b.fiscal_year = #{year} AND b.period_no = #{period}")
    List<Map<String, Object>> selectBalanceByYearAndPeriod(
        @Param("tenantId") Long tenantId,
        @Param("year") Integer year,
        @Param("period") Integer period
    );

    /**
     * 查询利润表相关科目的本期发生额（按科目编码过滤）
     * direction 来自 acc_subject 表（非 acc_subject_balance）
     */
    @Select("SELECT b.subject_code, s.direction, b.current_debit, b.current_credit " +
           "FROM acc_subject_balance b " +
           "JOIN acc_subject s ON s.subject_code = b.subject_code AND s.tenant_id = b.tenant_id AND s.deleted = 0 " +
           "WHERE b.tenant_id = #{tenantId} AND b.fiscal_year = #{year} AND b.period_no = #{period} " +
           "AND s.subject_type = 'PROFIT_LOSS'")
    List<Map<String, Object>> selectPLCurrentPeriod(
        @Param("tenantId") Long tenantId,
        @Param("year") Integer year,
        @Param("period") Integer period
    );

    /**
     * 统计某期是否有余额数据
     */
    @Select("SELECT COUNT(*) FROM acc_subject_balance " +
           "WHERE tenant_id = #{tenantId} AND fiscal_year = #{year} AND period_no = #{period}")
    int countByYearAndPeriod(
        @Param("tenantId") Long tenantId,
        @Param("year") Integer year,
        @Param("period") Integer period
    );
}
