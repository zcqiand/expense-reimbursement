package com.zcqiand.expense.repository;

import com.zcqiand.expense.entity.ExpenseReport;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 测试专用 Repository——覆写 created_at 以验证按月聚合。
 *
 * 设计取舍（为何不放在生产 Repository）：
 * - {@code ExpenseReport.createdAt} 标 {@code updatable = false}，由 {@code @PrePersist}
 *   单一写入点维护；生产 Repository 不应再暴露覆写它的方法，否则破坏不变性、
 *   也容易让业务代码误用。
 * - {@code @Modifying} JPQL 跳过一级缓存，生产路径用上后会产生「同事务内读不到刚写的值」
 *   之类难以排查的脆弱行为。把它隔离到测试类，仅测试切片可见，从根上杜绝误用。
 *
 * 仅 {@code src/test} 下可见：测试类注入它替代直接调生产 repo 的修改器，按月聚合
 * 测试用例照旧能覆写 createdAt。
 */
public interface TestExpenseReportTimeRepository extends JpaRepository<ExpenseReport, Long> {

    /**
     * 覆写指定报销单的 created_at。配合 {@code flush + clear} 让一级缓存失效，
     * 保证后续 {@code findByCreatedAtBetween} 读到新值。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ExpenseReport e set e.createdAt = :createdAt where e.id = :id")
    void setCreatedAt(@Param("id") Long id, @Param("createdAt") OffsetDateTime createdAt);
}
