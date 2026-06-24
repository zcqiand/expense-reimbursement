package com.zcqiand.expense.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 报表汇总响应负载。第 38 章 GET /api/v1/reports/summary 的 data 字段。
 *
 * statusBreakdown：按状态分组的 count + amount 合计，全量聚合
 * monthlySummary：仅当请求带 month 参数时填充；不带则省略（@JsonInclude NON_NULL）
 * topApplicants：按申请人 amount 合计降序的 top 5
 *
 * 用 Java record：天然 immutable，自动 equals/hashCode/toString，
 * 与 ApiResponse 风格一致。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportSummary(
        List<StatusBreakdown> statusBreakdown,
        MonthlySummary monthlySummary,
        List<ApplicantBreakdown> topApplicants) {

    /** Top 申请人默认取前 5 名。 */
    public static final int DEFAULT_TOP_LIMIT = 5;

    /** 不带月份的快捷构造：monthlySummary 为 null，序列化时省略。 */
    public static ReportSummary ofAll(List<StatusBreakdown> statusBreakdown,
                                      List<ApplicantBreakdown> topApplicants) {
        return new ReportSummary(statusBreakdown, null, topApplicants);
    }
}
