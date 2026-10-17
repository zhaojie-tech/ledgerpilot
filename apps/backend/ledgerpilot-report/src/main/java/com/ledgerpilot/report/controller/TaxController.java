package com.ledgerpilot.report.controller;

import com.ledgerpilot.common.core.R;
import com.ledgerpilot.report.service.TaxService;
import com.ledgerpilot.report.vo.TaxVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 税务中心 Controller
 */
@RestController
@RequestMapping("/api/tax")
public class TaxController {

    @Autowired
    private TaxService taxService;

    @GetMapping("/summary")
    public R<List<TaxVO>> getTaxSummary(
            @RequestParam Integer year,
            @RequestParam Integer period
    ) {
        List<TaxVO> result = taxService.getTaxSummary(year, period);
        return R.ok(result);
    }

    @GetMapping("/vat")
    public R<TaxVO> getVAT(
            @RequestParam Integer year,
            @RequestParam Integer period
    ) {
        TaxVO vat = taxService.getVAT(year, period);
        return R.ok(vat);
    }

    @GetMapping("/corporate-income-tax")
    public R<TaxVO> getCorporateIncomeTax(
            @RequestParam Integer year,
            @RequestParam Integer period
    ) {
        TaxVO cit = taxService.getCorporateIncomeTax(year, period);
        return R.ok(cit);
    }

    @GetMapping("/surtax")
    public R<TaxVO> getSurtax(
            @RequestParam Integer year,
            @RequestParam Integer period
    ) {
        TaxVO surtax = taxService.getSurtax(year, period);
        return R.ok(surtax);
    }

    @GetMapping("/periods")
    public R<List<Map<String, Object>>> getAvailablePeriods() {
        return R.ok(taxService.getAvailablePeriods());
    }
}
