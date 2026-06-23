package com.zcqiand.expense.dto;

import com.zcqiand.expense.entity.ApprovalDecision;
import com.zcqiand.expense.entity.ApprovalLevel;

/**
 * 审批请求 DTO。
 *
 * Service 层会再做语义校验（reject 时 reason 必填、level 必须与金额阈值匹配）。
 */
public record ApprovalRequest(
        Long approverId,
        ApprovalLevel level,
        ApprovalDecision decision,
        String reason) {
}
