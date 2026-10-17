package com.ledgerpilot.common.exception;

import com.ledgerpilot.common.core.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局统一异常处理器
 * 统一捕获并响应所有异常，返回标准R格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(LedgerPilotException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleLedgerPilotException(LedgerPilotException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 认证/鉴权失败（401）：身份问题而非参数问题，不泄露细节
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<?> handleUnauthorized(UnauthorizedException e) {
        log.warn("鉴权失败: {}", e.getMessage());
        return R.fail(e.getMessage(), "认证失败或未授权");
    }

    /**
     * 已认证但无权限（403）
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<?> handleForbidden(ForbiddenException e) {
        log.warn("权限拒绝: {}", e.getMessage());
        return R.fail(e.getMessage(), "无权限执行该操作");
    }

    /**
     * 参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("参数校验异常: {}", msg);
        return R.fail("VALID_ERROR", msg);
    }

    /**
     * 参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleBindException(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("参数绑定异常: {}", msg);
        return R.fail("VALID_ERROR", msg);
    }

    /**
     * 约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("约束违反: {}", msg);
        return R.fail("VALID_ERROR", msg);
    }

    /**
     * 其他未捕获异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<?> handleException(Exception e) {
        // C7：内部异常细节只进日志，不回显给客户端（防信息泄露）
        log.error("系统异常", e);
        return R.fail("SYSTEM_ERROR", "系统异常，请稍后重试或联系管理员");
    }
}
