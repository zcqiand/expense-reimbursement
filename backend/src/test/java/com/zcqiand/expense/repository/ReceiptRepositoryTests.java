package com.zcqiand.expense.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zcqiand.expense.entity.Receipt;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receipt Repository 集成测试。
 *
 * 照搬 ExpenseServiceTests 范式：@SpringBootTest + H2 内嵌库（MODE=PostgreSQL，
 * H2Dialect，flyway 关闭，ddl-auto=create-drop）+ @Transactional 自动回滚。
 *
 * 验证：
 * - 保存 Receipt 后能拿到自增 id 与 createdAt
 * - findByExpenseReportId 命中同一 expenseReportId 下的所有票据
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:expense-receipt-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "app.ocr.engine=mock"
})
class ReceiptRepositoryTests {

    @Autowired
    private ReceiptRepository repository;

    private Receipt makeReceipt(Long expenseReportId, String ocrStatus) {
        Receipt r = new Receipt();
        r.setExpenseReportId(expenseReportId);
        r.setFilePath("/tmp/receipt-" + expenseReportId + ".png");
        r.setOcrText("发票金额 128.50 元");
        r.setOcrStatus(ocrStatus);
        return r;
    }

    @Test
    @DisplayName("保存 Receipt 后 id 与 createdAt 被填充")
    void saveAssignsIdAndCreatedAt() {
        Receipt saved = repository.save(makeReceipt(1L, "SUCCESS"));

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals("SUCCESS", saved.getOcrStatus());
    }

    @Test
    @DisplayName("findByExpenseReportId 命中同一报销单下的所有票据")
    void findByExpenseReportIdReturnsAll() {
        repository.save(makeReceipt(7L, "SUCCESS"));
        repository.save(makeReceipt(7L, "FAILED"));
        repository.save(makeReceipt(9L, "SUCCESS")); // 不同报销单

        List<Receipt> hits = repository.findByExpenseReportId(7L);

        assertEquals(2, hits.size());
    }

    @Test
    @DisplayName("findByExpenseReportId 无命中返回空列表")
    void findByExpenseReportIdEmpty() {
        List<Receipt> hits = repository.findByExpenseReportId(404L);

        assertNotNull(hits);
        assertEquals(0, hits.size());
    }
}
