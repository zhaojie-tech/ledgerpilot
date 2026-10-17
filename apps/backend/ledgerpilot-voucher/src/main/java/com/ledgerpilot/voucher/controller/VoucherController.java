package com.ledgerpilot.voucher.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ledgerpilot.common.core.R;
import com.ledgerpilot.voucher.dto.OpeningBalanceDTO;
import com.ledgerpilot.voucher.dto.OpeningBalanceVO;
import com.ledgerpilot.voucher.dto.VoucherCreateRequest;
import com.ledgerpilot.voucher.dto.VoucherDetailVO;
import com.ledgerpilot.voucher.dto.VoucherQueryRequest;
import com.ledgerpilot.voucher.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 凭证管理接口
 * 包含凭证的创建、查询、审核、过账、作废等全生命周期操作
 */
@RestController
@RequestMapping("/api/voucher")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @GetMapping("/page")
    public R<Page> page(VoucherQueryRequest request) {
        return R.ok(voucherService.pageVouchers(request));
    }

    @PostMapping
    public R<Long> create(@RequestBody VoucherCreateRequest request) {
        Long voucherId = voucherService.createVoucher(request);
        return R.ok(voucherId);
    }

    @GetMapping("/{id}")
    public R<VoucherDetailVO> getById(@PathVariable Long id) {
        return R.ok(voucherService.getVoucherDetail(id));
    }

    @PostMapping("/{id}/approve")
    public R<?> approve(@PathVariable Long id) {
        voucherService.approveVoucher(id);
        return R.ok();
    }

    @PostMapping("/{id}/unapprove")
    public R<?> unapprove(@PathVariable Long id) {
        voucherService.unapproveVoucher(id);
        return R.ok();
    }

    @PostMapping("/{id}/post")
    public R<?> post(@PathVariable Long id) {
        voucherService.postVoucher(id);
        return R.ok();
    }

    @PostMapping("/{id}/void")
    public R<?> voidVoucher(@PathVariable Long id) {
        voucherService.voidVoucher(id);
        return R.ok();
    }

    @PostMapping("/close-period")
    public R<?> closePeriod(@RequestParam Integer year,
                            @RequestParam Integer period,
                            @RequestParam String transferDate) {
        Long voucherId = voucherService.closePeriod(year, period, transferDate);
        return R.ok(voucherId);
    }

    @GetMapping("/next-no")
    public R<String> nextVoucherNo(@RequestParam String voucherDate) {
        return R.ok(voucherService.generateNextVoucherNo(voucherDate));
    }

    /**
     * 获取期初余额（开账页面数据）
     * @param year  会计年度（默认当前年）
     * @param period 会计月份（默认当前月）
     */
    @GetMapping("/opening-balance")
    public R<OpeningBalanceVO> getOpeningBalance(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer period) {
        return R.ok(voucherService.getOpeningBalances(year, period));
    }

    /**
     * 保存期初余额（开账 / 调整期初余额）
     */
    @PostMapping("/opening-balance")
    public R<?> saveOpeningBalance(@RequestBody OpeningBalanceDTO dto) {
        voucherService.saveOpeningBalances(dto);
        return R.ok();
    }
}
