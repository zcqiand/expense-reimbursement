package com.zcqiand.expense.repository;

import com.zcqiand.expense.entity.ExpenseReport;
import com.zcqiand.expense.entity.ExpenseStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 报销单 JPA Repository。
 *
 * Spring Data 自动生成 CRUD 实现——这是 JPA + Hibernate 模式的核心
 * 价值，比手写 SQLAlchemy session.query 链或 MyBatis XML 都要简洁。
 *
 * 这里只放最小必需的查询接口；第 12 章 6.x 节会补 findByApplicantId
 * 等具体业务查询方法。
 */
public interface ExpenseReportRepository extends JpaRepository<ExpenseReport, Long> {

    /** 按状态查报销单——审批列表与已批准待付款列表都靠这个。 */
    List<ExpenseReport> findByStatus(ExpenseStatus status);
}
