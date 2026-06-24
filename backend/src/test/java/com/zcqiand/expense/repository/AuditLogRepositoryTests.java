package com.zcqiand.expense.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zcqiand.expense.entity.AuditLog;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuditLog Repository 集成测试。
 *
 * 照搬 ReceiptRepositoryTests 范式：@SpringBootTest + H2 内嵌库（MODE=PostgreSQL，
 * H2Dialect，flyway 关闭，ddl-auto=create-drop）+ @Transactional 自动回滚。
 *
 * 验证：
 * - 保存 AuditLog 后能拿到自增 id 与 @PrePersist 填充的 createdAt
 * - findByEntityIdAndEntityType 命中指定实体的所有审计流水
 * - 不同实体/类型互不串
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:expense-audit-repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "app.ocr.engine=mock"
})
class AuditLogRepositoryTests {

    @Autowired
    private AuditLogRepository repository;

    private AuditLog makeLog(String actionType, String entityType, Long entityId, Long operatorId) {
        AuditLog log = new AuditLog();
        log.setActionType(actionType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOperatorId(operatorId);
        log.setDetail("操作流水：" + actionType);
        return log;
    }

    @Test
    @DisplayName("保存 AuditLog 后 id 与 createdAt 被填充")
    void saveAssignsIdAndCreatedAt() {
        AuditLog saved = repository.save(
                makeLog(AuditLog.ACTION_SUBMIT, AuditLog.ENTITY_EXPENSE_REPORT, 1L, 100L));

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals(AuditLog.ACTION_SUBMIT, saved.getActionType());
    }

    @Test
    @DisplayName("findByEntityIdAndEntityType 命中同一实体的所有审计流水")
    void findByEntityIdAndEntityTypeReturnsAll() {
        repository.save(makeLog(AuditLog.ACTION_SUBMIT,
                AuditLog.ENTITY_EXPENSE_REPORT, 7L, 100L));
        repository.save(makeLog(AuditLog.ACTION_APPROVE,
                AuditLog.ENTITY_EXPENSE_REPORT, 7L, 200L));
        repository.save(makeLog(AuditLog.ACTION_SUBMIT,
                AuditLog.ENTITY_EXPENSE_REPORT, 9L, 100L));
        repository.save(makeLog(AuditLog.ACTION_OCR,
                AuditLog.ENTITY_RECEIPT, 7L, 100L));

        List<AuditLog> hits = repository.findByEntityIdAndEntityType(
                7L, AuditLog.ENTITY_EXPENSE_REPORT);

        assertEquals(2, hits.size());
    }

    @Test
    @DisplayName("findByEntityIdAndEntityType 无命中返回空列表")
    void findByEntityIdAndEntityTypeEmpty() {
        List<AuditLog> hits = repository.findByEntityIdAndEntityType(
                404L, AuditLog.ENTITY_EXPENSE_REPORT);

        assertNotNull(hits);
        assertEquals(0, hits.size());
    }
}
