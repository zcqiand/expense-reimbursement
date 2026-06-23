package com.zcqiand.expense.entity;

/**
 * 单次审批决策。
 *
 * REJECTED 必须带 reason——Service 层强制校验，DB 层 CHECK 约束兜底。
 */
public enum ApprovalDecision {
    APPROVED,
    REJECTED
}
