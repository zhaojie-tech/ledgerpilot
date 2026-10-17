package com.ledgerpilot.nlp.service.impl;

import com.ledgerpilot.common.exception.LedgerPilotException;
import com.ledgerpilot.nlp.service.FileParseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文件解析服务实现
 *
 * 解析策略：
 * - txt/md/csv：按行读取，非空行即为一条业务
 * - csv：取第一列（逗号分隔），忽略空行和表头
 * - Excel/Word：暂不支持，引导用户转格式
 *
 * 后续扩展：
 * - xlsx/xls：引入 poi-ooxml，按行读取 A 列
 * - docx：引入 poi-ooxml，提取段落文本
 */
@Slf4j
@Service
public class FileParseServiceImpl implements FileParseService {

    /** CSV 分隔符 */
    private static final Pattern CSV_SPLIT = Pattern.compile(",");
    /** 提取业务文本上限（FileParseService MAX_TEXTS 的副本） */

    /** 文件大小限制 */
    private static final long MAX_FILE_SIZE_LOCAL = 5 * 1024 * 1024;
    /** 提取业务文本上限 */
    private static final int MAX_TEXTS_LOCAL = 100;

    /** 跳过的常见表头关键词 */
    private static final String[] HEADER_KEYWORDS = {
            "业务", "摘要", "描述", "内容", "说明", "序号", "编号", "Business"
    };

    @Override
    public List<String> extractBusinessTexts(String filename, byte[] content) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new LedgerPilotException("文件名不能为空");
        }
        if (content == null || content.length == 0) {
            throw new LedgerPilotException("文件内容为空");
        }

        // 文件大小校验
        if (content.length > MAX_FILE_SIZE_LOCAL) {
            throw new LedgerPilotException("文件大小超过限制（最大5MB），请拆分后上传");
        }

        String ext = getExtension(filename).toLowerCase();
        log.info("解析文件: filename={}, ext={}, size={}bytes", filename, ext, content.length);

        switch (ext) {
            case "txt":
            case "md":
                return extractFromPlainText(new String(content, detectCharset(content)));
            case "csv":
            case "tsv":
                return extractFromCsv(new String(content, detectCharset(content)), ext.equals("tsv") ? "\t" : ",");
            case "xlsx":
            case "xls":
                throw new LedgerPilotException("暂不支持 Excel 格式，请将内容复制到 TXT 或直接粘贴到对话框");
            case "docx":
            case "doc":
                throw new LedgerPilotException("暂不支持 Word 格式，请将内容复制到 TXT 或直接粘贴到对话框");
            default:
                // 尝试当纯文本处理
                log.warn("未知文件格式: {}，尝试按纯文本解析", ext);
                return extractFromPlainText(new String(content, detectCharset(content)));
        }
    }

    @Override
    public List<String> extractFromPlainText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        } catch (Exception e) {
            log.error("文本解析失败", e);
            throw new LedgerPilotException("文本解析失败: " + e.getMessage());
        }

        log.info("提取业务文本: 总行数={}, 有效业务={}", lines.size(), lines.size());
        return lines;
    }

    /**
     * CSV 解析：取第一列作为业务描述
     */
    private List<String> extractFromCsv(String text, String delimiter) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                // 取第一列
                String[] cols = trimmed.split(delimiter);
                String businessText = cols[0].trim()
                        .replaceAll("^\"|\"$", "");  // 去掉引号

                if (businessText.isEmpty()) continue;

                // 跳过表头
                if (isFirstLine && isHeader(businessText)) {
                    log.info("跳过CSV表头: {}", businessText);
                    isFirstLine = false;
                    continue;
                }

                isFirstLine = false;
                lines.add(businessText);
            }
        } catch (Exception e) {
            log.error("CSV解析失败", e);
            throw new LedgerPilotException("CSV解析失败: " + e.getMessage());
        }

        log.info("CSV提取业务文本: 有效业务={}", lines.size());
        return lines;
    }

    /**
     * 判断是否为表头行
     */
    private boolean isHeader(String text) {
        String lower = text.toLowerCase();
        for (String keyword : HEADER_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 简单字符集检测：优先 UTF-8，回退 GBK
     */
    private Charset detectCharset(byte[] content) {
        // UTF-8 BOM 检测
        if (content.length >= 3 && content[0] == (byte) 0xEF
                && content[1] == (byte) 0xBB && content[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        // 简单启发式：如果内容能被 UTF-8 完美解码，就用 UTF-8
        try {
            String decoded = new String(content, StandardCharsets.UTF_8);
            // 检查是否有乱码特征（替换字符）
            if (!decoded.contains("\uFFFD")) {
                return StandardCharsets.UTF_8;
            }
        } catch (Exception ignored) {
        }
        // 回退 GBK
        try {
            return Charset.forName("GBK");
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }
}
