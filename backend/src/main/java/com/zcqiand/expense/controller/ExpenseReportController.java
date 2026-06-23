package com.zcqiand.expense.controller;

import com.zcqiand.expense.entity.ExpenseReport;
import com.zcqiand.expense.repository.ExpenseReportRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报销单 REST Controller——骨架阶段最小端到端：POST 创建 + GET 列表 + GET 详情。
 *
 * 第 12 章会扩展提交/审批/付款等状态机动作；这里只放 CRUD 让 docker-compose
 * 起来后能通过 swagger-ui 端到端跑通。
 *
 * 用构造器注入（不是 @Autowired 字段）——这是 CLAUDE.md 强制规范。
 */
@RestController
@RequestMapping("/api/v1/expense-reports")
@Tag(name = "报销单", description = "报销单的创建、查询与状态流转")
public class ExpenseReportController {

    private final ExpenseReportRepository repository;

    public ExpenseReportController(ExpenseReportRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    @Operation(summary = "创建报销单（DRAFT 状态）")
    public ResponseEntity<ExpenseReport> create(@RequestBody CreateExpenseRequest request) {
        ExpenseReport report = new ExpenseReport();
        report.setApplicantId(request.applicantId());
        report.setAmount(request.amount());
        report.setReason(request.reason());
        // status 由 @PrePersist 默认设为 DRAFT
        ExpenseReport saved = repository.save(report);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    @Operation(summary = "列出所有报销单（骨架阶段全量返回；第 12 章会加分页）")
    public List<ExpenseReport> list() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 ID 查报销单")
    public ResponseEntity<ExpenseReport> get(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建报销单的请求体。用 record 而非传统 POJO——Java 21 的现代特性，
     * 且能自动获得 equals/hashCode/toString。
     */
    public record CreateExpenseRequest(Long applicantId, BigDecimal amount, String reason) {
    }
}
