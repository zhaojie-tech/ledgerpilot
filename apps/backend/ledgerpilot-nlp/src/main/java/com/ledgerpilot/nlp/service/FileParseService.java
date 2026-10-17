package com.ledgerpilot.nlp.service;

import java.util.List;

/**
 * 文件解析服务 — 从上传文件中提取业务文本列表
 *
 * 支持格式：txt, md, csv
 * 后续扩展：xlsx, xls, docx（需引入 Apache POI / poi-ooxml）
 */
public interface FileParseService {

    /**
     * 从文件内容中提取业务文本列表
     *
     * @param filename 文件名（用于判断格式）
     * @param content  文件字节数组
     * @return 业务文本列表（每条一行，过滤空行）
     */
    List<String> extractBusinessTexts(String filename, byte[] content);

    /**
     * 从多行文本中提取业务列表（用于批量文本输入）
     *
     * @param text 多行文本，以换行符分隔
     * @return 业务文本列表
     */
    List<String> extractFromPlainText(String text);
}
