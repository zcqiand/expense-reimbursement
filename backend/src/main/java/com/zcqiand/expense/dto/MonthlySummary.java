package com.zcqiand.expense.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * 指定月份的报销汇总结果。
 *
 * month: 被汇总的年月（YearMonth）
 * count: 该月份内提交/创建的报销单笔数
 * totalAmount: 该月份内报销单金额合计
 *
 * 用 Java record：天然 immutable，自动 equals/hashCode/toString，
 * 与 ApiResponse 风格一致。
 */
public record MonthlySummary(YearMonth month, long count, BigDecimal totalAmount) {
}
