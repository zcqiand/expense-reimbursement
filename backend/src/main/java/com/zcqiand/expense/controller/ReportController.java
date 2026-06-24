package com.zcqiand.expense.controller;

import com.zcqiand.expense.dto.ApiResponse;
import com.zcqiand.expense.dto.ReportSummary;
import com.zcqiand.expense.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报表聚合 REST Controller——第 38 章案例的 HTTP 入口。
 *
 * 单端点：GET /api/v1/reports/summary（可选 month=YYYY-MM 参数）。聚合现有
 * expense_report 表数据返回按状态分组、按月份、按申请人 top N 的汇总。
 *
 * @Operation + @ApiResponses 注解是 CLAUDE.md 强制规范；@ApiResponses 用复数
 * 形式列多个状态码（200 / 400），让 Swagger UI 文档完整——照 ApprovalOpinionController
 * 风格，比仅有 @Operation 的旧 ExpenseReportController 更规范。
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "报表聚合", description = "报销单按状态 / 月份 / 申请人聚合报表（第 38 章）")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @Operation(
            summary = "聚合报销单报表",
            description = "返回按状态分组的 count + amount 合计、按申请人 amount 合计的 top "
                    + ReportSummary.DEFAULT_TOP_LIMIT
                    + " 名；可选 month=YYYY-MM 参数补一份该月汇总。"
                    + "聚合基于现有 expense_report 表，不引入部门字段。"
                    + "对应第 38 章「报表聚合 + 审计日志」案例。")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "聚合成功",
                    content = @Content(schema = @Schema(implementation = ReportSummary.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "month 参数格式不合法（应 YYYY-MM）")
    })
    public ApiResponse<ReportSummary> summary(
            @Parameter(description = "可选，格式 YYYY-MM，指定月份时补一份该月汇总")
            @RequestParam(name = "month", required = false) String month) {

        if (month == null || month.isBlank()) {
            return ApiResponse.ok(ReportSummary.ofAll(
                    service.summaryByStatus(),
                    service.topApplicantsByAmount(ReportSummary.DEFAULT_TOP_LIMIT)));
        }

        YearMonth ym = parseMonth(month);
        return ApiResponse.ok(new ReportSummary(
                service.summaryByStatus(),
                service.summaryByMonth(ym),
                service.topApplicantsByAmount(ReportSummary.DEFAULT_TOP_LIMIT)));
    }

    /** 解析 YYYY-MM；失败抛 IllegalArgumentException 让 GlobalExceptionHandler 翻译。 */
    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("month must be YYYY-MM, got: " + month, ex);
        }
    }

    /** 兜底：IllegalArgumentException 翻译成 400，避免被 GlobalExceptionHandler 当 500。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<ReportSummary> handleBadMonth(IllegalArgumentException ex) {
        return ApiResponse.error("VALIDATION_FAILED", ex.getMessage());
    }
}
