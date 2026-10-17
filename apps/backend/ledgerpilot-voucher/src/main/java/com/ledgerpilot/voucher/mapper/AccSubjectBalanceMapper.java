package com.ledgerpilot.voucher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ledgerpilot.voucher.entity.AccSubjectBalance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 科目余额Mapper
 *
 * 并发口径：发生额走原子增量 + version 条件更新，
 * rows=0 表示版本冲突（由服务层重试/抛出），杜绝读-改-写丢更新。
 *
 * ⚠️ SQL 求值序陷阱：MySQL 的 UPDATE SET 从左到右求值且后面的赋值能看到前面的新值，
 * H2 则整行按旧值求值——因此期末重算（end_*）必须排在发生额（current_*）更新之前，
 * 且表达式内显式使用「旧值 + 增量」，两种数据库语义下结果才一致。
 */
@Mapper
public interface AccSubjectBalanceMapper extends BaseMapper<AccSubjectBalance> {

    /**
     * 原子累计发生额并重算期末（统一口径 end = begin_debit + current_debit - current_credit，
     * 正数落借方、负数落贷方），version 条件防丢更新。
     *
     * @param debitDelta  借方增量（可为负，红冲/逆运算）
     * @param creditDelta 贷方增量（可为负）
     * @return rows=1 成功；rows=0 版本冲突
     */
    @Update("UPDATE acc_subject_balance SET " +
            "end_debit = GREATEST(COALESCE(begin_debit,0) + COALESCE(current_debit,0) + #{debitDelta} " +
            "                     - COALESCE(current_credit,0) - #{creditDelta}, 0), " +
            "end_credit = GREATEST(COALESCE(current_credit,0) + #{creditDelta} " +
            "                      - COALESCE(begin_debit,0) - COALESCE(current_debit,0) - #{debitDelta}, 0), " +
            "current_debit = COALESCE(current_debit,0) + #{debitDelta}, " +
            "current_credit = COALESCE(current_credit,0) + #{creditDelta}, " +
            "version = version + 1 " +
            "WHERE id = #{id} AND version = #{version}")
    int accrue(@Param("id") Long id,
               @Param("debitDelta") BigDecimal debitDelta,
               @Param("creditDelta") BigDecimal creditDelta,
               @Param("version") Integer version);
}
