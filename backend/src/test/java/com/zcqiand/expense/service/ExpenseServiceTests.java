package com.zcqiand.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zcqiand.expense.dto.ApprovalRequest;
import com.zcqiand.expense.entity.ApprovalDecision;
import com.zcqiand.expense.entity.ApprovalLevel;
import com.zcqiand.expense.entity.ExpenseReport;
import com.zcqiand.expense.entity.ExpenseStatus;
import com.zcqiand.expense.exception.ExpenseNotFoundException;
import com.zcqiand.expense.exception.IllegalStateTransitionException;
import com.zcqiand.expense.exception.ValidationException;
import com.zcqiand.expense.repository.ApprovalRecordRepository;
import com.zcqiand.expense.repository.ExpenseReportRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * ExpenseService 状态机单元测试。
 *
 * 用 @SpringBootTest + H2 内嵌库——能跑真实 JPA 与事务，避免 Mockito 大量
 * stub Repository 让测试与实现耦合过紧。@Transactional 让每个测试自动回滚。
 *
 * 测试结构按状态机分组：
 * - submit: DRAFT → SUBMITTED（+ 非 DRAFT 都报 409）
 * - approve（小额）: SUBMITTED + MANAGER → APPROVED 一步到位
 * - approve（大额）: 需要 MANAGER + FINANCE_DIRECTOR 两级
 * - approve（拒绝）: 任何级别都可拒，reason 必填
 * - approve（自批）: 申请人不能审批自己 → 400
 * - pay: APPROVED → PAID
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:expense-svc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
class ExpenseServiceTests {

    @Autowired
    private ExpenseService service;

    @Autowired
    private ExpenseReportRepository expenseRepo;

    @Autowired
    private ApprovalRecordRepository approvalRepo;

    private ExpenseReport makeReport(BigDecimal amount, ExpenseStatus status) {
        ExpenseReport r = new ExpenseReport();
        r.setApplicantId(1L);
        r.setAmount(amount);
        r.setReason("打车去客户现场");
        r.setStatus(status);
        return expenseRepo.save(r);
    }

    // ============= submit =============

    @Test
    @DisplayName("submit: DRAFT → SUBMITTED")
    void submitFromDraftSucceeds() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.DRAFT);

        ExpenseReport result = service.submit(saved.getId());

        assertEquals(ExpenseStatus.SUBMITTED, result.getStatus());
    }

    @Test
    @DisplayName("submit: 非 DRAFT 状态报 409")
    void submitFromNonDraftThrows() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.SUBMITTED);

        assertThrows(IllegalStateTransitionException.class, () -> service.submit(saved.getId()));
    }

    @Test
    @DisplayName("submit: 报销单不存在报 404")
    void submitMissingThrowsNotFound() {
        assertThrows(ExpenseNotFoundException.class, () -> service.submit(999L));
    }

    // ============= approve 小额（< 1000）一级通过 =============

    @Test
    @DisplayName("approve 小额: MANAGER 一级即可 APPROVED")
    void approveSmallAmountByManagerSingleStep() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.SUBMITTED);

        ExpenseReport result = service.approve(saved.getId(),
                new ApprovalRequest(2L, ApprovalLevel.MANAGER, ApprovalDecision.APPROVED, null));

        assertEquals(ExpenseStatus.APPROVED, result.getStatus());
        assertEquals(1, approvalRepo.findByExpenseIdOrderByCreatedAtAsc(saved.getId()).size());
    }

    @Test
    @DisplayName("approve 小额: FINANCE_DIRECTOR 级别错误报 400")
    void approveSmallAmountByFinanceDirectorRejected() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.SUBMITTED);

        assertThrows(ValidationException.class, () -> service.approve(saved.getId(),
                new ApprovalRequest(2L, ApprovalLevel.FINANCE_DIRECTOR,
                        ApprovalDecision.APPROVED, null)));
    }

    // ============= approve 大额（≥ 1000）两级通过 =============

    @Test
    @DisplayName("approve 大额: MANAGER 后保持 SUBMITTED，FINANCE_DIRECTOR 后 APPROVED")
    void approveLargeAmountTwoLevels() {
        ExpenseReport saved = makeReport(new BigDecimal("3000.00"), ExpenseStatus.SUBMITTED);

        ExpenseReport afterFirst = service.approve(saved.getId(),
                new ApprovalRequest(2L, ApprovalLevel.MANAGER, ApprovalDecision.APPROVED, null));
        assertEquals(ExpenseStatus.SUBMITTED, afterFirst.getStatus(),
                "MANAGER 单批后应保持 SUBMITTED 等待 FINANCE_DIRECTOR");

        ExpenseReport afterSecond = service.approve(saved.getId(),
                new ApprovalRequest(3L, ApprovalLevel.FINANCE_DIRECTOR,
                        ApprovalDecision.APPROVED, null));
        assertEquals(ExpenseStatus.APPROVED, afterSecond.getStatus());
        assertEquals(2, approvalRepo.findByExpenseIdOrderByCreatedAtAsc(saved.getId()).size());
    }

    @Test
    @DisplayName("approve 大额: 跳过 MANAGER 直接 FINANCE_DIRECTOR 报 400")
    void approveLargeAmountSkipsManagerThrows() {
        ExpenseReport saved = makeReport(new BigDecimal("3000.00"), ExpenseStatus.SUBMITTED);

        assertThrows(ValidationException.class, () -> service.approve(saved.getId(),
                new ApprovalRequest(3L, ApprovalLevel.FINANCE_DIRECTOR,
                        ApprovalDecision.APPROVED, null)));
    }

    @Test
    @DisplayName("approve 大额: MANAGER 重复审批报 400")
    void approveLargeAmountDoubleManagerThrows() {
        ExpenseReport saved = makeReport(new BigDecimal("3000.00"), ExpenseStatus.SUBMITTED);

        service.approve(saved.getId(),
                new ApprovalRequest(2L, ApprovalLevel.MANAGER, ApprovalDecision.APPROVED, null));

        assertThrows(ValidationException.class, () -> service.approve(saved.getId(),
                new ApprovalRequest(4L, ApprovalLevel.MANAGER, ApprovalDecision.APPROVED, null)));
    }

    // ============= approve REJECTED =============

    @Test
    @DisplayName("approve 拒绝: MANAGER 拒绝带 reason → REJECTED 终态")
    void rejectByManagerWithReason() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.SUBMITTED);

        ExpenseReport result = service.approve(saved.getId(),
                new ApprovalRequest(2L, ApprovalLevel.MANAGER, ApprovalDecision.REJECTED,
                        "票据不全"));

        assertEquals(ExpenseStatus.REJECTED, result.getStatus());
    }

    @Test
    @DisplayName("approve 拒绝: reason 为空报 400")
    void rejectWithoutReasonThrows() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.SUBMITTED);

        assertThrows(ValidationException.class, () -> service.approve(saved.getId(),
                new ApprovalRequest(2L, ApprovalLevel.MANAGER, ApprovalDecision.REJECTED, "")));
    }

    @Test
    @DisplayName("approve 拒绝: reason null 报 400")
    void rejectWithNullReasonThrows() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.SUBMITTED);

        assertThrows(ValidationException.class, () -> service.approve(saved.getId(),
                new ApprovalRequest(2L, ApprovalLevel.MANAGER, ApprovalDecision.REJECTED, null)));
    }

    // ============= 申请人不能审批自己 =============

    @Test
    @DisplayName("approve: 申请人审批自己报 400")
    void approverCannotBeApplicant() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.SUBMITTED);
        // 申请人 ID 在 makeReport 里是 1L
        assertThrows(ValidationException.class, () -> service.approve(saved.getId(),
                new ApprovalRequest(1L, ApprovalLevel.MANAGER, ApprovalDecision.APPROVED, null)));
    }

    // ============= approve 非 SUBMITTED 状态 =============

    @Test
    @DisplayName("approve: DRAFT 状态报 409")
    void approveFromDraftThrows() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.DRAFT);

        assertThrows(IllegalStateTransitionException.class, () -> service.approve(saved.getId(),
                new ApprovalRequest(2L, ApprovalLevel.MANAGER, ApprovalDecision.APPROVED, null)));
    }

    @Test
    @DisplayName("approve: PAID 状态报 409（不能重复审批已付款）")
    void approveFromPaidThrows() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.PAID);

        assertThrows(IllegalStateTransitionException.class, () -> service.approve(saved.getId(),
                new ApprovalRequest(2L, ApprovalLevel.MANAGER, ApprovalDecision.APPROVED, null)));
    }

    // ============= pay =============

    @Test
    @DisplayName("pay: APPROVED → PAID")
    void payFromApprovedSucceeds() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.APPROVED);

        ExpenseReport result = service.pay(saved.getId());

        assertEquals(ExpenseStatus.PAID, result.getStatus());
    }

    @Test
    @DisplayName("pay: SUBMITTED 状态报 409")
    void payFromSubmittedThrows() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.SUBMITTED);

        assertThrows(IllegalStateTransitionException.class, () -> service.pay(saved.getId()));
    }

    @Test
    @DisplayName("pay: REJECTED 状态报 409")
    void payFromRejectedThrows() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.REJECTED);

        assertThrows(IllegalStateTransitionException.class, () -> service.pay(saved.getId()));
    }

    // ============= 端到端正向流程 =============

    @Test
    @DisplayName("端到端：DRAFT → SUBMITTED → APPROVED → PAID")
    void endToEndHappyPath() {
        ExpenseReport saved = makeReport(new BigDecimal("500.00"), ExpenseStatus.DRAFT);

        ExpenseReport submitted = service.submit(saved.getId());
        assertEquals(ExpenseStatus.SUBMITTED, submitted.getStatus());

        ExpenseReport approved = service.approve(saved.getId(),
                new ApprovalRequest(2L, ApprovalLevel.MANAGER, ApprovalDecision.APPROVED, null));
        assertEquals(ExpenseStatus.APPROVED, approved.getStatus());

        ExpenseReport paid = service.pay(saved.getId());
        assertEquals(ExpenseStatus.PAID, paid.getStatus());

        // 验证审批记录链
        assertEquals(1, approvalRepo.findByExpenseIdOrderByCreatedAtAsc(saved.getId()).size());
        // 验证时间戳被填充
        assertNotNull(paid.getCreatedAt());
        assertNotNull(paid.getUpdatedAt());
        assertTrue(paid.getUpdatedAt().isAfter(paid.getCreatedAt())
                || paid.getUpdatedAt().equals(paid.getCreatedAt()));
    }
}
