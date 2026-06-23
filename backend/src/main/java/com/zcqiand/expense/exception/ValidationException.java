package com.zcqiand.expense.exception;

/**
 * 校验失败 → HTTP 400 (Bad Request)。
 *
 * 典型场景：
 * - 拒绝审批时 reason 为空（CLAUDE.md 业务规则强制要求）
 * - 申请人尝试审批自己的报销单
 * - 审批人级别与报销金额不匹配
 */
public class ValidationException extends BusinessException {
    public ValidationException(String message) {
        super(400, "VALIDATION_FAILED", message);
    }
}
