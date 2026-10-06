"""prompts 锚——SYSTEM_PROMPT/OPINION_SCHEMA 原文搬移 + build_user_prompt 对照 v1 措辞。"""
import json

from expense_agent.prompts import OPINION_SCHEMA, SYSTEM_PROMPT, build_user_prompt


def test_system_prompt_embeds_schema_and_key_phrases():
    assert OPINION_SCHEMA in SYSTEM_PROMPT
    for phrase in ("严谨的企业财务审批助手", "只输出一个 JSON 对象",
                   "错误信息会作为新输入回传"):
        assert phrase in SYSTEM_PROMPT
    schema = json.loads(OPINION_SCHEMA)
    assert schema["required"] == ["summary", "reasoning", "suggestion"]
    assert schema["additionalProperties"] is False
    for f in ("summary", "reasoning", "suggestion"):
        assert schema["properties"][f]["minLength"] == 1


def test_first_prompt_matches_v1_wording():
    p = build_user_prompt(_ctx())
    for line in ("报销单信息：", "- 报销单 ID: 1", "- 申请人 ID: 7",
                 "- 金额: 880.50", "- 事由: 出差酒店费", "- 当前状态: APPROVED",
                 "最新审批记录：", "- 审批人 ID: 2", "- 审批级别: MANAGER",
                 "- 审批决定: APPROVED", "- 审批理由: 符合标准",
                 "请按系统提示中的 JSON Schema 输出审批意见。"):
        assert line in p
    assert "上一轮输出错误" not in p


def test_repair_prompt_appends_v1_hint():
    p = build_user_prompt(_ctx(), "输出字段不完整（存在空字段）: {}")
    assert "【上一轮输出错误】输出字段不完整（存在空字段）: {}" in p
    assert "请严格按 JSON Schema 重新输出，不要重复错误。" in p
    assert "- 报销单 ID: 1" in p  # 完整上下文仍在（v1 是整段重构造，非只发错误）


def test_null_reason_renders_placeholder():
    ctx = _ctx()
    ctx["expense"]["reason"] = None
    ctx["latest_approval"]["reason"] = None
    p = build_user_prompt(ctx)
    assert "- 事由: (无)" in p and "- 审批理由: (无)" in p


def _ctx():
    return {"expense": {"id": 1, "applicant_id": 7, "amount": "880.50",
                        "reason": "出差酒店费", "status": "APPROVED"},
            "latest_approval": {"approver_id": 2, "level": "MANAGER",
                                "decision": "APPROVED", "reason": "符合标准"}}
