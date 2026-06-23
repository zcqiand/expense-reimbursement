package com.zcqiand.expense.exception;

/**
 * 业务异常基类。
 *
 * 所有 Service 层抛出的业务异常都继承自这个类，让 GlobalExceptionHandler
 * 能一次性 @ExceptionHandler(BusinessException.class) 兜底，子类通过
 * HttpStatus 字段告诉 Handler 应该返回什么状态码。
 *
 * 用 RuntimeException 而非 checked exception——Spring 生态约定，
 * checked 异常会污染所有 Repository / Service 方法签名。
 */
public abstract class BusinessException extends RuntimeException {

    private final int httpStatus;
    private final String code;

    protected BusinessException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }
}
