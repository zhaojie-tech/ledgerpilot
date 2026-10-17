package com.ledgerpilot.voucher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ledgerpilot.voucher.entity.AccVoucherEntry;
import org.apache.ibatis.annotations.Mapper;

/**
 * 凭证分录Mapper
 */
@Mapper
public interface AccVoucherEntryMapper extends BaseMapper<AccVoucherEntry> {
}
