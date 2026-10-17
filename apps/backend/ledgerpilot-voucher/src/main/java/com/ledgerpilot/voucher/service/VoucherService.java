package com.ledgerpilot.voucher.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ledgerpilot.voucher.dto.OpeningBalanceDTO;
import com.ledgerpilot.voucher.dto.OpeningBalanceVO;
import com.ledgerpilot.voucher.dto.VoucherCreateRequest;
import com.ledgerpilot.voucher.dto.VoucherDetailVO;
import com.ledgerpilot.voucher.dto.VoucherQueryRequest;
import com.ledgerpilot.voucher.entity.AccVoucher;

public interface VoucherService {

    /**
     * 分页查询凭证
     */
    Page<AccVoucher> pageVouchers(VoucherQueryRequest request);

    /**
     * 创建凭证
     * @return 凭证ID
     */
    Long createVoucher(VoucherCreateRequest request);

    /**
     * 获取凭证详情（含分录）
     */
    VoucherDetailVO getVoucherDetail(Long id);

    /**
     * 审核凭证
     */
    void approveVoucher(Long id);

    /**
     * 取消审核
     */
    void unapproveVoucher(Long id);

    /**
     * 过账凭证（更新科目余额）
     */
    void postVoucher(Long id);

    /**
     * 作废凭证
     */
    void voidVoucher(Long id);

    /**
     * 月末结转：将当月损益类科目结转至本年利润
     * @param year 年
     * @param period 月份
     * @param transferDate 月结日期（结转凭证的凭证日期）
     * @return 结转凭证ID
     */
    Long closePeriod(Integer year, Integer period, String transferDate);

    /**
     * 生成下一个凭证号
     */
    String generateNextVoucherNo(String voucherDate);

    /**
     * 获取期初余额（包含全部科目及当前余额状态）
     * @param year  会计年度（默认当前年）
     * @param period 会计月份（默认当前月）
     */
    OpeningBalanceVO getOpeningBalances(Integer year, Integer period);

    /**
     * 保存期初余额（开账 / 调整期初余额）
     * @param dto 期初余额数据
     */
    void saveOpeningBalances(OpeningBalanceDTO dto);
}
