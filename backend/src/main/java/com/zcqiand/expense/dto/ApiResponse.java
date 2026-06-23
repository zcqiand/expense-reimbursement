package com.zcqiand.expense.dto;

/**
 * 全局 REST 响应包装。
 *
 * CLAUDE.md 强制规范：所有 REST 响应必须用 ApiResponse&lt;T&gt;{success, data, error}
 * 格式。这是一个 Java record——天然 immutable，自动 equals/hashCode/toString。
 *
 * 用法：
 *   ApiResponse.ok(data)           → {"success":true,"data":...,"error":null}
 *   ApiResponse.error("CODE","msg")→ {"success":false,"data":null,"error":{...}}
 */
public record ApiResponse<T>(boolean success, T data, ErrorPayload error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorPayload(code, message));
    }

    /** 错误负载：code 给程序判断、message 给人读。 */
    public record ErrorPayload(String code, String message) {
    }
}
