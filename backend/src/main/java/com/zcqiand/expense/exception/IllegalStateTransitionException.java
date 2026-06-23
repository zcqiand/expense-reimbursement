package com.zcqiand.expense.exception;

import com.zcqiand.expense.entity.ExpenseStatus;

/**
 * 状态机非法迁移 → HTTP 409 (Conflict)。
 *
 * 比如：试图把 DRAFT 直接 approve、把 PAID 再 submit、对已 REJECTED 的报销单
 * 再调 approve——都会落到这个异常。状态机的安全性主要靠 Service 层
 * switch + 这个异常，而不是依赖前端不调错误端点。
 */
public class IllegalStateTransitionException extends BusinessException {
    public IllegalStateTransitionException(ExpenseStatus from, String attemptedAction) {
        super(409, "ILLEGAL_STATE_TRANSITION",
                "状态 " + from + " 不允许执行动作: " + attemptedAction);
    }
}
