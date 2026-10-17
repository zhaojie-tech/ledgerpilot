package com.ledgerpilot.common.exception;

import lombok.Getter;

/**
 * 业务异常类
 * 用于携带业务错误码和错误信息的可控异常
 */
@Getter
public class LedgerPilotException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final String code;

    /** HTTP状态码（可选，默认400） */
    private final int httpStatus;

    public LedgerPilotException(String code, String message) {
        this(code, message, 400);
    }

    public LedgerPilotException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public LedgerPilotException(String message) {
        this("LEDGERPILOT_ERROR", message, 400);
    }

    public LedgerPilotException(String message, Throwable cause) {
        super(message, cause);
        this.code = "LEDGERPILOT_ERROR";
        this.httpStatus = 500;
    }
}
