package com.zcqiand.expense.repository;

import com.zcqiand.expense.entity.Receipt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 票据 JPA Repository。
 *
 * 与 ExpenseReportRepository 同一风格：Spring Data 自动生成 CRUD 实现，
 * 只补一个按业务场景的派生查询方法。
 */
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    /**
     * 按报销单 ID 查所有票据——报销单详情页列出该单下上传过的全部票据，
     * 含成功与失败的 OCR 记录，便于追溯。
     */
    List<Receipt> findByExpenseReportId(Long expenseReportId);
}
