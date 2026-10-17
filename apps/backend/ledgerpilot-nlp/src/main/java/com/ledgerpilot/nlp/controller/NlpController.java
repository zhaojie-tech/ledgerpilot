package com.ledgerpilot.nlp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerpilot.common.calculator.dto.SalaryCalculateRequest;
import com.ledgerpilot.common.calculator.dto.SalaryCalculateResponse;
import com.ledgerpilot.common.calculator.service.SalaryCalculatorService;
import com.ledgerpilot.common.core.R;
import com.ledgerpilot.common.util.TenantContext;
import com.ledgerpilot.nlp.client.AIClient;
import com.ledgerpilot.nlp.client.AIClient.*;
import com.ledgerpilot.nlp.dto.BatchParseResponse;
import com.ledgerpilot.nlp.dto.ParseRequest;
import com.ledgerpilot.nlp.service.FileParseService;
import com.ledgerpilot.nlp.service.SalaryParamParser;
import com.ledgerpilot.voucher.dto.VoucherCreateRequest;
import com.ledgerpilot.common.exception.LedgerPilotException;
import com.ledgerpilot.voucher.entity.AccSubject;
import com.ledgerpilot.voucher.mapper.SubjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ledgerpilot.voucher.service.VoucherService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.*;

/**
 * NLP 智能做账接口
 */
@Slf4j
@RestController
@RequestMapping("/api/nlp")
@RequiredArgsConstructor
public class NlpController {

    private final AIClient aiClient;
    private final VoucherService voucherService;
    private final FileParseService fileParseService;
    private final ObjectMapper objectMapper;
    private final SalaryCalculatorService salaryCalculatorService;
    private final SubjectMapper subjectMapper;

    /** 工资凭证科目（可配置，默认对齐内置 seed 口径：6603=管理费用；非常规编号已在 README 声明） */
    @org.springframework.beans.factory.annotation.Value("${ledgerpilot.salary.expense-subject-code:6603}")
    private String salaryExpenseSubjectCode;
    @org.springframework.beans.factory.annotation.Value("${ledgerpilot.salary.expense-subject-name:管理费用}")
    private String salaryExpenseSubjectName;

    /**
     * 批量解析线程池：有界队列 + 饱和拒绝（防 AI 服务变慢时请求无限堆积拖垮服务）。
     * 饱和行为：直接拒绝并返回业务错误，由前端稍后重试。
     */
    private final ThreadPoolExecutor batchExecutor = new ThreadPoolExecutor(
            4, 4, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20),
            runnable -> {
                Thread t = new Thread(runnable, "nlp-batch-worker");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());

    /** 文件大小限制：5MB */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    /** 单次批量最大条数 */
    private static final int MAX_BATCH_SIZE = 20;
    /** 文件提取业务文本上限 */
    private static final int MAX_FILE_TEXTS = 100;
    /** 单条解析超时（秒） */
    private static final int PER_ITEM_TIMEOUT_SEC = 15;
    /** 整体批量超时（秒） */
    private static final int TOTAL_BATCH_TIMEOUT_SEC = 120;

    /**
     * 连通性测试 — 验证 Java ↔ Python 通信
     */
    @GetMapping("/ping")
    public R<?> ping() {
        boolean healthy = aiClient.isHealthy();
        if (healthy) {
            return R.ok("Java ↔ Python 通信正常");
        }
        return R.fail("AI 服务不可用，请检查 Python 服务是否启动");
    }

    /**
     * AI 服务健康信息
     */
    @GetMapping("/health")
    public R<?> health() {
        HealthInfo info = aiClient.getHealthInfo();
        return R.ok(info);
    }

    /**
     * 文件上传解析 — 从文件中提取业务文本列表
     *
     * 安全措施：文件大小 5MB 限制、类型白名单、条数上限 100
     */
    @PostMapping("/parse-file")
    public R<?> parseFile(@RequestParam("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        log.info("文件上传解析: filename={}, size={}", filename, file.getSize());

        // 安全校验：文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            return R.fail("文件大小超过限制（最大5MB），请拆分后上传");
        }
        if (file.isEmpty()) {
            return R.fail("文件内容为空");
        }

        // 安全校验：文件类型白名单
        String ext = getExtension(filename).toLowerCase();
        Set<String> allowedExts = new HashSet<>(Arrays.asList("txt", "md", "csv", "tsv"));
        if (!allowedExts.contains(ext)) {
            return R.fail("不支持的文件格式（." + ext + "），请使用 TXT、MD、CSV 格式");
        }

        try {
            List<String> texts = fileParseService.extractBusinessTexts(filename, file.getBytes());

            if (texts.isEmpty()) {
                return R.fail("文件中未提取到有效业务描述");
            }

            // 条数上限保护
            if (texts.size() > MAX_FILE_TEXTS) {
                log.warn("文件业务文本超过上限: 提取={}, 限制={}, 截断", texts.size(), MAX_FILE_TEXTS);
                texts = texts.subList(0, MAX_FILE_TEXTS);
            }

            // 构造返回结果
            List<Map<String, Object>> items = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                Map<String, Object> item = new HashMap<>();
                item.put("index", i + 1);
                item.put("text", texts.get(i));
                item.put("status", "pending");
                items.add(item);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("filename", filename);
            result.put("total", texts.size());
            result.put("items", items);
            return R.ok(result);
        } catch (LedgerPilotException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("文件解析异常: filename={}", filename, e);
            return R.fail("文件解析失败，请检查文件格式");
        }
    }

    /**
     * 批量文本解析
     *
     * - CompletableFuture 并行解析
     * - 每条独立超时 15 秒，整体 120 秒
     * - 部分失败策略：一条失败不影响其他条
     * - 生成 batchId 用于追踪
     */
    @PostMapping("/parse-batch")
    public R<BatchParseResponse> parseBatch(@RequestBody BatchParseApiRequest request) {
        if (request.getInputs() == null || request.getInputs().isEmpty()) {
            return R.fail("业务描述不能为空");
        }
        if (request.getInputs().size() > MAX_BATCH_SIZE) {
            return R.fail("单次最多解析" + MAX_BATCH_SIZE + "条业务");
        }

        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int total = request.getInputs().size();
        log.info("批量解析请求: batchId={}, 条数={}", batchId, total);

        // 构建 AI 请求列表
        List<AIParseRequest> aiRequests = new ArrayList<>();
        for (String input : request.getInputs()) {
            ParseRequest parseReq = new ParseRequest();
            parseReq.setUserInput(input);
            parseReq.setRequestId(batchId + "-" + (aiRequests.size() + 1));
            AIParseRequest aiRequest = buildAIRequest(parseReq);
            aiRequests.add(aiRequest);
        }

        // 并行解析（池饱和时逐条降级为 ERROR，不让拒绝异常冒泡成 500）
        List<CompletableFuture<BatchParseResponse.BatchItem>> futures = new ArrayList<>();
        for (int i = 0; i < aiRequests.size(); i++) {
            final int index = i;
            final AIParseRequest aiReq = aiRequests.get(i);
            final String originalInput = request.getInputs().get(i);

            BatchParseResponse.BatchItem rejected = new BatchParseResponse.BatchItem();
            rejected.setIndex(index + 1);
            rejected.setOriginalInput(originalInput);

            CompletableFuture<BatchParseResponse.BatchItem> future;
            try {
                final BatchParseResponse.BatchItem item = new BatchParseResponse.BatchItem();
                item.setIndex(index + 1);
                item.setOriginalInput(originalInput);
                future = CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            AIParseResponse aiResponse = aiClient.parse(aiReq);
                            if (aiResponse != null) {
                                item.setStatus("SUCCESS");
                                item.setResult(aiResponse);
                            } else {
                                item.setStatus("ERROR");
                                item.setErrorMsg("AI 服务返回空结果");
                            }
                        } catch (Exception e) {
                            String msg = e.getMessage();
                            item.setStatus(msg != null && msg.contains("timeout") ? "TIMEOUT" : "ERROR");
                            item.setErrorMsg(msg != null && msg.length() > 200 ? msg.substring(0, 200) : msg);
                            log.warn("批量解析第{}条失败: {}", index + 1, msg);
                        }
                        return item;
                    }, batchExecutor);
            } catch (RejectedExecutionException e) {
                log.warn("批量解析线程池饱和，第{}条拒绝: batchId={}", index + 1, batchId);
                rejected.setStatus("ERROR");
                rejected.setErrorMsg("服务繁忙，请稍后重试或减少批量条数");
                futures.add(CompletableFuture.completedFuture(rejected));
                continue;
            }

            futures.add(future);
        }

        // 等待所有结果（整体超时）
        List<BatchParseResponse.BatchItem> items = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(TOTAL_BATCH_TIMEOUT_SEC, TimeUnit.SECONDS);
            for (CompletableFuture<BatchParseResponse.BatchItem> f : futures) {
                items.add(f.get());
            }
        } catch (TimeoutException e) {
            log.error("批量解析整体超时: batchId={}, 已完成={}/{}", batchId, items.size(), total);
            // 收集已完成的结果
            for (CompletableFuture<BatchParseResponse.BatchItem> f : futures) {
                if (f.isDone()) {
                    try { items.add(f.get()); } catch (Exception ignored) {}
                } else {
                    BatchParseResponse.BatchItem timeoutItem = new BatchParseResponse.BatchItem();
                    timeoutItem.setIndex(items.size() + 1);
                    timeoutItem.setOriginalInput(request.getInputs().get(items.size()));
                    timeoutItem.setStatus("TIMEOUT");
                    timeoutItem.setErrorMsg("整体解析超时");
                    items.add(timeoutItem);
                }
            }
        } catch (Exception e) {
            log.error("批量解析异常: batchId={}", batchId, e);
            return R.fail("批量解析服务异常");
        }

        // 构建响应
        BatchParseResponse response = new BatchParseResponse();
        response.setBatchId(batchId);
        response.setTotal(items.size());
        int successCount = 0;
        int failCount = 0;
        for (BatchParseResponse.BatchItem item : items) {
            if ("SUCCESS".equals(item.getStatus())) {
                successCount++;
            } else {
                failCount++;
            }
        }
        response.setSuccessCount(successCount);
        response.setFailCount(failCount);
        response.setItems(items);

        log.info("批量解析完成: batchId={}, 成功={}, 失败={}", batchId, successCount, failCount);
        return R.ok(response);
    }

    // 【v0.1 下架】/parse-batch-stream（SSE 批量进度推送）已移除：
    // EventSource 无法携带 Authorization 头，三种替代路径各有不可接受的安全代价
    // （豁免=匿名触发 LLM 消费、query-token=凭证进访问日志、改 POST=前端协议破坏性变更）。
    // 计划以 POST + fetch 流或 WebSocket 回归（见 README 演进路线）。

    @PostMapping("/parse")
    public R<?> parse(@RequestBody ParseRequest request) {
        log.info("NLP 解析请求: userInput={}",
                request.getUserInput().length() > 50
                        ? request.getUserInput().substring(0, 50) + "..."
                        : request.getUserInput());

        AIClient.AIParseRequest aiRequest = buildAIRequest(request);
        AIClient.AIParseResponse aiResponse = aiClient.parse(aiRequest);
        if (aiResponse == null) {
            return R.fail("AI 服务暂时不可用，请稍后重试");
        }
        return R.ok(aiResponse);
    }

    /**
     * 从解析结果创建凭证
     * 一个业务条目（BizItem）创建一张凭证
     */
    @PostMapping("/create-voucher")
    public R<?> createVoucherFromParse(@RequestBody NlpCreateVoucherRequest request) {
        log.info("NLP 创建凭证请求: items={}", request.getItems() != null ? request.getItems().size() : 0);

        List<Long> voucherIds = new ArrayList<>();
        String voucherDate = request.getVoucherDate();

        for (NlpCreateVoucherRequest.BizItemVO item : request.getItems()) {
            VoucherCreateRequest voucherReq = new VoucherCreateRequest();
            voucherReq.setVoucherDate(voucherDate);
            voucherReq.setBusinessType(item.getBizType());
            voucherReq.setSummary(item.getSummary());
            voucherReq.setSourceType("NLP");

            List<VoucherCreateRequest.EntryDTO> entries = new ArrayList<>();
            for (NlpCreateVoucherRequest.EntryVO e : item.getEntries()) {
                VoucherCreateRequest.EntryDTO entryDTO = new VoucherCreateRequest.EntryDTO();
                entryDTO.setSubjectCode(e.getSubjectCode());
                entryDTO.setSubjectName(e.getSubjectName());
                entryDTO.setDirection(e.getDirection());
                entryDTO.setAmount(parseClientAmount(e.getAmount()));
                entryDTO.setSummary(e.getSummary());
                // 辅助核算 JSON
                if (e.getAux() != null && !e.getAux().isEmpty()) {
                    try {
                        entryDTO.setAuxJson(objectMapper.writeValueAsString(e.getAux()));
                    } catch (Exception ex) {
                        log.warn("辅助核算序列化失败: {}", ex.getMessage());
                    }
                }
                entries.add(entryDTO);
            }
            voucherReq.setEntries(entries);

            Long voucherId = voucherService.createVoucher(voucherReq);
            voucherIds.add(voucherId);
            log.info("创建凭证: voucherId={}, bizType={}, summary={}",
                    voucherId, item.getBizType(), item.getSummary());
        }

        return R.ok(voucherIds);
    }

    /**
     * 月末结转：汇总当月损益发生额，生成结转凭证并自动过账
     */
    @PostMapping("/month-end-transfer")
    public R<?> monthEndTransfer(@RequestBody MonthEndTransferRequest request) {
        log.info("月末结转请求: year={}, period={}, transferDate={}",
                request.getYear(), request.getPeriod(), request.getTransferDate());
        try {
            Long voucherId = voucherService.closePeriod(request.getYear(), request.getPeriod(), request.getTransferDate());
            return R.ok(voucherId);
        } catch (LedgerPilotException e) {
            log.warn("月末结转失败: {}", e.getMessage());
            return R.fail(e.getMessage());
        }
    }

    /**
     * 一键解析并创建凭证
     *
     * 智能路由：
     * - 工资/社保/公积金类描述 → SalaryCalculatorService 计算精确金额，LLM 只生成摘要
     * - 其他类型 → 标准 AI 解析流程
     */
    @PostMapping("/parse-and-create")
    public R<?> parseAndCreate(@RequestBody ParseRequest request) {
        String userInput = request.getUserInput();
        log.info("NLP 一键创建: userInput={}", userInput.length() > 60 ? userInput.substring(0, 60) + "..." : userInput);

        // ── 工资类业务：走确定性计算引擎，LLM 只负责摘要 ──
        boolean salaryDetected = isSalaryRelatedInput(userInput);
        log.info("工资业务检测: {} -> {}", salaryDetected, userInput.length() > 50 ? userInput.substring(0, 50) + "..." : userInput);
        if (salaryDetected) {
            R<?> result = handleSalaryVoucherCreation(request, userInput);
            log.info("工资凭证生成结果: code={}, voucherIds={}",
                ((com.ledgerpilot.common.core.R<?>)result).getCode(),
                ((com.ledgerpilot.common.core.R<?>)result).getData());
            return result;
        }

        // ── 通用业务：标准 AI 解析流程 ──
        AIClient.AIParseRequest aiRequest = buildAIRequest(request);
        AIClient.AIParseResponse aiResponse = aiClient.parse(aiRequest);
        if (aiResponse == null) {
            return R.fail("AI 服务暂时不可用，请稍后重试");
        }

        if (aiResponse.getValidation() != null && !aiResponse.getValidation().isValid()) {
            return R.fail("解析结果校验未通过，请手动调整");
        }
        if (aiResponse.getItems() == null || aiResponse.getItems().isEmpty()) {
            return R.fail("AI 未能识别有效凭证");
        }

        String voucherDate = aiResponse.getVoucherDate() != null
                ? aiResponse.getVoucherDate()
                : java.time.LocalDate.now().toString();

        List<Long> voucherIds = new ArrayList<>();
        for (AIEntryItem item : aiResponse.getItems()) {
            VoucherCreateRequest voucherReq = new VoucherCreateRequest();
            voucherReq.setVoucherDate(voucherDate);
            voucherReq.setBusinessType(item.getBizType());
            voucherReq.setSummary(item.getSummary());
            voucherReq.setSourceType("NLP");

            List<VoucherCreateRequest.EntryDTO> entries = new ArrayList<>();
            if (item.getEntries() != null) {
                for (AIEntry e : item.getEntries()) {
                    VoucherCreateRequest.EntryDTO entryDTO = new VoucherCreateRequest.EntryDTO();
                    entryDTO.setSubjectCode(e.getSubjectCode());
                    entryDTO.setSubjectName(e.getSubjectName());
                    entryDTO.setDirection(e.getDirection());
                    // schema v2 先行步：BigDecimal 承接 + 归一（消灭 Double 浮点尾巴）
                    entryDTO.setAmount(e.getAmount().setScale(2, java.math.RoundingMode.HALF_UP));
                    entryDTO.setSummary(e.getSummary());
                    if (e.getAux() != null) {
                        try {
                            entryDTO.setAuxJson(objectMapper.writeValueAsString(e.getAux()));
                        } catch (Exception ex) {
                            log.warn("辅助核算序列化失败: {}", ex.getMessage());
                        }
                    }
                    entries.add(entryDTO);
                }
            }
            voucherReq.setEntries(entries);

            Long voucherId = voucherService.createVoucher(voucherReq);
            voucherIds.add(voucherId);
        }

        return R.ok(voucherIds);
    }

    /**
     * 工资类业务：确定性计算 + LLM 摘要生成
     *
     * 从用户输入中解析工资参数 → SalaryCalculatorService 计算精确金额 → 生成凭证
     * LLM 只负责根据输入上下文生成更人性化的凭证摘要
     */
    private R<?> handleSalaryVoucherCreation(ParseRequest request, String userInput) {
        log.info("工资类业务检测到，启动确定性计算流程 | input={}", userInput);

        // 1. 从用户输入中解析工资参数
        SalaryParamParser.Params params = parseSalaryParamsFromInput(userInput);
        log.info("工资参数解析结果: grossSalary={}, socialBase={}, period={}, empType={}",
                params.grossSalary, params.socialBase, params.period, params.employeeType);

        if (params.grossSalary == null || params.grossSalary <= 0) {
            log.warn("无法解析工资金额，回落到标准AI流程");
            return R.fail("无法从描述中识别工资金额，请明确写出工资金额，如：本月应发工资 30000 元");
        }

        // 2. 计算精确工资
        SalaryCalculateRequest calcReq = new SalaryCalculateRequest();
        calcReq.setTenantId(TenantContext.get());
        calcReq.setPeriod(params.period != null ? params.period : java.time.LocalDate.now().toString().substring(0, 7));
        calcReq.setGrossSalary(BigDecimal.valueOf(params.grossSalary));
        if (params.socialBase != null && params.socialBase > 0) {
            calcReq.setSocialBase(BigDecimal.valueOf(params.socialBase));
        }
        calcReq.setCalculateSocial(true);
        calcReq.setCalculateHousing(true);
        if (params.employeeType != null) {
            calcReq.setEmployeeType(params.employeeType);
        }
        // 专项附加扣除（默认 0）
        calcReq.setChildrenEduDeduction(BigDecimal.valueOf(params.childrenEduDeduction));
        calcReq.setContinuingEduDeduction(BigDecimal.valueOf(params.continuingEduDeduction));
        calcReq.setMedicalDeduction(BigDecimal.valueOf(params.seriousIllnessDeduction));
        calcReq.setHousingLoanDeduction(BigDecimal.valueOf(params.housingLoanDeduction));
        calcReq.setHousingRentDeduction(BigDecimal.valueOf(params.housingRentDeduction));
        calcReq.setElderlyCareDeduction(BigDecimal.valueOf(params.elderlyCareDeduction));

        SalaryCalculateResponse calcResult = salaryCalculatorService.calculate(calcReq);
        log.info("工资精确计算完成: 应发={}, 实发={}, 个税={}, 单位社保={}",
                calcResult.getGrossSalary(), calcResult.getNetSalary(),
                calcResult.getPersonalIncomeTax(), calcResult.getEmployerSocialTotal());

        // 3. 调用 LLM 生成更人性化的凭证摘要
        String aiSummary = generateSalarySummaryFromAI(userInput, calcResult);

        String voucherDate = params.voucherDate != null
                ? params.voucherDate
                : java.time.LocalDate.now().toString();

        List<Long> voucherIds = new ArrayList<>();

        // 4. 生成 3 张凭证
        // 凭证1：计提工资
        {
            VoucherCreateRequest vReq = new VoucherCreateRequest();
            vReq.setVoucherDate(voucherDate);
            vReq.setBusinessType("SALARY_ACCRUAL");
            vReq.setSummary(aiSummary + " — 计提工资");
            vReq.setSourceType("SALARY_CALC");

            List<VoucherCreateRequest.EntryDTO> entries = new ArrayList<>();

            VoucherCreateRequest.EntryDTO debit = new VoucherCreateRequest.EntryDTO();
            debit.setSubjectCode(salaryExpenseSubjectCode);
            debit.setSubjectName(salaryExpenseSubjectName);
            debit.setDirection("DEBIT");
            debit.setAmount(calcResult.getGrossSalary());
            debit.setSummary(aiSummary + " — 工资");
            entries.add(debit);

            VoucherCreateRequest.EntryDTO credit = new VoucherCreateRequest.EntryDTO();
            credit.setSubjectCode("2211");
            credit.setSubjectName("应付职工薪酬-工资");
            credit.setDirection("CREDIT");
            credit.setAmount(calcResult.getGrossSalary());
            credit.setSummary(aiSummary + " — 工资");
            entries.add(credit);

            vReq.setEntries(entries);
            Long vid = voucherService.createVoucher(vReq);
            voucherIds.add(vid);
            log.info("生成工资计提凭证: voucherId={}", vid);
        }

        // 凭证2：计提单位社保公积金
        if (calcResult.getEmployerSocialTotal().compareTo(BigDecimal.ZERO) > 0) {
            VoucherCreateRequest vReq = new VoucherCreateRequest();
            vReq.setVoucherDate(voucherDate);
            vReq.setBusinessType("SALARY_SOCIAL");
            vReq.setSummary(aiSummary + " — 计提社保公积金（单位部分）");
            vReq.setSourceType("SALARY_CALC");

            List<VoucherCreateRequest.EntryDTO> entries = new ArrayList<>();

            VoucherCreateRequest.EntryDTO debit = new VoucherCreateRequest.EntryDTO();
            debit.setSubjectCode(salaryExpenseSubjectCode);
            debit.setSubjectName(salaryExpenseSubjectName);
            debit.setDirection("DEBIT");
            debit.setAmount(calcResult.getEmployerSocialTotal());
            debit.setSummary("社保公积金单位部分");
            entries.add(debit);

            VoucherCreateRequest.EntryDTO credit = new VoucherCreateRequest.EntryDTO();
            credit.setSubjectCode("2211");
            credit.setSubjectName("应付职工薪酬-社保");
            credit.setDirection("CREDIT");
            credit.setAmount(calcResult.getEmployerSocialTotal());
            credit.setSummary("社保公积金单位部分");
            entries.add(credit);

            vReq.setEntries(entries);
            Long vid = voucherService.createVoucher(vReq);
            voucherIds.add(vid);
            log.info("生成社保计提凭证: voucherId={}", vid);
        }

        // 凭证3：发放工资
        {
            VoucherCreateRequest vReq = new VoucherCreateRequest();
            vReq.setVoucherDate(voucherDate);
            vReq.setBusinessType("SALARY_PAY");
            vReq.setSummary(aiSummary + " — 发放工资");
            vReq.setSourceType("SALARY_CALC");

            List<VoucherCreateRequest.EntryDTO> entries = new ArrayList<>();

            // 借：应付职工薪酬-工资 [应发工资]
            VoucherCreateRequest.EntryDTO debit = new VoucherCreateRequest.EntryDTO();
            debit.setSubjectCode("2211");
            debit.setSubjectName("应付职工薪酬-工资");
            debit.setDirection("DEBIT");
            debit.setAmount(calcResult.getGrossSalary());
            debit.setSummary(aiSummary + " — 工资");
            entries.add(debit);

            // 贷：银行存款 [实发工资]
            VoucherCreateRequest.EntryDTO creditBank = new VoucherCreateRequest.EntryDTO();
            creditBank.setSubjectCode("1002");
            creditBank.setSubjectName("银行存款");
            creditBank.setDirection("CREDIT");
            creditBank.setAmount(calcResult.getNetSalary());
            creditBank.setSummary("实发工资金额");
            entries.add(creditBank);

            // 贷：代扣个人所得税
            if (calcResult.getPersonalIncomeTax().compareTo(BigDecimal.ZERO) > 0) {
                VoucherCreateRequest.EntryDTO creditTax = new VoucherCreateRequest.EntryDTO();
                creditTax.setSubjectCode("2221");
                creditTax.setSubjectName("应交税费");
                creditTax.setDirection("CREDIT");
                creditTax.setAmount(calcResult.getPersonalIncomeTax());
                creditTax.setSummary("代扣个人所得税");
                entries.add(creditTax);
            }

            // 贷：代扣社保公积金个人部分
            if (calcResult.getPersonalSocialTotal().compareTo(BigDecimal.ZERO) > 0) {
                VoucherCreateRequest.EntryDTO creditSocial = new VoucherCreateRequest.EntryDTO();
                creditSocial.setSubjectCode("2241");
                creditSocial.setSubjectName("其他应付款-社保公积金");
                creditSocial.setDirection("CREDIT");
                creditSocial.setAmount(calcResult.getPersonalSocialTotal());
                creditSocial.setSummary("代扣社保公积金个人部分");
                entries.add(creditSocial);
            }

            vReq.setEntries(entries);
            Long vid = voucherService.createVoucher(vReq);
            voucherIds.add(vid);
            log.info("生成工资发放凭证: voucherId={}", vid);
        }

        log.info("工资凭证批量生成完成: 共{}张, voucherIds={}", voucherIds.size(), voucherIds);
        return R.ok(voucherIds);
    }

    /**
     * 判断用户输入是否属于工资/社保/公积金类业务
     */
    private boolean isSalaryRelatedInput(String input) {
        if (input == null || input.trim().isEmpty()) return false;
        String lower = input.toLowerCase();
        // 工资类关键词
        String[] salaryKeywords = {
                "工资", "发工资", "发薪", "月薪", "年薪", "税前", "税后",
                "实发", "应发", "工资条", "薪资",
                "社保", "养老保险", "医疗保险", "失业保险", "工伤", "生育",
                "公积金", "住房公积金", "社保公积金",
                "个税", "个人所得税", "代扣", "专项附加"
        };
        for (String kw : salaryKeywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    /** 前端字符串金额 → BigDecimal（schema v2 先行步；格式错给业务错误而非 500） */
    private java.math.BigDecimal parseClientAmount(String raw) {
        try {
            return new java.math.BigDecimal(raw).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new LedgerPilotException("AMOUNT_INVALID", "金额格式不合法: " + raw);
        }
    }

    /**
     * 从用户输入中解析工资计算参数（实现见 SalaryParamParser，量词紧邻判定）
     */
    private SalaryParamParser.Params parseSalaryParamsFromInput(String input) {
        return SalaryParamParser.parse(input);
    }

    /**
     * 调用 LLM 生成工资凭证摘要
     *
     * 使用轻量级提示，让 LLM 根据输入上下文生成更人性化的描述
     * 而非使用计算引擎的原始金额
     */
    private String generateSalarySummaryFromAI(String userInput, SalaryCalculateResponse calcResult) {
        try {
            // 构造一个最小化的 AI 请求，只生成摘要
            AIClient.AIParseRequest aiRequest = new AIClient.AIParseRequest();
            Long tid = TenantContext.get();
            aiRequest.setTenantId(tid != null ? tid.intValue() : null);
            aiRequest.setRequestId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));

            // 构造摘要生成提示
            String summaryPrompt = String.format(
                "根据以下业务描述，生成一句简洁的凭证摘要（15字以内）：\n%s",
                userInput
            );

            aiRequest.setUserInput(summaryPrompt);
            // 传入计算结果供 LLM 参考（但不直接用 LLM 的估算金额）
            aiRequest.setContext(new AIClient.AIParseRequest.SubjectContext());

            AIClient.AIParseResponse aiResponse = aiClient.parse(aiRequest);
            if (aiResponse != null && aiResponse.getItems() != null && !aiResponse.getItems().isEmpty()) {
                String aiSummary = aiResponse.getItems().get(0).getSummary();
                if (aiSummary != null && !aiSummary.trim().isEmpty()) {
                    // 截断到合理长度
                    return aiSummary.trim().length() > 20
                        ? aiSummary.trim().substring(0, 20)
                        : aiSummary.trim();
                }
            }
        } catch (Exception e) {
            log.warn("LLM 摘要生成失败，使用默认摘要: {}", e.getMessage());
        }
        // 回退：使用基于期间的默认摘要
        String period = java.time.LocalDate.now().toString().substring(0, 7);
        return period + "月工资";
    }

    /**
     * 工资计算接口 — 计算精确工资（不走 LLM 估算）
     *
     * 前端先调此接口预览工资明细，确认后再调用 /salary-create-voucher 生成凭证
     */
    @PostMapping("/salary-compute")
    public R<SalaryComputeResponse> salaryCompute(@RequestBody SalaryComputeRequest request) {
        if (request.getGrossSalary() == null || request.getGrossSalary().compareTo(BigDecimal.ZERO) <= 0) {
            return R.fail("应发工资 grossSalary 不能为空且必须大于 0");
        }
        if (request.getPeriod() == null || request.getPeriod().trim().isEmpty()) {
            return R.fail("工资所属期 period 不能为空，格式：yyyy-MM");
        }

        SalaryCalculateRequest calcReq = new SalaryCalculateRequest();
        calcReq.setTenantId(TenantContext.get());
        calcReq.setPeriod(request.getPeriod());
        calcReq.setGrossSalary(request.getGrossSalary());
        if (request.getSocialBase() != null) {
            calcReq.setSocialBase(request.getSocialBase());
        }
        calcReq.setCalculateSocial(request.getCalculateSocial() != null ? request.getCalculateSocial() : true);
        calcReq.setCalculateHousing(request.getCalculateHousing() != null ? request.getCalculateHousing() : true);
        if (request.getEmployeeType() != null) {
            calcReq.setEmployeeType(request.getEmployeeType());
        }
        // 专项附加扣除
        calcReq.setChildrenEduDeduction(request.getChildrenEduDeduction());
        calcReq.setContinuingEduDeduction(request.getContinuingEduDeduction());
        // 大病医疗（年总额，接口按月摊销）
        calcReq.setMedicalDeduction(request.getSeriousIllnessDeduction());
        calcReq.setHousingLoanDeduction(request.getHousingLoanDeduction());
        calcReq.setHousingRentDeduction(request.getHousingRentDeduction());
        calcReq.setElderlyCareDeduction(request.getElderlyCareDeduction());

        SalaryCalculateResponse result = salaryCalculatorService.calculate(calcReq);

        SalaryComputeResponse resp = new SalaryComputeResponse();
        resp.setPeriod(request.getPeriod());
        resp.setGrossSalary(result.getGrossSalary());
        resp.setNetSalary(result.getNetSalary());
        resp.setPersonalDeductionTotal(result.getPersonalDeductionTotal());
        resp.setEmployerSocialTotal(result.getEmployerSocialTotal());
        resp.setPersonalIncomeTax(result.getPersonalIncomeTax());
        resp.setSocialBase(result.getSocialBase());
        resp.setRemark(result.getRemark());
        resp.setItems(result.getItems());
        return R.ok(resp);
    }

    /**
     * 工资凭证生成接口 — 先计算精确工资，再生成凭证
     *
     * 生成两类凭证：
     *   凭证1：计提工资（管理费用 → 应付职工薪酬）
     *   凭证2：发放工资（应付职工薪酬 → 银行存款/其他应付款/个税）
     *
     * 会计科目（按常用设置，可通过配置调整）：
     *   2211 应付职工薪酬-工资
     *   6603 管理费用-工资
     *   6603 管理费用-社保（单位）
     *   1002 银行存款
     *   2241 其他应付款-社保公积金（个人）
     *   2221-01 应交增值税-个人所得税
     */
    @PostMapping("/salary-create-voucher")
    public R<List<Long>> salaryCreateVoucher(@RequestBody SalaryComputeRequest request) {
        if (request.getGrossSalary() == null || request.getGrossSalary().compareTo(BigDecimal.ZERO) <= 0) {
            return R.fail("应发工资不能为空");
        }
        if (request.getPeriod() == null || request.getPeriod().trim().isEmpty()) {
            return R.fail("工资所属期不能为空");
        }

        Long tenantId = TenantContext.get();
        log.info("工资生成凭证: period={}, grossSalary={}, tenantId={}",
                request.getPeriod(), request.getGrossSalary(), tenantId);

        // 1. 计算精确工资
        SalaryCalculateRequest calcReq = new SalaryCalculateRequest();
        calcReq.setTenantId(tenantId);
        calcReq.setPeriod(request.getPeriod());
        calcReq.setGrossSalary(request.getGrossSalary());
        if (request.getSocialBase() != null) {
            calcReq.setSocialBase(request.getSocialBase());
        }
        calcReq.setCalculateSocial(request.getCalculateSocial() != null ? request.getCalculateSocial() : true);
        calcReq.setCalculateHousing(request.getCalculateHousing() != null ? request.getCalculateHousing() : true);
        if (request.getEmployeeType() != null) {
            calcReq.setEmployeeType(request.getEmployeeType());
        }
        calcReq.setChildrenEduDeduction(request.getChildrenEduDeduction());
        calcReq.setContinuingEduDeduction(request.getContinuingEduDeduction());
        calcReq.setMedicalDeduction(request.getSeriousIllnessDeduction());
        calcReq.setHousingLoanDeduction(request.getHousingLoanDeduction());
        calcReq.setHousingRentDeduction(request.getHousingRentDeduction());
        calcReq.setElderlyCareDeduction(request.getElderlyCareDeduction());

        SalaryCalculateResponse calcResult = salaryCalculatorService.calculate(calcReq);

        String voucherDate = request.getVoucherDate() != null
                ? request.getVoucherDate()
                : java.time.LocalDate.now().toString();

        List<Long> voucherIds = new ArrayList<>();

        // 2. 凭证1：计提工资
        //   借：管理费用-工资 (6603)    [应发工资]
        //   贷：应付职工薪酬-工资 (2211)[应发工资]
        {
            VoucherCreateRequest vReq = new VoucherCreateRequest();
            vReq.setVoucherDate(voucherDate);
            vReq.setBusinessType("SALARY_ACCRUAL");
            vReq.setSummary("计提" + request.getPeriod() + "月工资");
            vReq.setSourceType("SALARY_CALC");
            vReq.setEntries(new ArrayList<>());

            VoucherCreateRequest.EntryDTO debit1 = new VoucherCreateRequest.EntryDTO();
            debit1.setSubjectCode(salaryExpenseSubjectCode);
            debit1.setSubjectName(salaryExpenseSubjectName);
            debit1.setDirection("DEBIT");
            debit1.setAmount(calcResult.getGrossSalary());
            debit1.setSummary("计提" + request.getPeriod() + "月工资");
            vReq.getEntries().add(debit1);

            VoucherCreateRequest.EntryDTO credit1 = new VoucherCreateRequest.EntryDTO();
            credit1.setSubjectCode("2211");
            credit1.setSubjectName("应付职工薪酬-工资");
            credit1.setDirection("CREDIT");
            credit1.setAmount(calcResult.getGrossSalary());
            credit1.setSummary("计提" + request.getPeriod() + "月工资");
            vReq.getEntries().add(credit1);

            Long vid = voucherService.createVoucher(vReq);
            voucherIds.add(vid);
            log.info("生成工资计提凭证: voucherId={}, 金额={}", vid, calcResult.getGrossSalary());
        }

        // 3. 凭证2：计提单位社保公积金（如果有）
        if (calcResult.getEmployerSocialTotal().compareTo(BigDecimal.ZERO) > 0) {
            VoucherCreateRequest vReq = new VoucherCreateRequest();
            vReq.setVoucherDate(voucherDate);
            vReq.setBusinessType("SALARY_SOCIAL");
            vReq.setSummary("计提" + request.getPeriod() + "月社保公积金（单位部分）");
            vReq.setSourceType("SALARY_CALC");
            vReq.setEntries(new ArrayList<>());

            VoucherCreateRequest.EntryDTO debit2 = new VoucherCreateRequest.EntryDTO();
            debit2.setSubjectCode(salaryExpenseSubjectCode);
            debit2.setSubjectName(salaryExpenseSubjectName);
            debit2.setDirection("DEBIT");
            debit2.setAmount(calcResult.getEmployerSocialTotal());
            debit2.setSummary("社保公积金单位部分");
            vReq.getEntries().add(debit2);

            VoucherCreateRequest.EntryDTO credit2 = new VoucherCreateRequest.EntryDTO();
            credit2.setSubjectCode("2211");
            credit2.setSubjectName("应付职工薪酬-社保");
            credit2.setDirection("CREDIT");
            credit2.setAmount(calcResult.getEmployerSocialTotal());
            credit2.setSummary("社保公积金单位部分");
            vReq.getEntries().add(credit2);

            Long vid = voucherService.createVoucher(vReq);
            voucherIds.add(vid);
            log.info("生成社保计提凭证: voucherId={}, 金额={}", vid, calcResult.getEmployerSocialTotal());
        }

        // 4. 凭证3：发放工资（实发薪资转账）
        //   借：应付职工薪酬-工资 (2211) [应发工资]
        //   贷：银行存款 (1002)           [实发工资]
        //   贷：其他应付款-个税 (2221-01) [代扣个税]
        //   贷：其他应付款-社保 (2241)    [代扣社保公积金个人部分]
        {
            VoucherCreateRequest vReq = new VoucherCreateRequest();
            vReq.setVoucherDate(voucherDate);
            vReq.setBusinessType("SALARY_PAY");
            vReq.setSummary("发放" + request.getPeriod() + "月工资");
            vReq.setSourceType("SALARY_CALC");
            vReq.setEntries(new ArrayList<>());

            // 借：应付职工薪酬-工资 [应发工资]
            VoucherCreateRequest.EntryDTO debit3 = new VoucherCreateRequest.EntryDTO();
            debit3.setSubjectCode("2211");
            debit3.setSubjectName("应付职工薪酬-工资");
            debit3.setDirection("DEBIT");
            debit3.setAmount(calcResult.getGrossSalary());
            debit3.setSummary("发放" + request.getPeriod() + "月工资");
            vReq.getEntries().add(debit3);

            // 贷：银行存款 [实发工资]
            VoucherCreateRequest.EntryDTO credit3a = new VoucherCreateRequest.EntryDTO();
            credit3a.setSubjectCode("1002");
            credit3a.setSubjectName("银行存款");
            credit3a.setDirection("CREDIT");
            credit3a.setAmount(calcResult.getNetSalary());
            credit3a.setSummary("实发工资金额");
            vReq.getEntries().add(credit3a);

            // 贷：代扣个人所得税
            if (calcResult.getPersonalIncomeTax().compareTo(BigDecimal.ZERO) > 0) {
                VoucherCreateRequest.EntryDTO credit3b = new VoucherCreateRequest.EntryDTO();
                credit3b.setSubjectCode("2221");
                credit3b.setSubjectName("应交税费");
                credit3b.setDirection("CREDIT");
                credit3b.setAmount(calcResult.getPersonalIncomeTax());
                credit3b.setSummary("代扣个人所得税");
                vReq.getEntries().add(credit3b);
            }

            // 贷：代扣社保公积金个人部分
            if (calcResult.getPersonalSocialTotal().compareTo(BigDecimal.ZERO) > 0) {
                VoucherCreateRequest.EntryDTO credit3c = new VoucherCreateRequest.EntryDTO();
                credit3c.setSubjectCode("2241");
                credit3c.setSubjectName("其他应付款-社保公积金");
                credit3c.setDirection("CREDIT");
                credit3c.setAmount(calcResult.getPersonalSocialTotal());
                credit3c.setSummary("代扣社保公积金个人部分");
                vReq.getEntries().add(credit3c);
            }

            Long vid = voucherService.createVoucher(vReq);
            voucherIds.add(vid);
            log.info("生成工资发放凭证: voucherId={}, 实发={}, 个税={}, 社保={}",
                    vid, calcResult.getNetSalary(),
                    calcResult.getPersonalIncomeTax(),
                    calcResult.getPersonalSocialTotal());
        }

        return R.ok(voucherIds);
    }

    /**
     * 构建 Python AI 请求
     */
    private AIClient.AIParseRequest buildAIRequest(ParseRequest request) {
        AIClient.AIParseRequest aiRequest = new AIClient.AIParseRequest();
        aiRequest.setUserInput(request.getUserInput());
        Long tid = TenantContext.get();
        aiRequest.setTenantId(tid != null ? tid.intValue() : null);
        aiRequest.setRequestId(request.getRequestId());

        AIClient.AIParseRequest.SubjectContext ctx = new AIClient.AIParseRequest.SubjectContext();
        ctx.setSubjectList(loadTenantSubjects());
        ctx.setAuxDimensions(Arrays.asList("customer", "supplier", "department"));
        ctx.setTaxpayerType("SMALL_SCALE");
        ctx.setIndustry("TRADING");
        aiRequest.setContext(ctx);

        return aiRequest;
    }

    /** 内置样例科目 */
    private static final List<String> SAMPLE_SUBJECTS = Arrays.asList(
            "1001 库存现金", "1002 银行存款", "1122 应收账款",
            "2202 应付账款", "1405 库存商品", "1601 固定资产",
            "2211 应付职工薪酬", "2221 应交税费", "2241 其他应付款",
            "6001 主营业务收入", "6602 销售费用", "6603 管理费用");

    /**
     * NLP 科目白名单改读租户科目表：
     * 旧实现硬编码 12 条（含与科目表冲突的口径），AI 会按错误白名单写错账。
     * 现读 acc_subject（末级可用科目），为空时回退内置样例。
     */
    private List<String> loadTenantSubjects() {
        try {
            List<AccSubject> subjects = subjectMapper.selectList(new LambdaQueryWrapper<AccSubject>()
                    .eq(AccSubject::getDeleted, 0)
                    .eq(AccSubject::getIsDetail, 1)
                    .orderByAsc(AccSubject::getSubjectCode)
                    .last("LIMIT 100"));
            if (subjects != null && !subjects.isEmpty()) {
                List<String> result = new ArrayList<>();
                for (AccSubject sub : subjects) {
                    result.add(sub.getSubjectCode() + " " + sub.getSubjectName());
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("读取租户科目表失败，回退内置样例白名单: {}", e.getMessage());
        }
        return SAMPLE_SUBJECTS;
    }

    // ─── 请求 DTO ───

    @Data
    public static class NlpCreateVoucherRequest {
        private String voucherDate;
        private List<BizItemVO> items;

        @Data
        public static class BizItemVO {
            private String bizType;
            private String summary;
            private List<EntryVO> entries;
        }

        @Data
        public static class EntryVO {
            private String direction;
            private String subjectCode;
            private String subjectName;
            private String amount;
            private String summary;
            private Map<String, String> aux;
        }
    }

    @Data
    public static class MonthEndTransferRequest {
        private Integer year;
        private Integer period;
        private String transferDate;  // 月结日期（如 2025-03-31）
    }

    @Data
    public static class BatchParseApiRequest {
        private List<String> inputs;
        private String voucherDate;
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    // ─── 工资计算响应 DTO ───

    @Data
    public static class SalaryComputeResponse {
        private String period;
        private BigDecimal grossSalary;       // 应发工资
        private BigDecimal netSalary;         // 实发工资
        private BigDecimal personalDeductionTotal;  // 个人扣款合计
        private BigDecimal employerSocialTotal;      // 单位社保合计
        private BigDecimal personalIncomeTax;       // 代扣个税
        private BigDecimal socialBase;       // 社保基数
        private String remark;
        private List<SalaryCalculateResponse.SalaryItem> items; // 明细
    }
}
