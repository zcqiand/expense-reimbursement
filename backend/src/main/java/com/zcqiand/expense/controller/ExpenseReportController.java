package com.zcqiand.expense.controller;

import com.zcqiand.expense.dto.ApiResponse;
import com.zcqiand.expense.dto.ApprovalRequest;
import com.zcqiand.expense.entity.ExpenseReport;
import com.zcqiand.expense.exception.ExpenseNotFoundException;
import com.zcqiand.expense.repository.ExpenseReportRepository;
import com.zcqiand.expense.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报销单 REST Controller。
 *
 * 端点设计遵循 RESTful + 动词资源化（POST submit/approve/pay 等）。
 * 所有响应通过 ApiResponse&lt;T&gt; 统一包装；异常由 GlobalExceptionHandler 翻译。
 *
 * 构造器注入（CLAUDE.md 强制规范）。
 */
@RestController
@RequestMapping("/api/v1/expense-reports")
@Tag(name = "报销单", description = "报销单的创建、查询与状态流转")
public class ExpenseReportController {

    private final ExpenseReportRepository repository;
    private final ExpenseService service;

    public ExpenseReportController(ExpenseReportRepository repository, ExpenseService service) {
        this.repository = repository;
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "创建报销单（DRAFT 状态）")
    public ApiResponse<ExpenseReport> create(@RequestBody CreateExpenseRequest request) {
        ExpenseReport report = new ExpenseReport();
        report.setApplicantId(request.applicantId());
        report.setAmount(request.amount());
        report.setReason(request.reason());
        // status 由 @PrePersist 默认设为 DRAFT
        return ApiResponse.ok(repository.save(report));
    }

    @GetMapping
    @Operation(summary = "列出所有报销单（骨架阶段全量返回；第 12 章会加分页）")
    public ApiResponse<List<ExpenseReport>> list() {
        return ApiResponse.ok(repository.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查报销单")
    public ApiResponse<ExpenseReport> get(@PathVariable Long id) {
        ExpenseReport report = repository.findById(id)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
        return ApiResponse.ok(report);
    }

    // ------------------------------------------------------------------
    // 状态机动作端点
    // ------------------------------------------------------------------

    @PostMapping("/{id}/submit")
    @Operation(summary = "提交报销单：DRAFT → SUBMITTED")
    public ApiResponse<ExpenseReport> submit(@PathVariable Long id) {
        return ApiResponse.ok(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "审批报销单：SUBMITTED → APPROVED 或 REJECTED")
    public ApiResponse<ExpenseReport> approve(@PathVariable Long id,
                                              @RequestBody ApprovalRequest request) {
        return ApiResponse.ok(service.approve(id, request));
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "财务付款：APPROVED → PAID")
    public ApiResponse<ExpenseReport> pay(@PathVariable Long id) {
        return ApiResponse.ok(service.pay(id));
    }

    /** 创建报销单请求体。Java record，自动 immutable + equals + toString。 */
    public record CreateExpenseRequest(Long applicantId, BigDecimal amount, String reason) {
    }
}
