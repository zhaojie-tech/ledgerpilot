package com.ledgerpilot.report.controller;

import com.ledgerpilot.common.core.R;
import com.ledgerpilot.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 财务报表接口
 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/profit")
    public R<?> profit(
            @RequestParam Integer year,
            @RequestParam Integer period,
            @RequestParam(required = false) @DateTimeFormat LocalDate asOfDate) {
        return R.ok(reportService.getProfitStatement(year, period));
    }

    @GetMapping("/balance")
    public R<?> balance(
            @RequestParam Integer year,
            @RequestParam Integer period,
            @RequestParam(required = false) @DateTimeFormat LocalDate asOfDate) {
        return R.ok(reportService.getBalanceSheet(year, period));
    }

    @GetMapping("/cashflow")
    public R<?> cashflow(
            @RequestParam Integer year,
            @RequestParam Integer period) {
        return R.ok(reportService.getCashFlowStatement(year, period));
    }

    @GetMapping("/subject-balance")
    public R<?> subjectBalance(
            @RequestParam Integer year,
            @RequestParam Integer period) {
        return R.ok(reportService.getSubjectBalanceReport(year, period));
    }

    @GetMapping("/export/{type}")
    public R<?> exportReport(
            @PathVariable String type,
            @RequestParam Integer year,
            @RequestParam Integer period) {
        String fileUrl = reportService.exportReport(type, year, period);
        return R.ok(fileUrl);
    }
}
