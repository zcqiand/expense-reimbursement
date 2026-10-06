package com.zcqiand.expense.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 发给 sidecar 的结构化上下文（spec §3.4 请求体）——字段名即 wire 契约。
 *
 * @JsonProperty 把 Java camelCase 钉死成 sidecar pydantic 模型的 snake_case，
 * 防字段映射漂移（spec §11 风险，AgentSidecarClientTests.requestContractSendsSnakeCaseContext
 * 与 sidecar tests/test_api.py::test_request_model_field_names_match_java_record 双侧锚定）。
 *
 * amount 用字符串透传（BigDecimal.toPlainString()），防 JSON 数字精度漂移；
 * reason 可为 null（sidecar 侧渲染「(无)」，对齐 v1 buildUserPrompt 占位语义）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpinionContext(
        @JsonProperty("expense") Expense expense,
        @JsonProperty("latest_approval") Approval latestApproval) {

    public record Expense(
            @JsonProperty("id") Long id,
            @JsonProperty("applicant_id") Long applicantId,
            @JsonProperty("amount") String amount,
            @JsonProperty("reason") String reason,
            @JsonProperty("status") String status) {
    }

    public record Approval(
            @JsonProperty("approver_id") Long approverId,
            @JsonProperty("level") String level,
            @JsonProperty("decision") String decision,
            @JsonProperty("reason") String reason) {
    }
}
