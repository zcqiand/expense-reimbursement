package com.zcqiand.expense.repository;

import com.zcqiand.expense.entity.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 审计日志 JPA Repository。
 *
 * 照搬 ExpenseReportRepository 范式：Spring Data 自动生成 CRUD 实现，
 * 这里只补按实体定位审计流水的查询方法——报销单详情页 / 票据详情页
 * 都要列出该实体的全部操作历史。
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** 按实体 ID + 实体类型查审计流水——实体详情页的操作历史。 */
    List<AuditLog> findByEntityIdAndEntityType(Long entityId, String entityType);
}
