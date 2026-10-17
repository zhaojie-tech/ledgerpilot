package com.ledgerpilot.voucher.controller;

import com.ledgerpilot.common.core.R;
import com.ledgerpilot.voucher.dto.SubjectCreateRequest;
import com.ledgerpilot.voucher.dto.SubjectUpdateRequest;
import com.ledgerpilot.voucher.service.SubjectService;
import com.ledgerpilot.voucher.vo.SubjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subject")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping("/tree")
    public R<List<SubjectVO>> getTree() {
        return R.ok(subjectService.getTree());
    }

    @GetMapping("/{id}")
    public R<SubjectVO> getById(@PathVariable Long id) {
        return R.ok(subjectService.getById(id));
    }

    @PostMapping
    public R<SubjectVO> create(@Validated @RequestBody SubjectCreateRequest req) {
        return R.ok(subjectService.create(req));
    }

    @PutMapping
    public R<SubjectVO> update(@Validated @RequestBody SubjectUpdateRequest req) {
        return R.ok(subjectService.update(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return R.ok();
    }

    @PostMapping("/init")
    public R<Void> initFromTemplate(@RequestBody Map<String, String> body) {
        String systemCode = body.getOrDefault("systemCode", "SMALL_BUSINESS");
        subjectService.initFromTemplate(systemCode);
        return R.ok();
    }

    @PutMapping("/{id}/toggle")
    public R<Void> toggleEnabled(@PathVariable Long id) {
        subjectService.toggleEnabled(id);
        return R.ok();
    }
}
