package com.zcqiand.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcqiand.expense.dto.ApprovalOpinion;
import com.zcqiand.expense.entity.ApprovalDecision;
import com.zcqiand.expense.entity.ApprovalLevel;
import com.zcqiand.expense.entity.ApprovalRecord;
import com.zcqiand.expense.entity.ExpenseReport;
import com.zcqiand.expense.entity.ExpenseStatus;
import com.zcqiand.expense.exception.ExpenseNotFoundException;
import com.zcqiand.expense.exception.OpinionGenerationException;
import com.zcqiand.expense.repository.ApprovalRecordRepository;
import com.zcqiand.expense.repository.ExpenseReportRepository;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * ApprovalOpinionService 测试——第 16 章「精准控制大模型」三机制的回归网。
 *
 * 测试策略照搬 ExpenseServiceTests 范式（@SpringBootTest + @Transactional
 * + H2 内嵌 + @TestPropertySource），但用一个 @TestConfiguration 提供打桩
 * 的 OpinionModelClient（@Primary 覆盖真实的 AnthropicOpinionClient）——
 * 这样：
 * - 不发真实网络请求、不需要 ANTHROPIC_API_KEY
 * - stub 可编程返回任意文本，精确驱动验证-修复循环的每条分支
 *
 * 覆盖三条核心路径：
 * - happy path：首轮即返回合法 JSON → 解析成功 + 持久化
 * - 验证-修复：首轮非法（缺字段）→ repair → 次轮合法 → 成功
 * - 超过重试上限仍非法 → 抛 OpinionGenerationException
 * 外加：报销单不存在 → 404；无审批记录 → 409。
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:expense-opinion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        // 测试不打真实 Anthropic：关闭 AnthropicOpinionClient bean，注入 stub
        "app.opinion.anthropic.enabled=false"
})
class ApprovalOpinionServiceTests {

    @Autowired
    private ApprovalOpinionService service;

    @Autowired
    private ExpenseReportRepository expenseRepo;

    @Autowired
    private ApprovalRecordRepository approvalRepo;

    @Autowired
    private ObjectMapper objectMapper;

    /** 可编程 stub：把预设回复排队，每次 chat() 弹一个。 */
    @Autowired
    private StubOpinionModelClient stubClient;

    // ============= happy path =============

    @Test
    @DisplayName("happy path: 首轮合法 JSON → 解析成功 + 持久化到 ApprovalRecord.opinion")
    void happyPathPersistsOpinion() throws Exception {
        ExpenseReport report = makeReport(new BigDecimal("500.00"));
        ApprovalRecord record = makeApproval(report);
        stubClient.enqueue("""
                {"summary":"建议批准","reasoning":"金额合规且事由清晰","suggestion":"可进入付款流程"}
                """);

        ApprovalOpinion opinion = service.generateAndSave(report.getId());

        assertEquals("建议批准", opinion.summary());
        assertTrue(opinion.isComplete());

        // 持久化校验：opinion 列被写入且可反序列化回 ApprovalOpinion
        ApprovalRecord reloaded = approvalRepo.findById(record.getId()).orElseThrow();
        assertNotNull(reloaded.getOpinion());
        JsonNode node = objectMapper.readTree(reloaded.getOpinion());
        assertEquals("建议批准", node.get("summary").asText());
        assertEquals("可进入付款流程", node.get("suggestion").asText());
    }

    // ============= 验证-修复循环 =============

    @Test
    @DisplayName("验证-修复: 首轮缺字段 → repair → 次轮合法 → 成功")
    void repairAfterIncompleteFirstAttempt() {
        ExpenseReport report = makeReport(new BigDecimal("3000.00"));
        makeApproval(report);
        // 首轮：suggestion 缺失（isComplete=false）；次轮：合法
        stubClient.enqueue("""
                {"summary":"建议补充材料","reasoning":"大额需核对发票"}
                """);
        stubClient.enqueue("""
                {"summary":"建议补充材料","reasoning":"大额需核对发票原件","suggestion":"请补充发票扫描件"}
                """);

        ApprovalOpinion opinion = service.generateAndSave(report.getId());

        assertTrue(opinion.isComplete());
        assertEquals("请补充发票扫描件", opinion.suggestion());
        // stub 应被消耗 2 次——首轮失败 + 次轮成功
        assertEquals(0, stubClient.remaining());
    }

    @Test
    @DisplayName("验证-修复: 首轮带 Markdown 围栏 → extractJson 抽取后解析成功")
    void repairStripsMarkdownFence() {
        ExpenseReport report = makeReport(new BigDecimal("800.00"));
        makeApproval(report);
        // 模型偶尔会用代码块包裹——extractJson 应能抽出 JSON
        stubClient.enqueue("""
                ```json
                {"summary":"建议批准","reasoning":"小额打车合规","suggestion":"正常付款"}
                ```
                """);

        ApprovalOpinion opinion = service.generateAndSave(report.getId());

        assertTrue(opinion.isComplete());
        assertEquals("建议批准", opinion.summary());
    }

    // ============= 超过重试上限 =============

    @Test
    @DisplayName("超过重试上限仍非法 → 抛 OpinionGenerationException")
    void exhaustsRetriesThrows() {
        ExpenseReport report = makeReport(new BigDecimal("500.00"));
        makeApproval(report);
        // 首轮 + 2 次 repair 全部非法（每次缺不同字段）
        stubClient.enqueue("""
                {"summary":"x","reasoning":"y"}
                """);
        stubClient.enqueue("""
                {"summary":"x","suggestion":"z"}
                """);
        stubClient.enqueue("""
                {"reasoning":"y","suggestion":"z"}
                """);

        OpinionGenerationException ex = assertThrows(OpinionGenerationException.class,
                () -> service.generateAndSave(report.getId()));
        assertEquals("OPINION_MODEL_OUTPUT_INVALID", ex.getCode());
        // 三次尝试全部消耗
        assertEquals(0, stubClient.remaining());
    }

    @Test
    @DisplayName("验证-修复: 首轮完全非法 JSON → repair → 次轮合法 → 成功")
    void repairAfterUnparseableFirstAttempt() {
        ExpenseReport report = makeReport(new BigDecimal("500.00"));
        makeApproval(report);
        // 首轮：纯解释文字，根本不是 JSON；次轮：合法
        stubClient.enqueue("我认为应该批准这笔报销，因为金额合规。");
        stubClient.enqueue("""
                {"summary":"建议批准","reasoning":"金额合规","suggestion":"正常付款"}
                """);

        ApprovalOpinion opinion = service.generateAndSave(report.getId());

        assertTrue(opinion.isComplete());
        assertEquals("建议批准", opinion.summary());
    }

    // ============= 前置条件异常 =============

    @Test
    @DisplayName("报销单不存在 → ExpenseNotFoundException")
    void missingExpenseThrowsNotFound() {
        assertThrows(ExpenseNotFoundException.class, () -> service.generateAndSave(999L));
    }

    @Test
    @DisplayName("报销单无审批记录 → OpinionGenerationException OPINION_NO_APPROVAL_RECORD")
    void noApprovalRecordThrows() {
        ExpenseReport report = makeReport(new BigDecimal("500.00"));
        // 不创建任何 ApprovalRecord

        OpinionGenerationException ex = assertThrows(OpinionGenerationException.class,
                () -> service.generateAndSave(report.getId()));
        assertEquals("OPINION_NO_APPROVAL_RECORD", ex.getCode());
    }

    // ------------------------------------------------------------------
    // 测试夹具
    // ------------------------------------------------------------------

    private ExpenseReport makeReport(BigDecimal amount) {
        ExpenseReport r = new ExpenseReport();
        r.setApplicantId(1L);
        r.setAmount(amount);
        r.setReason("出差打车去客户现场");
        r.setStatus(ExpenseStatus.SUBMITTED);
        return expenseRepo.save(r);
    }

    private ApprovalRecord makeApproval(ExpenseReport report) {
        ApprovalRecord r = new ApprovalRecord();
        r.setExpenseId(report.getId());
        r.setApproverId(2L);
        r.setLevel(ApprovalLevel.MANAGER);
        r.setDecision(ApprovalDecision.APPROVED);
        r.setReason("金额合规");
        return approvalRepo.save(r);
    }

    // ------------------------------------------------------------------
    // 打桩：可编程 OpinionModelClient，覆盖真实的 AnthropicOpinionClient
    // ------------------------------------------------------------------

    @TestConfiguration
    static class OpinionTestConfig {

        @Bean
        @Primary
        StubOpinionModelClient stubOpinionModelClient() {
            return new StubOpinionModelClient();
        }
    }

    /**
     * 队列式打桩：测试预先 enqueue 若干回复，每次 chat() 弹一个，按顺序模拟
     * 「首轮 / repair1 / repair2」。remaining() 用于断言消耗次数。
     */
    static class StubOpinionModelClient implements OpinionModelClient {
        private final Deque<String> replies = new ArrayDeque<>();

        void enqueue(String reply) {
            replies.addLast(reply);
        }

        int remaining() {
            return replies.size();
        }

        @Override
        public String chat(String systemPrompt, String userPrompt) {
            if (replies.isEmpty()) {
                throw new IllegalStateException(
                        "StubOpinionModelClient 队列已空：测试 enqueue 的回复数少于实际调用次数");
            }
            return replies.pollFirst();
        }
    }
}
