package com.zcqiand.expense.dto;

import java.math.BigDecimal;

/**
 * 按申请人分组的报表聚合结果。
 *
 * applicantId: 申请人 ID
 * count: 该申请人提交的报销单笔数
 * totalAmount: 该申请人报销单金额合计
 *
 * 用 Java record：天然 immutable，自动 equals/hashCode/toString，
 * 与 ApiResponse 风格一致。供 topApplicantsByAmount 报表返回。
 */
public record ApplicantBreakdown(long applicantId, long count, BigDecimal totalAmount) {
}
