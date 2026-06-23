package com.zcqiand.expense.controller;

import com.zcqiand.expense.dto.ApiResponse;
import com.zcqiand.expense.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：把业务异常翻译成统一的 ApiResponse JSON 响应。
 *
 * 用 @RestControllerAdvice 而非每个 Controller 自己 try/catch——
 * 这样异常处理逻辑集中在一处，新增 Controller 不必重复样板。
 *
 * 注意 BusinessException 是基类——子类如 ExpenseNotFoundException 抛出时
 * 会被这个 handler 接住，并通过 getHttpStatus() 返回正确的状态码。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        // 业务异常按预期发生（如状态机非法迁移）——记 WARN 级别，便于运维筛
        log.warn("业务异常: code={} message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.valueOf(ex.getHttpStatus()))
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        // 兜底处理意外异常（NPE、SQLException 等）——记 ERROR + 完整堆栈
        log.error("意外异常", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "服务器内部错误"));
    }
}
