package com.ledgerpilot.voucher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ledgerpilot.voucher.entity.AccVoucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 凭证Mapper
 */
@Mapper
public interface AccVoucherMapper extends BaseMapper<AccVoucher> {

    /**
     * 条件状态迁移（CAS）：status 从 from 迁到 to，rows=0 表示并发冲突或状态不符。
     * post_at 仅在过账时写入。
     */
    @Update("UPDATE acc_voucher SET status = #{to}, " +
            "post_at = CASE WHEN #{to} = 'POSTED' THEN #{now} ELSE post_at END " +
            "WHERE id = #{id} AND status = #{from} AND deleted = 0")
    int casStatus(@Param("id") Long id, @Param("from") String from,
                  @Param("to") String to, @Param("now") LocalDateTime now);

    /** 作废：允许多个来源状态的一次性 CAS */
    @Update("UPDATE acc_voucher SET status = 'VOIDED' " +
            "WHERE id = #{id} AND status IN ('DRAFT', 'APPROVED', 'POSTED') AND deleted = 0")
    int casVoid(@Param("id") Long id);
}
