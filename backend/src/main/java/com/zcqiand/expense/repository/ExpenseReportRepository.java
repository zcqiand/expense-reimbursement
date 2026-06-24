package com.zcqiand.expense.repository;

import com.zcqiand.expense.dto.ApplicantBreakdown;
import com.zcqiand.expense.dto.StatusBreakdown;
import com.zcqiand.expense.entity.ExpenseReport;
import com.zcqiand.expense.entity.ExpenseStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 报销单 JPA Repository。
 *
 * Spring Data 自动生成 CRUD 实现——这是 JPA + Hibernate 模式的核心
 * 价值，比手写 SQLAlchemy session.query 链或 MyBatis XML 都要简洁。
 *
 * 第 12 章放最小必需的 findByStatus；第 38 章报表聚合新增三组
 * 只读聚合查询（@Query 投影到 dto record），用 SQL 直接在库内聚合，
 * 比拉全量到内存更省 IO。
 *
 * 不变量：created_at 由 {@code @PrePersist} 单一写入点维护（实体上
 * {@code updatable = false}），本生产 Repository 不再暴露任何覆写它的
 * 写方法——按月聚合测试需要覆写 created_at 时改用测试切片下的
 * {@code TestExpenseReportTimeRepository}。
 */
public interface ExpenseReportRepository extends JpaRepository<ExpenseReport, Long> {

    /** 按状态查报销单——审批列表与已批准待付款列表都靠这个。 */
    List<ExpenseReport> findByStatus(ExpenseStatus status);

    /**
     * 第 38 章报表聚合：按状态分组汇总 count + amount 合计。
     * 在库内 GROUP BY 聚合后投到 {@link StatusBreakdown} record，
     * 避免拉全量报销单到内存。
     */
    @Query("select new com.zcqiand.expense.dto.StatusBreakdown("
            + "cast(e.status as string), count(e), coalesce(sum(e.amount), 0)) "
            + "from ExpenseReport e group by e.status")
    List<StatusBreakdown> aggregateByStatus();

    /** 第 38 章报表聚合：按 created_at 落在某闭区间内的报销单。 */
    List<ExpenseReport> findByCreatedAtBetween(OffsetDateTime start, OffsetDateTime end);

    /**
     * 第 38 章报表聚合：按申请人分组，amount 合计降序取前 limit 名。
     * H2 / PostgreSQL 均支持 JPQL 的 order by + sum。
     */
    @Query("select new com.zcqiand.expense.dto.ApplicantBreakdown("
            + "e.applicantId, count(e), coalesce(sum(e.amount), 0)) "
            + "from ExpenseReport e group by e.applicantId order by sum(e.amount) desc, e.applicantId asc")
    List<ApplicantBreakdown> aggregateByApplicant();
}
