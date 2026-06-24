package com.zcqiand.expense.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zcqiand.expense.entity.ExpenseReport;
import com.zcqiand.expense.entity.ExpenseStatus;
import com.zcqiand.expense.repository.ExpenseReportRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报表 Controller MockMvc 集成测试。
 *
 * 照搬 ExpenseReportControllerTests 范式：@SpringBootTest + @AutoConfigureMockMvc +
 * H2 内嵌库（MODE=PostgreSQL，H2Dialect，flyway 关闭，ddl-auto=create-drop）+
 * @Transactional 自动回滚。
 *
 * 验证：
 * - GET /api/v1/reports/summary 不带月份返回全量状态聚合 + top 申请人
 * - GET /api/v1/reports/summary?month=2026-03 带月份返回该月汇总
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:expense-report-mvc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "app.ocr.engine=mock"
})
class ReportControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExpenseReportRepository repository;

    private static OffsetDateTime atMonth(YearMonth ym, int day) {
        return OffsetDateTime.of(ym.atDay(day).atTime(10, 0), ZoneOffset.UTC);
    }

    private void seedReport(Long applicantId, BigDecimal amount, ExpenseStatus status,
                            OffsetDateTime createdAt) {
        ExpenseReport r = new ExpenseReport();
        r.setApplicantId(applicantId);
        r.setAmount(amount);
        r.setReason("测试报销单");
        r.setStatus(status);
        ExpenseReport saved = repository.save(r);
        repository.setCreatedAt(saved.getId(), createdAt);
    }

    @BeforeEach
    void seed() {
        // 申请人 1：SUBMITTED 500 + PAID 200 = 合计 700（status 维度各 1 笔）
        seedReport(1L, new BigDecimal("500.00"), ExpenseStatus.SUBMITTED,
                atMonth(YearMonth.of(2026, 3), 5));
        seedReport(1L, new BigDecimal("200.00"), ExpenseStatus.PAID,
                atMonth(YearMonth.of(2026, 3), 6));
        // 申请人 2：APPROVED 1000（命中 2026-03）
        seedReport(2L, new BigDecimal("1000.00"), ExpenseStatus.APPROVED,
                atMonth(YearMonth.of(2026, 3), 31));
        // 申请人 3：不命中 2026-03（2026-02）
        seedReport(3L, new BigDecimal("9999.00"), ExpenseStatus.PAID,
                atMonth(YearMonth.of(2026, 2), 28));
    }

    @Test
    @DisplayName("GET /api/v1/reports/summary 不带月份返回全量状态聚合 + ApiResponse.ok")
    void summaryWithoutMonthReturnsAllStatus() throws Exception {
        mockMvc.perform(get("/api/v1/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                // status 维度至少含 SUBMITTED / APPROVED / PAID 三行
                .andExpect(jsonPath("$.data.statusBreakdown").isArray())
                .andExpect(jsonPath("$.data.statusBreakdown[0].status").exists())
                // top 申请人：第一名应是申请人 3（9999），其次申请人 2（1000）
                .andExpect(jsonPath("$.data.topApplicants").isArray())
                .andExpect(jsonPath("$.data.topApplicants[0].applicantId").value(3))
                .andExpect(jsonPath("$.data.topApplicants[0].totalAmount").value(9999.00));
    }

    @Test
    @DisplayName("GET /api/v1/reports/summary?month=2026-03 返回该月汇总")
    void summaryWithMonthReturnsMonthly() throws Exception {
        mockMvc.perform(get("/api/v1/reports/summary").param("month", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.monthlySummary.month").value("2026-03"))
                // 命中 3 笔：500 + 200 + 1000 = 1700
                .andExpect(jsonPath("$.data.monthlySummary.count").value(3))
                .andExpect(jsonPath("$.data.monthlySummary.totalAmount").value(1700.00));
    }
}
