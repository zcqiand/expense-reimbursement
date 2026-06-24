package com.zcqiand.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zcqiand.expense.dto.ApplicantBreakdown;
import com.zcqiand.expense.dto.MonthlySummary;
import com.zcqiand.expense.dto.StatusBreakdown;
import com.zcqiand.expense.entity.ExpenseReport;
import com.zcqiand.expense.entity.ExpenseStatus;
import com.zcqiand.expense.repository.ExpenseReportRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * ReportService 报表聚合单元测试。
 *
 * 照搬 ExpenseServiceTests 范式：@SpringBootTest + H2 内嵌库（MODE=PostgreSQL，
 * H2Dialect，flyway 关闭，ddl-auto=create-drop）+ @Transactional 自动回滚。
 *
 * 验证三个聚合维度：
 * - summaryByStatus：按状态汇总 count + amount 总额
 * - summaryByMonth：指定月份的 count + amount 总额
 * - topApplicantsByAmount：按申请人 amount 汇总取 top N
 *
 * 断言全部校验具体数值，杜绝恒真。
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:expense-report-svc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "app.ocr.engine=mock"
})
class ReportServiceTests {

    @Autowired
    private ReportService service;

    @Autowired
    private ExpenseReportRepository expenseRepo;

    /** 直接构造并保存报销单，再覆写 created_at 以测月份过滤。 */
    private ExpenseReport makeReport(Long applicantId, BigDecimal amount,
                                     ExpenseStatus status, OffsetDateTime createdAt) {
        ExpenseReport r = new ExpenseReport();
        r.setApplicantId(applicantId);
        r.setAmount(amount);
        r.setReason("测试报销单");
        r.setStatus(status);
        ExpenseReport saved = expenseRepo.save(r);
        // @PrePersist 已写 createdAt，用原生 SQL 覆写以测月份维度（H2 支持）
        expenseRepo.setCreatedAt(saved.getId(), createdAt);
        return saved;
    }

    private static OffsetDateTime atMonth(YearMonth ym, int day) {
        return OffsetDateTime.of(ym.atDay(day).atTime(10, 0), ZoneOffset.UTC);
    }

    // ============= summaryByStatus =============

    @Test
    @DisplayName("summaryByStatus: 按状态分组汇总 count + amount")
    void summaryByStatusGroupsAndSums() {
        // SUBMITTED: 1 笔 500
        makeReport(1L, new BigDecimal("500.00"), ExpenseStatus.SUBMITTED,
                atMonth(YearMonth.of(2026, 3), 5));
        // APPROVED: 2 笔 共 3500
        makeReport(2L, new BigDecimal("1000.00"), ExpenseStatus.APPROVED,
                atMonth(YearMonth.of(2026, 3), 6));
        makeReport(3L, new BigDecimal("2500.00"), ExpenseStatus.APPROVED,
                atMonth(YearMonth.of(2026, 3), 7));
        // PAID: 1 笔 200
        makeReport(1L, new BigDecimal("200.00"), ExpenseStatus.PAID,
                atMonth(YearMonth.of(2026, 3), 8));

        List<StatusBreakdown> breakdowns = service.summaryByStatus();

        // 期望包含 SUBMITTED / APPROVED / PAID 三行
        StatusBreakdown submitted = breakdowns.stream()
                .filter(b -> b.status().equals(ExpenseStatus.SUBMITTED.name()))
                .findFirst().orElseThrow();
        StatusBreakdown approved = breakdowns.stream()
                .filter(b -> b.status().equals(ExpenseStatus.APPROVED.name()))
                .findFirst().orElseThrow();
        StatusBreakdown paid = breakdowns.stream()
                .filter(b -> b.status().equals(ExpenseStatus.PAID.name()))
                .findFirst().orElseThrow();

        assertEquals(1L, submitted.count());
        assertEquals(0, submitted.totalAmount().compareTo(new BigDecimal("500.00")));

        assertEquals(2L, approved.count());
        assertEquals(0, approved.totalAmount().compareTo(new BigDecimal("3500.00")));

        assertEquals(1L, paid.count());
        assertEquals(0, paid.totalAmount().compareTo(new BigDecimal("200.00")));
    }

    // ============= summaryByMonth =============

    @Test
    @DisplayName("summaryByMonth: 只汇总指定月份的数据")
    void summaryByMonthFiltersByMonth() {
        YearMonth target = YearMonth.of(2026, 3);
        // 命中：2026-03
        makeReport(1L, new BigDecimal("500.00"), ExpenseStatus.SUBMITTED,
                atMonth(target, 5));
        makeReport(2L, new BigDecimal("1000.00"), ExpenseStatus.APPROVED,
                atMonth(target, 31));
        // 不命中：2026-02 与 2026-04
        makeReport(3L, new BigDecimal("9999.00"), ExpenseStatus.PAID,
                atMonth(YearMonth.of(2026, 2), 28));
        makeReport(4L, new BigDecimal("8888.00"), ExpenseStatus.PAID,
                atMonth(YearMonth.of(2026, 4), 1));

        MonthlySummary summary = service.summaryByMonth(target);

        assertEquals(target, summary.month());
        assertEquals(2L, summary.count());
        assertEquals(0, summary.totalAmount().compareTo(new BigDecimal("1500.00")));
    }

    @Test
    @DisplayName("summaryByMonth: 无数据月份返回 count=0 amount=0")
    void summaryByMonthEmpty() {
        MonthlySummary summary = service.summaryByMonth(YearMonth.of(1999, 1));

        assertEquals(YearMonth.of(1999, 1), summary.month());
        assertEquals(0L, summary.count());
        assertEquals(0, summary.totalAmount().compareTo(BigDecimal.ZERO));
    }

    // ============= topApplicantsByAmount =============

    @Test
    @DisplayName("topApplicantsByAmount: 按申请人 amount 降序取前 N")
    void topApplicantsByAmountOrdersDesc() {
        // 申请人 1：合计 800
        makeReport(1L, new BigDecimal("300.00"), ExpenseStatus.SUBMITTED,
                atMonth(YearMonth.of(2026, 3), 1));
        makeReport(1L, new BigDecimal("500.00"), ExpenseStatus.SUBMITTED,
                atMonth(YearMonth.of(2026, 3), 2));
        // 申请人 2：合计 2000
        makeReport(2L, new BigDecimal("2000.00"), ExpenseStatus.APPROVED,
                atMonth(YearMonth.of(2026, 3), 3));
        // 申请人 3：合计 100
        makeReport(3L, new BigDecimal("100.00"), ExpenseStatus.PAID,
                atMonth(YearMonth.of(2026, 3), 4));

        List<ApplicantBreakdown> top2 = service.topApplicantsByAmount(2);

        assertEquals(2, top2.size());
        // 第一名应是申请人 2（2000）
        assertEquals(2L, top2.get(0).applicantId());
        assertEquals(0, top2.get(0).totalAmount().compareTo(new BigDecimal("2000.00")));
        // 第二名应是申请人 1（800）
        assertEquals(1L, top2.get(1).applicantId());
        assertEquals(0, top2.get(1).totalAmount().compareTo(new BigDecimal("800.00")));
        // 顺序：第一名 >= 第二名
        assertTrue(top2.get(0).totalAmount().compareTo(top2.get(1).totalAmount()) >= 0);
    }
}
