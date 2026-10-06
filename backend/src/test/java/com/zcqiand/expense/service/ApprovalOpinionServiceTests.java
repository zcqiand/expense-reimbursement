package com.zcqiand.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcqiand.expense.client.OpinionAgentClient;
import com.zcqiand.expense.client.OpinionContext;
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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
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
 * ApprovalOpinionService 测试——v2 thin-client 语义。
 *
 * v1 的验证-修复循环行为锚（缺字段重试 / Markdown 围栏 / 超限抛异常等）已随
 * 内核整体搬入 Python sidecar，由 agent/tests/test_graph.py 的等价锚表逐条
 * 钉住（spec §5 等价门）。本类只测 Spring 侧剩下的编排语义：
 *
 * - happy path：stub 返回合法意见 → 上下文组装正确（spec §11 契约锚）+ 落库
 * - 前置校验：报销单不存在 404 / 无审批记录 409，且都不触达 sidecar
 * - sidecar 422 → modelOutputInvalid，opinion 不落库
 * - sidecar 不可达 → agentUnavailable（RF3：@Transactional 内不写半截数据）
 *
 * stub 手法沿 v1：@TestConfiguration + @Primary 覆盖真实 AgentSidecarClient，
 * 队列式出队——ApprovalOpinion 即返回、RuntimeException 即抛、空队列自曝漏桩。
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
        // 测试不打真实 sidecar：base-url 指向本地不监听端口（stub @Primary 接管）
        "app.agent.base-url=http://127.0.0.1:8806"
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

    @Autowired
    private StubOpinionAgentClient stub;

    @BeforeEach
    void resetStub() {
        // stub 是缓存上下文里的单例：@Transactional 只回滚 DB，不重置内存态——
        // 不清则 queue/calls 计数断言会踩前序测试的残留
        stub.queue.clear();
        stub.calls.clear();
    }

    // ============= happy path + 契约锚 =============

    @Test
    @DisplayName("happy path: stub 返回合法意见 → 上下文映射正确（spec §11）+ 持久化到 ApprovalRecord.opinion")
    void happyPathPersistsOpinionAndMapsContext() throws Exception {
        ExpenseReport report = makeReport(new BigDecimal("500.00"));
        ApprovalRecord record = makeApproval(report);
        stub.queue.addLast(new ApprovalOpinion("建议批准", "金额合规且事由清晰", "可进入付款流程"));

        ApprovalOpinion opinion = service.generateAndSave(report.getId());

        // spec §11 契约锚：Java record ↔ sidecar pydantic 双侧 schema
        assertEquals(1, stub.calls.size());
        OpinionContext ctx = stub.calls.get(0);
        assertEquals(report.getId(), ctx.expense().id());
        assertEquals(1L, ctx.expense().applicantId());
        assertEquals("500.00", ctx.expense().amount());        // BigDecimal → toPlainString
        assertEquals("出差打车去客户现场", ctx.expense().reason());
        assertEquals("SUBMITTED", ctx.expense().status());     // enum → name()
        assertEquals(2L, ctx.latestApproval().approverId());
        assertEquals("MANAGER", ctx.latestApproval().level());
        assertEquals("APPROVED", ctx.latestApproval().decision());
        assertEquals("金额合规", ctx.latestApproval().reason());

        assertTrue(opinion.isComplete());

        // 持久化校验：opinion 列被写入且可反序列化回 ApprovalOpinion
        ApprovalRecord reloaded = approvalRepo.findById(record.getId()).orElseThrow();
        assertNotNull(reloaded.getOpinion());
        JsonNode node = objectMapper.readTree(reloaded.getOpinion());
        assertEquals("建议批准", node.get("summary").asText());
        assertEquals("可进入付款流程", node.get("suggestion").asText());
    }

    // ============= 前置条件异常（先于 sidecar 调用） =============

    @Test
    @DisplayName("报销单不存在 → ExpenseNotFoundException，且不触达 sidecar")
    void missingExpenseThrowsNotFound() {
        assertThrows(ExpenseNotFoundException.class, () -> service.generateAndSave(999L));
        assertEquals(0, stub.calls.size());
    }

    @Test
    @DisplayName("报销单无审批记录 → OPINION_NO_APPROVAL_RECORD，且不触达 sidecar")
    void noApprovalRecordThrows() {
        ExpenseReport report = makeReport(new BigDecimal("500.00"));
        // 不创建任何 ApprovalRecord

        OpinionGenerationException ex = assertThrows(OpinionGenerationException.class,
                () -> service.generateAndSave(report.getId()));

        assertEquals("OPINION_NO_APPROVAL_RECORD", ex.getCode());
        assertEquals(0, stub.calls.size());
    }

    // ============= sidecar 失败映射（422 / 不可达） =============

    @Test
    @DisplayName("sidecar 422 → OPINION_MODEL_OUTPUT_INVALID，opinion 不落库")
    void agentInvalidOutputMapsTo409AndWritesNothing() {
        ExpenseReport report = makeReport(new BigDecimal("500.00"));
        ApprovalRecord record = makeApproval(report);
        stub.queue.addLast(OpinionGenerationException.modelOutputInvalid(
                "输出字段不完整（存在空字段）: {}"));

        OpinionGenerationException ex = assertThrows(OpinionGenerationException.class,
                () -> service.generateAndSave(report.getId()));

        assertEquals("OPINION_MODEL_OUTPUT_INVALID", ex.getCode());
        assertNull(approvalRepo.findById(record.getId()).orElseThrow().getOpinion());
    }

    @Test
    @DisplayName("sidecar 不可达 → OPINION_AGENT_UNAVAILABLE（409 形状），opinion 不留半截（RF3）")
    void agentUnavailableMapsTo409AndRollsBackWrite() {
        ExpenseReport report = makeReport(new BigDecimal("500.00"));
        ApprovalRecord record = makeApproval(report);
        stub.queue.addLast(OpinionGenerationException.agentUnavailable("Connection refused"));

        OpinionGenerationException ex = assertThrows(OpinionGenerationException.class,
                () -> service.generateAndSave(report.getId()));

        assertEquals("OPINION_AGENT_UNAVAILABLE", ex.getCode());
        assertTrue(ex.getMessage().contains("审批 Agent 暂不可用"));
        // 回滚锚：落库未发生——ApprovalRecord.opinion 不写半截数据
        assertNull(approvalRepo.findById(record.getId()).orElseThrow().getOpinion());
    }

    // ------------------------------------------------------------------
    // 测试夹具（照 v1）
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
    // 打桩：可编程 OpinionAgentClient，覆盖真实 AgentSidecarClient
    // ------------------------------------------------------------------

    @TestConfiguration
    static class OpinionTestConfig {

        @Bean
        @Primary
        StubOpinionAgentClient stubOpinionAgentClient() {
            return new StubOpinionAgentClient();
        }
    }

    /**
     * 队列式打桩：出队元素是 ApprovalOpinion 即返回，是 RuntimeException 即抛，
     * 空队列抛 NoSuchElementException 自曝「测试漏了出队注入」。
     * calls 记录每次收到的上下文——前置校验类测试用它断言「没触达 sidecar」。
     */
    static class StubOpinionAgentClient implements OpinionAgentClient {
        final Deque<Object> queue = new ArrayDeque<>();
        final List<OpinionContext> calls = new ArrayList<>();

        @Override
        public ApprovalOpinion generate(OpinionContext context) {
            calls.add(context);
            Object next = queue.poll();
            if (next instanceof ApprovalOpinion opinion) {
                return opinion;
            }
            if (next instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new NoSuchElementException("StubOpinionAgentClient 队列已空：测试漏了出队注入");
        }
    }
}
