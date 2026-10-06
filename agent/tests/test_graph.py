"""graph 等价锚——v1 修复循环逐条搬移（spec §3.2）+ <think> + 第 3 次边界（Review Focus 1/2）。"""
from expense_agent.graph import MAX_REPAIR, build_graph, extract_json, parse_opinion
from expense_agent.llm import FakeLLM

GOOD = '{"summary":"建议批准","reasoning":"事由明确金额合规","suggestion":"同意报销"}'
INCOMPLETE = '{"summary":"建议批准","reasoning":"","suggestion":"同意报销"}'
NOT_JSON = "好的，这是您的意见：抱歉输出乱了"


def _ctx():
    return {"expense": {"id": 1, "applicant_id": 7, "amount": "880.50",
                        "reason": "出差酒店费", "status": "APPROVED"},
            "latest_approval": {"approver_id": 2, "level": "MANAGER",
                                "decision": "APPROVED", "reason": "符合标准"}}


def _run(llm, ctx=None):
    return build_graph(llm).invoke({"context": ctx or _ctx()})


# ---- 等价锚（spec §3.2 表）----

# 锚 1 + Review Focus 2：第 3 次成功不算降级
def test_third_attempt_succeeds_no_fallback():
    llm = FakeLLM([INCOMPLETE, INCOMPLETE, GOOD])
    out = _run(llm)
    assert out["final"] == {"summary": "建议批准", "reasoning": "事由明确金额合规", "suggestion": "同意报销"}
    assert out.get("error") is None
    assert len(llm.calls) == 3
    assert MAX_REPAIR == 2


# 锚 2：extractJson 剥围栏与解释文字
def test_extract_json_strips_fences_and_prose():
    llm = FakeLLM(['```json\n{"summary":"s","reasoning":"r","suggestion":"g"}\n```'])
    out = _run(llm)
    assert out["final"] == {"summary": "s", "reasoning": "r", "suggestion": "g"}


# 锚 3：空白字段算不完整 → repair
def test_validate_blank_field_fails():
    llm = FakeLLM([INCOMPLETE, GOOD])
    out = _run(llm)
    assert out["final"]["summary"] == "建议批准"
    assert "输出字段不完整（存在空字段）" in llm.calls[1][1]  # 回喂进第二轮 user prompt


# 锚 4：回喂措辞原文
def test_repair_hint_wording_preserved():
    llm = FakeLLM([INCOMPLETE, GOOD])
    out = _run(llm)
    second_prompt = llm.calls[1][1]
    assert "【上一轮输出错误】输出字段不完整（存在空字段）: " + INCOMPLETE in second_prompt

    llm2 = FakeLLM([NOT_JSON, GOOD])
    _run(llm2)
    second2 = llm2.calls[1][1]
    assert "【上一轮输出错误】JSON 解析失败 (" in second2
    assert NOT_JSON in second2


# 锚 5：SYSTEM_PROMPT 原文进每轮 system 位
def test_system_prompt_sent_every_call():
    llm = FakeLLM([GOOD])
    _run(llm)
    from expense_agent.prompts import SYSTEM_PROMPT
    assert llm.calls[0][0] == SYSTEM_PROMPT


# 锚 6：穷尽 → fallback（3 次调用后停，不抛异常——降级是图的正常出口）
def test_repair_exhausted_falls_back():
    llm = FakeLLM([INCOMPLETE, INCOMPLETE, INCOMPLETE])
    out = _run(llm)
    assert out["final"] is None
    assert out["error"].startswith("输出字段不完整（存在空字段）")
    assert len(llm.calls) == 3


# 锚 7：LLM 输出为空 → 解析失败 → repair
def test_empty_output_enters_repair():
    llm = FakeLLM(["", GOOD])
    out = _run(llm)
    assert out["final"]["summary"] == "建议批准"
    assert "JSON 解析失败 (模型输出为空): " in llm.calls[1][1]


# ---- Review Focus 1：<think> 剥离先于 extractJson；剥不了的畸形进 repair ----

def test_think_block_stripped_before_extraction():
    llm = FakeLLM(["<think>先想想要点。</think>" + GOOD])
    out = _run(llm)
    assert out["final"]["suggestion"] == "同意报销"


def test_unclosed_think_garbage_json_enters_repair():
    garbage = '<think>只有开头… {"summary": "被思维链污染'
    llm = FakeLLM([garbage, GOOD])
    out = _run(llm)
    assert out["final"]["summary"] == "建议批准"
    assert "JSON 解析失败" in llm.calls[1][1]


# ---- extract_json / parse_opinion 单元锚 ----

def test_extract_json_no_boundary_raises():
    import pytest
    with pytest.raises(ValueError, match="未找到 JSON 对象边界"):
        extract_json("没有任何花括号的普通文本")


def test_parse_opinion_non_object_json_counts_incomplete():
    """v1 语义：readTree 非对象/空对象节点 → 三字段全 null → 不完整路径（非解析错误）。

    注：v1 extractJson 无花括号边界即抛（源码 226-228），故 "[1,2]" 这类
    无边界输入走「JSON 解析失败」而非本路径——一并钉住。
    """
    opinion, err = parse_opinion("{}")
    assert opinion is None
    assert err is not None and err.startswith("输出字段不完整（存在空字段）")

    opinion2, err2 = parse_opinion("[1,2]")
    assert opinion2 is None
    assert err2 is not None and err2.startswith("JSON 解析失败")


def test_container_field_counts_incomplete_and_scalar_renders_json_text():
    """v1 Jackson asText 容器/标量语义（final review Important#1 等价锚补钉）：

    - 嵌套对象/数组：v1 asText() → "" → 空白 → null → 不完整 → repair。
      v2 若 str(dict) 会把 Python repr（单引号 + True）当合法意见放行——
      live 模式模型吐嵌套字段时垃圾直达意见卡且绕过自愈。
    - 非字符串标量：v1 BooleanNode/IntNode asText → "true"/"880"（JSON 文本
      形态），不是 Python 的 "True"。
    """
    opinion, err = parse_opinion(
        '{"summary": {"nested": true}, "reasoning": "r", "suggestion": "s"}')
    assert opinion is None
    assert err is not None and err.startswith("输出字段不完整（存在空字段）")

    opinion2, err2 = parse_opinion(
        '{"summary": ["a"], "reasoning": "r", "suggestion": "s"}')
    assert opinion2 is None
    assert err2 is not None and err2.startswith("输出字段不完整（存在空字段）")

    opinion3, err3 = parse_opinion(
        '{"summary": true, "reasoning": 880, "suggestion": "s"}')
    assert opinion3 is not None and err3 is None
    assert opinion3["summary"] == "true"
    assert opinion3["reasoning"] == "880"
