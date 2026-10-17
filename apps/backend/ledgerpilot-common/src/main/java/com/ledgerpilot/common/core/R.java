package com.ledgerpilot.common.core;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应结果封装
 * 所有API接口返回此格式
 *
 * @param <T> data字段的数据类型
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码：0=成功，非0=失败 */
    private int code;

    /** 消息 */
    private String msg;

    /** 数据 */
    private T data;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(0);
        r.setMsg("操作成功");
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail(String code, String msg) {
        R<T> r = new R<>();
        // R 的 code 是数值位：所有失败统一 -1；字符串 code 仅保留给日志/调用方语义（原实现存在恒 -1 的死语句）
        r.setCode(-1);
        r.setMsg(msg);
        return r;
    }

    public static <T> R<T> fail(String msg) {
        return fail("ERROR", msg);
    }

    public boolean isSuccess() {
        return this.code == 0;
    }
}
