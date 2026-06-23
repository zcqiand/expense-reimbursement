package com.zcqiand.expense.entity;

/**
 * 报销单状态机。
 *
 * 状态流转（CLAUDE.md 业务规则）：
 *   DRAFT → SUBMITTED → APPROVED → PAID
 *                    ↘ REJECTED（终态）
 *
 * 用 enum 表达状态机是有意为之——所有非法状态在编译期就被排除。
 * 第 12 章 Service 层的状态机校验会基于这个枚举做 switch 校验。
 */
public enum ExpenseStatus {
    /** 草稿态：申请人正在编辑，未提交 */
    DRAFT,
    /** 已提交：等待审批 */
    SUBMITTED,
    /** 已批准：等待财务付款 */
    APPROVED,
    /** 已拒绝：终态，需带 reject_reason */
    REJECTED,
    /** 已付款：终态 */
    PAID
}
