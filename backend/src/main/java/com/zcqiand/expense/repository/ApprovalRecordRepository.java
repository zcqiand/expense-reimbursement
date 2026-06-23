package com.zcqiand.expense.repository;

import com.zcqiand.expense.entity.ApprovalRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

    /** 按报销单查所有审批记录，按 created_at 升序——保留审批链时序。 */
    List<ApprovalRecord> findByExpenseIdOrderByCreatedAtAsc(Long expenseId);
}
