package com.zcqiand.expense.exception;

/** 报销单不存在 → HTTP 404。 */
public class ExpenseNotFoundException extends BusinessException {
    public ExpenseNotFoundException(Long id) {
        super(404, "EXPENSE_NOT_FOUND", "报销单不存在: id=" + id);
    }
}
