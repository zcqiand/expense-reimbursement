package com.zcqiand.expense.service;

import com.zcqiand.expense.dto.ApplicantBreakdown;
import com.zcqiand.expense.dto.MonthlySummary;
import com.zcqiand.expense.dto.StatusBreakdown;
import com.zcqiand.expense.entity.ExpenseReport;
import com.zcqiand.expense.repository.ExpenseReportRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报表聚合服务。第 38 章报表聚合的核心实现。
 *
 * 基于现有 expense_report 表字段做只读聚合，不修改现有表、不引入部门字段——
 * 维度只有现成的 status / applicantId / amount / createdAt 四列。
 *
 * 三类聚合：
 * - summaryByStatus：按状态分组，库内 GROUP BY 直接出 count + amount 合计
 * - summaryByMonth：指定月份的 count + amount 合计，按 created_at 落在该月闭区间
 * - topApplicantsByAmount：按申请人 amount 合计降序取前 N
 *
 * 所有查询走只读事务——报表无副作用。
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ExpenseReportRepository expenseRepo;

    public ReportService(ExpenseReportRepository expenseRepo) {
        this.expenseRepo = expenseRepo;
    }

    /**
     * 按状态分组汇总。库内 GROUP BY 聚合，返回每个 status 的 count + amount 合计。
     *
     * @return 不保证顺序的状态分组列表，空表返回空列表
     */
    public List<StatusBreakdown> summaryByStatus() {
        return expenseRepo.aggregateByStatus();
    }

    /**
     * 指定月份的报销汇总。按 created_at 落在该月闭区间过滤后聚合 count + amount。
     * 用 UTC 边界构造 [月初 00:00, 下月初 00:00) 区间，避免时区漂移。
     *
     * @param month 被汇总的年月，不可为 null
     * @return 该月份的汇总；无数据返回 count=0 / amount=0
     */
    public MonthlySummary summaryByMonth(YearMonth month) {
        OffsetDateTime start = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = month.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<ExpenseReport> inRange = expenseRepo.findByCreatedAtBetween(start, end);
        long count = inRange.size();
        BigDecimal total = inRange.stream()
                .map(ExpenseReport::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MonthlySummary(month, count, total);
    }

    /**
     * 按申请人 amount 合计降序取前 limit 名。
     * 库内 GROUP BY applicantId + ORDER BY sum(amount) DESC，再在 service 截 limit。
     * 截 limit 放 service 而非 @Query 是因为 JPQL 不便接收 Pageable 与构造表达式
     * 同时使用，service 截前 N 行可读且无副作用。
     *
     * @param limit 取前几名，必须 &gt; 0
     * @return 按金额合计降序的申请人聚合列表；空表返回空列表
     */
    public List<ApplicantBreakdown> topApplicantsByAmount(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, got " + limit);
        }
        List<ApplicantBreakdown> all = expenseRepo.aggregateByApplicant();
        return all.size() <= limit ? all : all.subList(0, limit);
    }
}
