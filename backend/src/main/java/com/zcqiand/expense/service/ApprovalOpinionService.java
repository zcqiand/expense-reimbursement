package com.zcqiand.expense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 审批意见生成服务——第 16 章「精准控制大模型」的实物落地。
 *
 * 三件套组合拳（ch16 三个核心机制，逐个真实实现）：
 *
 * 1. JSON Schema 结构化输出约束
 *    systemPrompt 里内嵌一段 JSON Schema，明确告诉模型「只输出符合这个
 *    schema 的 JSON、不要任何解释文字」。schema 锁定三字段
 *    (summary/reasoning/suggestion) 都非空——把「自由文本」收敛成「可解析
 *    的结构」。
 *
 * 2. 验证（validate）
 *    拿到模型文本 → 抽取 JSON → 反序列化成 ApprovalOpinion → 调
 *    ApprovalOpinion.isComplete() 校验三字段非空。任一步失败都视为
 *    「输出不合规」。
 *
 * 3. 修复循环（repair loop）
 *    失败不放弃：把上次的错误（缺字段 / JSON 解析失败 / 多余解释文字）回喂
 *    给模型，让它「看着自己的错误重输出一次」。最多重试 MAX_REPAIR 次
 *    （=2，含首次共 3 次尝试）；仍不合规才抛 OpinionGenerationException。
 *
 * 不耦合进 ExpenseService.approve() 状态机：审批意见是「辅助决策」的展示
 * 字段，不是状态流转的输入——保持 approve() 不动，现有测试不受影响。本
 * Service 独立被 Controller 按需调用。
 *
 * 构造器注入（CLAUDE.md 强制）；类级 @Transactional：生成成功后写
 * ApprovalRecord.opinion 与读取上下文在同一事务内。
 */
@Service
@Transactional
public class ApprovalOpinionService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalOpinionService.class);

    /** 验证-修复循环上限：含首次共尝试 1 + MAX_REPAIR 次。 */
    private static final int MAX_REPAIR = 2;

    /** 内嵌的 JSON Schema——把模型输出约束成 ApprovalOpinion 三字段结构。 */
    private static final String OPINION_SCHEMA = """
            {
              "type": "object",
              "additionalProperties": false,
              "required": ["summary", "reasoning", "suggestion"],
              "properties": {
                "summary":   { "type": "string", "minLength": 1,
                               "description": "一句话结论" },
                "reasoning": { "type": "string", "minLength": 1,
                               "description": "判断依据" },
                "suggestion":{ "type": "string", "minLength": 1,
                               "description": "给审批人的可操作建议" }
              }
            }
            """;

    private static final String SYSTEM_PROMPT = """
            你是一名严谨的企业财务审批助手。你的任务是根据给定的报销单信息
            与审批历史，生成一条结构化的审批意见。

            输出格式约束（严格遵守）：
            - 只输出一个 JSON 对象，不要任何 Markdown 代码块标记、不要任何
              解释性文字、不要前缀后缀
            - JSON 必须符合下面的 JSON Schema：
            """ + OPINION_SCHEMA + """

            字段语义：
            - summary   一句话结论（如：建议批准 / 建议驳回 / 建议补充材料）
            - reasoning 判断依据：结合金额、事由、审批历史给出推理过程
            - suggestion 给审批人的可操作建议（如：请补充发票编号）

            若之前的输出有错误，错误信息会作为新输入回传给你，请据此修正后
            重新输出严格符合 Schema 的 JSON。
            """;

    private final OpinionModelClient modelClient;
    private final ExpenseReportRepository expenseRepo;
    private final ApprovalRecordRepository approvalRepo;
    private final ObjectMapper objectMapper;

    public ApprovalOpinionService(OpinionModelClient modelClient,
                                  ExpenseReportRepository expenseRepo,
                                  ApprovalRecordRepository approvalRepo,
                                  ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.expenseRepo = expenseRepo;
        this.approvalRepo = approvalRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * 为指定报销单的最新审批记录生成审批意见并落库。
     *
     * @param expenseId 报销单 ID
     * @return 结构化的 ApprovalOpinion（同时已序列化写入 ApprovalRecord.opinion）
     * @throws ExpenseNotFoundException      报销单不存在
     * @throws OpinionGenerationException    无审批记录 / 模型输出经修复循环仍不合规
     */
    public ApprovalOpinion generateAndSave(Long expenseId) {
        // 1. 取上下文：报销单 + 最近一条审批记录
        ExpenseReport report = expenseRepo.findById(expenseId)
                .orElseThrow(() -> new ExpenseNotFoundException(expenseId));

        List<ApprovalRecord> records =
                approvalRepo.findByExpenseIdOrderByCreatedAtAsc(expenseId);
        if (records.isEmpty()) {
            throw OpinionGenerationException.noApprovalRecord(expenseId);
        }
        ApprovalRecord latest = records.get(records.size() - 1);

        // 2. 构造首轮 user prompt（schema 约束已在 systemPrompt 里）
        String userPrompt = buildUserPrompt(report, latest, null);

        // 3. 验证-修复循环
        ApprovalOpinion opinion = null;
        String lastError = null;
        for (int attempt = 0; attempt <= MAX_REPAIR; attempt++) {
            String prompt = (attempt == 0)
                    ? userPrompt
                    : buildUserPrompt(report, latest, lastError);

            String raw = modelClient.chat(SYSTEM_PROMPT, prompt);
            log.debug("审批意见生成 attempt={} expenseId={} raw={}", attempt, expenseId, raw);

            try {
                ApprovalOpinion candidate = parse(raw);
                if (candidate.isComplete()) {
                    opinion = candidate;
                    break;
                }
                lastError = "输出字段不完整（存在空字段）: " + raw;
            } catch (OpinionParseException ex) {
                lastError = "JSON 解析失败 (" + ex.getMessage() + "): " + raw;
            }

            log.warn("审批意见 attempt={} 不合规，将进入修复重试: {}", attempt, lastError);
        }

        if (opinion == null) {
            throw OpinionGenerationException.modelOutputInvalid(lastError);
        }

        // 4. 落库：序列化为 JSON 写入 ApprovalRecord.opinion
        try {
            latest.setOpinion(objectMapper.writeValueAsString(opinion));
        } catch (Exception ex) {
            // ApprovalOpinion 是简单 record，序列化几乎不可能失败；真失败也不应吞
            throw new OpinionSerializationException("审批意见序列化失败", ex);
        }
        approvalRepo.save(latest);

        return opinion;
    }

    // ------------------------------------------------------------------
    // 内部：prompt 构造、JSON 解析
    // ------------------------------------------------------------------

    private String buildUserPrompt(ExpenseReport report, ApprovalRecord latest,
                                   String repairHint) {
        StringBuilder sb = new StringBuilder();
        sb.append("报销单信息：\n");
        sb.append("- 报销单 ID: ").append(report.getId()).append('\n');
        sb.append("- 申请人 ID: ").append(report.getApplicantId()).append('\n');
        sb.append("- 金额: ").append(report.getAmount()).append('\n');
        sb.append("- 事由: ").append(report.getReason() == null ? "(无)" : report.getReason()).append('\n');
        sb.append("- 当前状态: ").append(report.getStatus()).append('\n');
        sb.append("\n最新审批记录：\n");
        sb.append("- 审批人 ID: ").append(latest.getApproverId()).append('\n');
        sb.append("- 审批级别: ").append(latest.getLevel()).append('\n');
        sb.append("- 审批决定: ").append(latest.getDecision()).append('\n');
        sb.append("- 审批理由: ").append(latest.getReason() == null ? "(无)" : latest.getReason()).append('\n');

        if (repairHint != null) {
            sb.append("\n【上一轮输出错误】").append(repairHint).append('\n');
            sb.append("请严格按 JSON Schema 重新输出，不要重复错误。\n");
        } else {
            sb.append("\n请按系统提示中的 JSON Schema 输出审批意见。\n");
        }
        return sb.toString();
    }

    /**
     * 把模型原始文本解析成 ApprovalOpinion。
     *
     * 做了两层鲁棒性：
     * - extractJson：模型偶尔会用 ```json ... ``` 包裹或带解释文字，抽出
     *   第一个 { 到最后一个 } 之间的子串
     * - 字段缺失时 isComplete() 会兜底；这里只管解析
     */
    private ApprovalOpinion parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new OpinionParseException("模型输出为空");
        }
        String json = extractJson(raw);
        try {
            JsonNode node = objectMapper.readTree(json);
            String summary = textOrNull(node, "summary");
            String reasoning = textOrNull(node, "reasoning");
            String suggestion = textOrNull(node, "suggestion");
            return new ApprovalOpinion(summary, reasoning, suggestion);
        } catch (Exception ex) {
            throw new OpinionParseException(ex.getMessage());
        }
    }

    /** 从可能带 Markdown 围栏或解释文字的输出中抽出最外层 JSON 对象。 */
    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            throw new OpinionParseException("未找到 JSON 对象边界");
        }
        return raw.substring(start, end + 1);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String text = child.asText();
        return text == null || text.isBlank() ? null : text;
    }

    /** 内部解析异常——只用于在循环里传递错误信息，不外抛。 */
    private static final class OpinionParseException extends RuntimeException {
        OpinionParseException(String message) {
            super(message);
        }
    }

    /**
     * 序列化失败——理论上不可达（简单 record 序列化），保留以避免 try/catch
     * 吞异常。继承 RuntimeException 让事务回滚。
     */
    private static final class OpinionSerializationException extends RuntimeException {
        OpinionSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
