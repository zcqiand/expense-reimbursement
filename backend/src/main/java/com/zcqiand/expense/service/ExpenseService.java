package com.zcqiand.expense.service;

import com.zcqiand.expense.dto.ApprovalRequest;
import com.zcqiand.expense.entity.ApprovalDecision;
import com.zcqiand.expense.entity.ApprovalLevel;
import com.zcqiand.expense.entity.ApprovalRecord;
import com.zcqiand.expense.entity.ExpenseReport;
import com.zcqiand.expense.entity.ExpenseStatus;
import com.zcqiand.expense.exception.ExpenseNotFoundException;
import com.zcqiand.expense.exception.IllegalStateTransitionException;
import com.zcqiand.expense.exception.ValidationException;
import com.zcqiand.expense.repository.ApprovalRecordRepository;
import com.zcqiand.expense.repository.ExpenseReportRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报销单业务服务层——状态机核心。
 *
 * 状态机（CLAUDE.md）：
 *   DRAFT → SUBMITTED → APPROVED → PAID
 *                    ↘ REJECTED（终态）
 *
 * 审批策略（CLAUDE.md）：
 * - 金额 < 1000：MANAGER 一级即可通过
 * - 金额 ≥ 1000：MANAGER 先批 + FINANCE_DIRECTOR 再批，两级都通过才进 APPROVED
 *
 * 设计取舍：
 * - 状态机迁移用显式 switch + IllegalStateTransitionException，编译期穷举
 *   保证所有状态都有处理；非法迁移立刻 409，绝不静默忽略
 * - 申请人禁止审批自己——避免自批；落到 ValidationException 400
 * - 拒绝必填 reason——Service 层强制校验，DB CHECK 约束兜底
 * - 用构造器注入（@Service + final 字段）而非 @Autowired：CLAUDE.md 强制规范
 * - 类级 @Transactional：所有改状态动作都在事务内执行，部分失败回滚
 */
@Service
@Transactional
public class ExpenseService {

    /** 金额阈值：> 这个值需要两级审批。CLAUDE.md 业务规则。 */
    private static final BigDecimal TWO_LEVEL_THRESHOLD = new BigDecimal("1000.00");

    private final ExpenseReportRepository expenseRepo;
    private final ApprovalRecordRepository approvalRepo;

    public ExpenseService(ExpenseReportRepository expenseRepo,
                          ApprovalRecordRepository approvalRepo) {
        this.expenseRepo = expenseRepo;
        this.approvalRepo = approvalRepo;
    }

    // ------------------------------------------------------------------
    // 状态机动作
    // ------------------------------------------------------------------

    /**
     * 提交报销单：DRAFT → SUBMITTED。
     *
     * 其他状态调此方法都会 409。
     */
    public ExpenseReport submit(Long id) {
        ExpenseReport report = findOrThrow(id);
        if (report.getStatus() != ExpenseStatus.DRAFT) {
            throw new IllegalStateTransitionException(report.getStatus(), "submit");
        }
        report.setStatus(ExpenseStatus.SUBMITTED);
        return expenseRepo.save(report);
    }

    /**
     * 审批报销单。
     *
     * APPROVED 时根据金额阈值与审批层级决定是否进入终态：
     * - 金额 < 1000，level = MANAGER → 直接 APPROVED
     * - 金额 ≥ 1000，level = MANAGER → 仍保持 SUBMITTED 等 FINANCE_DIRECTOR
     * - 金额 ≥ 1000，level = FINANCE_DIRECTOR → 必须已有 MANAGER 批准，否则 400
     * - level = MANAGER 但金额 ≥ 1000 已被前一个 MANAGER 批过 → 400 重复审批
     *
     * REJECTED 直接进 REJECTED 终态，任一级别均可拒绝；reason 必填。
     */
    public ExpenseReport approve(Long id, ApprovalRequest request) {
        ExpenseReport report = findOrThrow(id);

        if (report.getStatus() != ExpenseStatus.SUBMITTED) {
            throw new IllegalStateTransitionException(report.getStatus(), "approve");
        }

        // 申请人不能审批自己——这是 CLAUDE.md 业务规则
        if (request.approverId() != null && request.approverId().equals(report.getApplicantId())) {
            throw new ValidationException("申请人不能审批自己的报销单");
        }

        // REJECTED 路径
        if (request.decision() == ApprovalDecision.REJECTED) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ValidationException("拒绝必须填写理由");
            }
            recordApproval(report, request);
            report.setStatus(ExpenseStatus.REJECTED);
            return expenseRepo.save(report);
        }

        // APPROVED 路径——根据金额阈值与层级决定下一步
        boolean needsTwoLevels = report.getAmount().compareTo(TWO_LEVEL_THRESHOLD) >= 0;

        if (!needsTwoLevels) {
            // 单级审批：必须是 MANAGER
            if (request.level() != ApprovalLevel.MANAGER) {
                throw new ValidationException(
                        "金额 " + report.getAmount() + " < " + TWO_LEVEL_THRESHOLD
                        + "，仅需 MANAGER 审批");
            }
            recordApproval(report, request);
            report.setStatus(ExpenseStatus.APPROVED);
            return expenseRepo.save(report);
        }

        // 两级审批
        long managerApprovals = approvalRepo.findByExpenseIdOrderByCreatedAtAsc(id).stream()
                .filter(r -> r.getLevel() == ApprovalLevel.MANAGER
                        && r.getDecision() == ApprovalDecision.APPROVED)
                .count();

        if (request.level() == ApprovalLevel.MANAGER) {
            if (managerApprovals > 0) {
                throw new ValidationException("MANAGER 已审批过，等待 FINANCE_DIRECTOR");
            }
            recordApproval(report, request);
            // 保持 SUBMITTED 等下一级
            return expenseRepo.save(report);
        }

        // FINANCE_DIRECTOR
        if (managerApprovals == 0) {
            throw new ValidationException("必须先经过 MANAGER 审批");
        }
        recordApproval(report, request);
        report.setStatus(ExpenseStatus.APPROVED);
        return expenseRepo.save(report);
    }

    /**
     * 财务付款：APPROVED → PAID。
     *
     * 只允许 APPROVED 进入 PAID；其他状态 409。
     */
    public ExpenseReport pay(Long id) {
        ExpenseReport report = findOrThrow(id);
        if (report.getStatus() != ExpenseStatus.APPROVED) {
            throw new IllegalStateTransitionException(report.getStatus(), "pay");
        }
        report.setStatus(ExpenseStatus.PAID);
        return expenseRepo.save(report);
    }

    // ------------------------------------------------------------------
    // 内部 helper
    // ------------------------------------------------------------------

    private ExpenseReport findOrThrow(Long id) {
        return expenseRepo.findById(id)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
    }

    private ApprovalRecord recordApproval(ExpenseReport report, ApprovalRequest request) {
        ApprovalRecord record = new ApprovalRecord();
        record.setExpenseId(report.getId());
        record.setApproverId(request.approverId());
        record.setLevel(request.level());
        record.setDecision(request.decision());
        record.setReason(request.reason());
        return approvalRepo.save(record);
    }
}
