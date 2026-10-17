package com.ledgerpilot.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 认证/鉴权失败（401）：token 缺失、无效或作用域不符。
 * 与 LedgerPilotException(400) 的区别：这是身份问题，不是请求参数问题。
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String code, String message) {
        super(message);
    }
}
