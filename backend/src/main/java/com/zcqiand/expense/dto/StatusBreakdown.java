package com.zcqiand.expense.dto;

import java.math.BigDecimal;

/**
 * 按状态分组的报表聚合结果。
 *
 * status: 报销单状态名（DRAFT / SUBMITTED / APPROVED / REJECTED / PAID）
 * count: 该状态下的报销单笔数
 * totalAmount: 该状态下报销单金额合计
 *
 * 用 Java record：天然 immutable，自动 equals/hashCode/toString，
 * 与 ApiResponse 风格一致。
 */
public record StatusBreakdown(String status, long count, BigDecimal totalAmount) {
}
