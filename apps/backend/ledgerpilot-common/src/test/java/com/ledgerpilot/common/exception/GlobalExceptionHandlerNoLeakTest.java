package com.ledgerpilot.common.exception;

import com.ledgerpilot.common.core.R;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** C7 回归锁：兜底异常不向客户端回显内部细节。 */
class GlobalExceptionHandlerNoLeakTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void genericExceptionDoesNotLeakMessage() {
        R<?> r = handler.handleException(new RuntimeException("数据库密码是 secret-password"));
        assertFalse(String.valueOf(r.getMsg()).contains("secret-password"), "不得回显内部异常消息");
        assertFalse(String.valueOf(r.getMsg()).contains("Exception"));
        assertEquals(-1, r.getCode());
    }

    @Test
    void httpStatusAnnotations() {
        assertEquals(HttpStatus.UNAUTHORIZED, UnauthorizedException.class.getAnnotation(
                org.springframework.web.bind.annotation.ResponseStatus.class).value());
        assertEquals(HttpStatus.FORBIDDEN, ForbiddenException.class.getAnnotation(
                org.springframework.web.bind.annotation.ResponseStatus.class).value());
    }

    @Test
    void ledgerpilotExceptionKeepsBusinessMessage() {
        R<?> r = handler.handleLedgerPilotException(new LedgerPilotException("LOGIN_FAILED", "用户名或密码错误"));
        assertEquals("用户名或密码错误", r.getMsg());
    }
}
