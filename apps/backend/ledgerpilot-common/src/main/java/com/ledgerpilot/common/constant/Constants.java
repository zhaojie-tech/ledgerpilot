package com.ledgerpilot.common.constant;

/**
 * 通用常量定义
 */
public class Constants {

    /** ========== 租户相关 ========== */

    /** 超级管理员租户ID（系统内置租户） */
    public static final Long SUPER_TENANT_ID = 1L;

    /** ========== 用户角色 ========== */

    /** 超级管理员 */
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";

    /** 租户管理员 */
    public static final String ROLE_TENANT_ADMIN = "TENANT_ADMIN";

    /** 普通用户 */
    public static final String ROLE_USER = "USER";

    /** ========== 凭证状态 ========== */

    /** 草稿 */
    public static final String VOUCHER_STATUS_DRAFT = "DRAFT";

    /** 已审核 */
    public static final String VOUCHER_STATUS_APPROVED = "APPROVED";

    /** 已过账 */
    public static final String VOUCHER_STATUS_POSTED = "POSTED";

    /** 已作废 */
    public static final String VOUCHER_STATUS_VOIDED = "VOIDED";

    /** ========== 凭证来源 ========== */

    /** 自然语言NLP生成 */
    public static final String SOURCE_NLP = "NLP";

    /** OCR识别生成 */
    public static final String SOURCE_OCR = "OCR";

    /** 手工录入 */
    public static final String SOURCE_MANUAL = "MANUAL";

    /** ========== 会计期间 ========== */

    /** 一年中的最大月数 */
    public static final int MAX_PERIOD = 12;

    /** ========== 金额精度 ========== */

    /** 金额精度：2位小数 */
    public static final int AMOUNT_SCALE = 2;

    /** 金额最大整数位 */
    public static final int AMOUNT_INTEGER_DIGITS = 16;

    /** ========== 套餐类型 ========== */

    /** 免费版 */
    public static final String PLAN_FREE = "FREE";

    /** 基础版 */
    public static final String PLAN_BASIC = "BASIC";

    /** 专业版 */
    public static final String PLAN_PRO = "PRO";

    /** ========== 纳税人类型 ========== */

    /** 一般纳税人 */
    public static final String TAXPAYER_GENERAL = "GENERAL";

    /** 小规模纳税人 */
    public static final String TAXPAYER_SMALL_SCALE = "SMALL_SCALE";

    /** ========== 科目余额方向 ========== */

    /** 借方 */
    public static final String DIRECTION_DEBIT = "DEBIT";

    /** 贷方 */
    public static final String DIRECTION_CREDIT = "CREDIT";

    /** ========== Redis Key前缀 ========== */

    public static final String REDIS_PREFIX_JWT_BLACKLIST = "jwt:blacklist:";

    public static final String REDIS_PREFIX_TENANT_CONFIG = "tenant:config:";

    public static final String REDIS_PREFIX_PROMPT_TEMPLATE = "prompt:template:";

    public static final String REDIS_PREFIX_LLM_CACHE = "llm:cache:";
}
