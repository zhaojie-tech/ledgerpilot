package com.ledgerpilot.nlp.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量解析响应 DTO
 *
 * 每条业务独立状态：成功/失败/超时
 * 部分失败不影响其他条，前端可针对失败条单独重试
 */
@Data
public class BatchParseResponse {

    /** 批次ID，用于追踪 */
    private String batchId;

    /** 总条数 */
    private int total;

    /** 成功条数 */
    private int successCount;

    /** 失败条数 */
    private int failCount;

    /** 每条解析结果 */
    private List<BatchItem> items;

    /**
     * 单条批量解析结果
     */
    @Data
    public static class BatchItem {

        /** 序号（从1开始） */
        private int index;

        /** 原始输入 */
        private String originalInput;

        /** 状态：SUCCESS / ERROR / TIMEOUT */
        private String status;

        /** 错误信息（失败/超时时有值） */
        private String errorMsg;

        /** AI 解析结果（成功时有值，结构同 AIParseResponse） */
        private Object result;
    }
}
