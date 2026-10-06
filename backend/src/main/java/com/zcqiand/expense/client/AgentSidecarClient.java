package com.zcqiand.expense.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcqiand.expense.dto.ApprovalOpinion;
import com.zcqiand.expense.exception.OpinionGenerationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * OpinionAgentClient 的 HTTP 实现——调 Python sidecar 的 POST /api/opinions。
 *
 * 超时（spec §4.3）：connect 5s / read 30s——意见生成是同步等待用户点击的
 * 操作，超时上限即用户最长等待；超时与不可达/5xx/响应形状错统一映射成
 * agentUnavailable（409 形状，Review Focus 4：不让 504 直穿前端）。
 *
 * base-url 无默认值（${AGENT_BASE_URL}）——env 缺失 Spring 启动即失败，
 * 禁止 env 兜底（suite 铁律）。
 */
@Component
public class AgentSidecarClient implements OpinionAgentClient {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /** Spring 主构造器——多 ctor 场景必须显式指定注入入口（否则退回无参 ctor 报错）。 */
    @Autowired
    public AgentSidecarClient(RestClient.Builder builder,
                              ObjectMapper objectMapper,
                              @Value("${app.agent.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    /** 包私有：测试用 MockRestServiceServer 绑定的 RestClient 注入（不起 Spring）。 */
    AgentSidecarClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ApprovalOpinion generate(OpinionContext context) {
        try {
            String body = restClient.post()
                    .uri("/api/opinions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(context)
                    .retrieve()
                    .body(String.class);
            return toOpinion(body);
        } catch (RestClientResponseException e) {
            throw mapHttpFailure(e);
        } catch (ResourceAccessException e) {
            // connect/read 超时与拒连同款 409 形状（Review Focus 4）
            throw OpinionGenerationException.agentUnavailable(e.getMessage());
        }
    }

    /** 200 响应体 → 三段意见；body 非 JSON/缺字段走 agentUnavailable（降级体面收口）。 */
    private ApprovalOpinion toOpinion(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return new ApprovalOpinion(
                    textOrNull(node, "summary"),
                    textOrNull(node, "reasoning"),
                    textOrNull(node, "suggestion"));
        } catch (Exception e) {
            throw OpinionGenerationException.agentUnavailable(
                    "agent 响应格式错误: " + e.getMessage());
        }
    }

    private OpinionGenerationException mapHttpFailure(RestClientResponseException e) {
        String detail = e.getResponseBodyAsString();
        try {
            JsonNode node = objectMapper.readTree(detail);
            if (node.hasNonNull("detail")) {
                detail = node.get("detail").asText();
            }
        } catch (Exception ignored) {
            // 非 JSON 错误体——原文作 detail
        }
        if (e.getStatusCode().value() == 422) {
            return OpinionGenerationException.modelOutputInvalid(detail);
        }
        return OpinionGenerationException.agentUnavailable(
                "HTTP " + e.getStatusCode().value() + ": " + detail);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String text = child.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
