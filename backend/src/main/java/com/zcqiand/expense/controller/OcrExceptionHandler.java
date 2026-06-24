package com.zcqiand.expense.controller;

import com.zcqiand.expense.dto.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MissingServletRequestParameterException;

/**
 * OCR/票据上传的局部异常处理。
 *
 * 不动既有的 GlobalExceptionHandler（005 在用，只增不改原则），把 multipart
 * 上传相关的参数缺失异常单独映射成 400 + ApiResponse.error，使
 * OcrReceiptController 在缺 file 参数时返回规整的契约响应而非全局兜底的 500。
 *
 * 仅声明关心的两个异常类型——其它异常仍由 GlobalExceptionHandler 接管。
 */
@RestControllerAdvice(assignableTypes = OcrReceiptController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OcrExceptionHandler {

    @ExceptionHandler({MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadUpload(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_FAILED",
                        "上传参数无效: " + ex.getMessage()));
    }
}
