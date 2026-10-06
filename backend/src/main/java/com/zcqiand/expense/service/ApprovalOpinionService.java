package com.zcqiand.expense.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcqiand.expense.client.OpinionAgentClient;
import com.zcqiand.expense.client.OpinionContext;
import com.zcqiand.expense.dto.ApprovalOpinion;
import com.zcqiand.expense.entity.ApprovalRecord;
import com.zcqiand.expense.entity.ExpenseReport;
import com.zcqiand.expense.exception.ExpenseNotFoundException;
import com.zcqiand.expense.exception.OpinionGenerationException;
import com.zcqiand.expense.repository.ApprovalRecordRepository;
import com.zcqiand.expense.repository.ExpenseReportRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审批意见生成服务——v2 thin-client 编排层。
 *
 * v1 的编排内核（JSON Schema 约束 → validate → 修复循环 ≤2 次回喂）已整体
 * 搬入 Python sidecar（agent/，LangGraph 生成图，等价锚见 spec §3.2 表）；
 * 本类只做 Spring 侧剩下的三件事：
 *
 * 1. 前置校验（404 报销单不存在 / 409 无审批记录）——与 v1 逐字一致
 * 2. 组装结构化上下文 OpinionContext（spec §3.4 请求体，snake_case 契约）
 * 3. 经 OpinionAgentClient 端口取意见并落库（写 ApprovalRecord.opinion）
 *
 * sidecar 失败映射（409 形状，前端意见卡展示零改动）：
 * - 422 model_output_invalid → OPINION_MODEL_OUTPUT_INVALID（模型不合规）
 * - 不可达/超时/5xx → OPINION_AGENT_UNAVAILABLE（Review Focus 3/4）
 *
 * 构造器注入（CLAUDE.md 强制）；类级 @Transactional：生成成功后写
 * ApprovalRecord.opinion 与读取上下文在同一事务内；sidecar 异常沿
 * RuntimeException 传播 → 事务回滚，不留半截数据。
 */
@Service
@Transactional
public class ApprovalOpinionService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalOpinionService.class);

    private final OpinionAgentClient agentClient;
    private final ExpenseReportRepository expenseRepo;
    private final ApprovalRecordRepository approvalRepo;
    private final ObjectMapper objectMapper;

    public ApprovalOpinionService(OpinionAgentClient agentClient,
                                  ExpenseReportRepository expenseRepo,
                                  ApprovalRecordRepository approvalRepo,
                                  ObjectMapper objectMapper) {
        this.agentClient = agentClient;
        this.expenseRepo = expenseRepo;
        this.approvalRepo = approvalRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * 为指定报销单生成结构化审批意见并写入最新审批记录。
     *
     * 编排顺序（与 v1 等价，内核已外移）：
     * 1. 报销单存在性校验 → ExpenseNotFoundException（404）
     * 2. 最新审批记录 = findByExpenseIdOrderByCreatedAtAsc 列表末位；空 → 409
     * 3. 组装 OpinionContext（snake_case 经 client 序列化）
     * 4. agentClient.generate —— sidecar 全部失败形态在 client 内映射成 409
     * 5. 意见 JSON 序列化落 ApprovalRecord.opinion
     */
    public ApprovalOpinion generateAndSave(Long expenseId) {
        ExpenseReport report = expenseRepo.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));

        List<ApprovalRecord> records = approvalRepo.findByExpenseIdOrderByCreatedAtAsc(expenseId);
        if (records.isEmpty()) {
            throw OpinionGenerationException.noApprovalRecord(expenseId);
        }
        ApprovalRecord latest = records.get(records.size() - 1);

        OpinionContext context = new OpinionContext(
                new OpinionContext.Expense(
                        report.getId(),
                        report.getApplicantId(),
                        report.getAmount() == null ? null : report.getAmount().toPlainString(),
                        report.getReason(),
                        report.getStatus() == null ? null : report.getStatus().name()),
                new OpinionContext.Approval(
                        latest.getApproverId(),
                        latest.getLevel() == null ? null : latest.getLevel().name(),
                        latest.getDecision() == null ? null : latest.getDecision().name(),
                        latest.getReason()));

        // sidecar 不可达 / 422 不合规 / 非法响应 → OpinionGenerationException(409) 由此抛出，
        // 类级事务随之回滚（RF3：不留半截数据）
        ApprovalOpinion opinion = agentClient.generate(context);
        log.debug("opinion generated for expense {}: summary={}", expenseId, opinion.summary());

        try {
            latest.setOpinion(objectMapper.writeValueAsString(opinion));
        } catch (Exception ex) {
            throw new OpinionSerializationException("审批意见序列化失败", ex);
        }
        approvalRepo.save(latest);
        return opinion;
    }

    /**
     * 意见序列化失败——理论死路（ApprovalOpinion 是纯 record，Jackson 必能写），
     * 但不能让 IOException 裸逃到事务边界外吞掉业务异常，包裹成 RuntimeException 保持 409 形状。
     */
    static class OpinionSerializationException extends RuntimeException {
        OpinionSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
