package com.zcqiand.expense.entity;

/**
 * 审批层级。
 *
 * 业务规则（CLAUDE.md）：
 * - 金额 < 1000：MANAGER 一级审批通过即可
 * - 金额 ≥ 1000：MANAGER 先批 + FINANCE_DIRECTOR 再批，两级都通过才进 APPROVED
 *
 * 用 enum 而非字符串常量，保证状态机迁移逻辑在编译期就能穷举。
 */
public enum ApprovalLevel {
    /** 直属经理 */
    MANAGER,
    /** 财务总监 */
    FINANCE_DIRECTOR
}
