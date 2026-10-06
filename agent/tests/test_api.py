"""API 锚——家族探活契约 + mock 全链（Review Focus 5）+ 双侧 schema 锚（spec §11）+ 400/422。"""
from fastapi.testclient import TestClient

from expense_agent.app import ApprovalCtx, ExpenseCtx, OpinionRequest, create_app
from expense_agent.config import Settings

BODY = {
    "expense": {"id": 1, "applicant_id": 7, "amount": "880.50",
                "reason": "出差酒店费", "status": "APPROVED"},
    "latest_approval": {"approver_id": 2, "level": "MANAGER",
                        "decision": "APPROVED", "reason": "符合标准"},
}


def _settings():
    return Settings(mode="mock", llm_base_url=None, llm_api_key=None,
                    llm_model=None, agent_port=8100)


def _client():
    return TestClient(create_app(_settings()))


def test_health_family_contract():
    resp = _client().get("/api/health")
    assert resp.status_code == 200
    assert resp.json() == {"ok": True, "mode": "mock"}


def test_opinions_mock_returns_three_parts():
    """mock 全链：无 Key POST → 200 三段（Review Focus 5 的 app 层锚）。"""
    resp = _client().post("/api/opinions", json=BODY)
    assert resp.status_code == 200
    body = resp.json()
    assert set(body) == {"summary", "reasoning", "suggestion"}
    assert all(isinstance(body[k], str) and body[k] for k in body)


def test_request_model_field_names_match_java_record():
    """spec §11 契约锚：pydantic 字段 = Java OpinionContext @JsonProperty 名（snake_case）。"""
    assert set(OpinionRequest.model_fields) == {"expense", "latest_approval"}
    assert set(ExpenseCtx.model_fields) == {"id", "applicant_id", "amount", "reason", "status"}
    assert set(ApprovalCtx.model_fields) == {"approver_id", "level", "decision", "reason"}


def test_missing_field_returns_400_invalid_request():
    bad = {"expense": {"id": 1}}  # 缺 latest_approval 与 expense 多数字段
    resp = _client().post("/api/opinions", json=bad)
    assert resp.status_code == 400
    assert resp.json()["error"] == "invalid_request"


def test_422_body_shape_when_model_exhausted():
    """穷尽降级 → 422 {"error","detail"}（Spring 端 mapHttpFailure 消费此形状）。"""
    from expense_agent.llm import FakeLLM

    llm = FakeLLM(["坏输出", "坏输出", "坏输出"])  # 构造期注入（create_app 的测试缝）
    resp = TestClient(create_app(_settings(), llm=llm)).post("/api/opinions", json=BODY)
    assert resp.status_code == 422
    body = resp.json()
    assert body["error"] == "model_output_invalid"
    assert "detail" in body
