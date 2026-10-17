package com.ledgerpilot.nlp.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * NLP解析请求
 */
@Data
public class ParseRequest {

    /**
     * 用户自然语言输入
     * 示例： "12月5日，收到客户A货款10000元，银行转账"
     */
    @NotBlank(message = "用户输入不能为空")
    private String userInput;

    /**
     * 期望的业务类型（可选）
     * 例：PURCHASE, SALE, EXPENSE
     */
    private String bizType;

    /**
     * 请求ID（用于追踪，可选）
     */
    private String requestId;

    /**
     * 是否直接创建凭证（true=解析后自动创建凭证）
     */
    private Boolean autoCreateVoucher = false;
}
