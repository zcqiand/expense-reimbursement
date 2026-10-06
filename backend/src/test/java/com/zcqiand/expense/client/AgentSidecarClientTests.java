package com.zcqiand.expense.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcqiand.expense.dto.ApprovalOpinion;
import com.zcqiand.expense.exception.OpinionGenerationException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * AgentSidecarClient 测试——纯 JUnit（不起 Spring 上下文）。
 *
 * MockRestServiceServer 绑定 RestClient.Builder，逐条钉住 HTTP 失败映射
 * （spec §4.3 + Review Focus 3/4 的客户端半边）：
 * - 422 model_output_invalid → modelOutputInvalid（同款 409 形状语义）
 * - 5xx / 连接拒绝 / 读超时 / 200 但 body 非 JSON → agentUnavailable（409 形状）
 * 请求契约（snake_case 字段名）与 sidecar pydantic 模型一一对应（spec §11 双侧锚）。
 */
class AgentSidecarClientTests {

    private MockRestServiceServer server;
    private AgentSidecarClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AgentSidecarClient(builder.build(), new ObjectMapper());
    }

    private OpinionContext ctx() {
        return new OpinionContext(
                new OpinionContext.Expense(1L, 7L, "880.50", "差旅报销", "APPROVED"),
                new OpinionContext.Approval(2L, "MANAGER", "APPROVED", "同意"));
    }

    @Test
    @DisplayName("请求契约：POST /api/opinions + snake_case 字段名与 sidecar pydantic 一一对应（spec §11）")
    void requestContractSendsSnakeCaseContext() {
        server.expect(requestTo("/api/opinions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.expense.id").value(1))
                .andExpect(jsonPath("$.expense.applicant_id").value(7))
                .andExpect(jsonPath("$.expense.amount").value("880.50"))
                .andExpect(jsonPath("$.expense.status").value("APPROVED"))
                .andExpect(jsonPath("$.latest_approval.approver_id").value(2))
                .andExpect(jsonPath("$.latest_approval.level").value("MANAGER"))
                .andExpect(jsonPath("$.latest_approval.decision").value("APPROVED"))
                .andRespond(withSuccess(
                        "{\"summary\":\"s\",\"reasoning\":\"r\",\"suggestion\":\"g\"}",
                        MediaType.APPLICATION_JSON));

        ApprovalOpinion opinion = client.generate(ctx());

        assertEquals("s", opinion.summary());
        assertEquals("r", opinion.reasoning());
        assertEquals("g", opinion.suggestion());
        server.verify();
    }

    @Test
    @DisplayName("sidecar 422 model_output_invalid → modelOutputInvalid（detail 从 body 读取）")
    void maps422ToModelOutputInvalid() {
        server.expect(requestTo("/api/opinions"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"error\":\"model_output_invalid\",\"detail\":\"JSON 解析失败 (xxx)\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        OpinionGenerationException ex = assertThrows(OpinionGenerationException.class,
                () -> client.generate(ctx()));

        assertEquals("OPINION_MODEL_OUTPUT_INVALID", ex.getCode());
        assertTrue(ex.getMessage().contains("JSON 解析失败 (xxx)"));
        server.verify();
    }

    @Test
    @DisplayName("sidecar 5xx → agentUnavailable（409 形状，不直穿）")
    void maps5xxToAgentUnavailable() {
        server.expect(requestTo("/api/opinions"))
                .andRespond(withServerError());

        OpinionGenerationException ex = assertThrows(OpinionGenerationException.class,
                () -> client.generate(ctx()));

        assertEquals("OPINION_AGENT_UNAVAILABLE", ex.getCode());
        assertTrue(ex.getMessage().contains("审批 Agent 暂不可用"));
        server.verify();
    }

    @Test
    @DisplayName("连接拒绝 → agentUnavailable（Review Focus 3 的客户端半边）")
    void mapsConnectionFailureToAgentUnavailable() {
        server.expect(requestTo("/api/opinions"))
                .andRespond(withException(new ConnectException("Connection refused")));

        OpinionGenerationException ex = assertThrows(OpinionGenerationException.class,
                () -> client.generate(ctx()));

        assertEquals("OPINION_AGENT_UNAVAILABLE", ex.getCode());
        server.verify();
    }

    @Test
    @DisplayName("read 超时 → 同款 409 agentUnavailable 形状，不 504 直穿（Review Focus 4）")
    void readTimeoutKeeps409AgentUnavailableShape() {
        server.expect(requestTo("/api/opinions"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        OpinionGenerationException ex = assertThrows(OpinionGenerationException.class,
                () -> client.generate(ctx()));

        assertEquals("OPINION_AGENT_UNAVAILABLE", ex.getCode());
        assertTrue(ex.getMessage().contains("审批 Agent 暂不可用"));
        server.verify();
    }

    @Test
    @DisplayName("200 但 body 非 JSON → agentUnavailable（不许 NPE 直穿前端）")
    void nonJson200BodyMapsToAgentUnavailable() {
        server.expect(requestTo("/api/opinions"))
                .andRespond(withSuccess("<html>gateway</html>", MediaType.TEXT_HTML));

        OpinionGenerationException ex = assertThrows(OpinionGenerationException.class,
                () -> client.generate(ctx()));

        assertEquals("OPINION_AGENT_UNAVAILABLE", ex.getCode());
        assertNotNull(ex.getMessage());
        server.verify();
    }
}
