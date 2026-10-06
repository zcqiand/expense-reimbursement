"""LangGraph 生成图——assemble → draft → validate →(条件边) finish / repair / fallback。

等价锚（v1 ApprovalOpinionService 逐条搬移，spec §3.2）：
- 总 LLM 调用 ≤ 3（首轮 + MAX_REPAIR=2 次修复）；第 3 次成功不算降级
- extractJson：第一个 { 到最后一个 }
- isComplete：summary/reasoning/suggestion 三字段非空白
- 回喂措辞原文：「输出字段不完整（存在空字段）: <raw>」/「JSON 解析失败 (<msg>): <raw>」
- 穷尽 → fallback（error=lastError，HTTP 422）
无 checkpointer（家族纪律）；图是无状态纯函数，上下文随请求自携。
"""
from __future__ import annotations

import json
from typing import Any, TypedDict

from langgraph.graph import END, StateGraph

from .llm import strip_think
from .prompts import SYSTEM_PROMPT, build_user_prompt

MAX_REPAIR = 2  # v1 同值：含首轮共 1 + MAX_REPAIR 次 LLM 调用


class GraphState(TypedDict, total=False):
    context: dict            # {expense: {...}, latest_approval: {...}}
    user_prompt: str         # 本轮 user prompt（repair 轮会重构造）
    draft_raw: str           # 最近一次 LLM 原始输出（含 <think>）
    attempts: int            # 已发生的 LLM 调用数
    last_error: str | None   # 最近一次不合规原因（回喂措辞）
    opinion: dict | None     # validate 通过的三段意见
    final: dict | None       # finish 出口（成功路径）
    error: str | None        # fallback 出口（降级路径，HTTP 422 detail）


def build_graph(llm):
    """llm: 提供 chat(system, user) -> str 的对象（LiveLLM/MockLLM/FakeLLM）。"""

    def assemble(state: GraphState) -> dict:
        return {"user_prompt": build_user_prompt(state["context"]), "attempts": 0}

    def draft(state: GraphState) -> dict:
        raw = llm.chat(SYSTEM_PROMPT, state["user_prompt"])
        return {"draft_raw": raw, "attempts": state.get("attempts", 0) + 1}

    def validate(state: GraphState) -> dict:
        raw = strip_think(state["draft_raw"])     # <think> 剥离先于 extractJson
        opinion, last_error = parse_opinion(raw)
        update: dict[str, Any] = {"last_error": last_error}
        if opinion is not None:
            update["opinion"] = opinion
        return update

    def _after_validate(state: GraphState) -> str:
        if state.get("opinion") is not None:
            return "pass"
        if state.get("attempts", 0) <= MAX_REPAIR:
            return "repair"
        return "fallback"

    def repair(state: GraphState) -> dict:
        # v1 语义：修复轮整段重构造 prompt（上下文 + 回喂错误）
        return {"user_prompt": build_user_prompt(state["context"], state["last_error"])}

    def finish(state: GraphState) -> dict:
        return {"final": state["opinion"]}

    def fallback(state: GraphState) -> dict:
        # final 显式置 None：降级出口与成功出口共用同一键，消费方 get("final") 单点判定
        return {"final": None, "error": state["last_error"] or "模型输出不合规"}

    graph = StateGraph(GraphState)
    graph.add_node("assemble", assemble)
    graph.add_node("draft", draft)
    graph.add_node("validate", validate)
    graph.add_node("repair", repair)
    graph.add_node("finish", finish)
    graph.add_node("fallback", fallback)
    graph.set_entry_point("assemble")
    graph.add_edge("assemble", "draft")
    graph.add_edge("draft", "validate")
    graph.add_conditional_edges("validate", _after_validate,
                                {"pass": "finish", "repair": "repair", "fallback": "fallback"})
    graph.add_edge("repair", "draft")
    graph.add_edge("finish", END)
    graph.add_edge("fallback", END)
    return graph.compile()


def parse_opinion(raw: str) -> tuple[dict | None, str | None]:
    """v1 parse() 等价：extractJson + 三字段提取，返回 (opinion, last_error)。

    返回 (opinion, None) 或 (None, 回喂措辞)——措辞模板原文同 v1；
    <msg> 为 Python 异常消息（语言固有差异，Global Constraints 裁定不锚消息体）。
    """
    if not raw or not raw.strip():
        return None, "JSON 解析失败 (模型输出为空): " + raw
    try:
        json_text = extract_json(raw)
    except ValueError as ex:
        return None, f"JSON 解析失败 ({ex}): {raw}"
    try:
        node = json.loads(json_text)
    except ValueError as ex:
        return None, f"JSON 解析失败 ({ex}): {raw}"
    fields = [_text_or_none(node, f) for f in ("summary", "reasoning", "suggestion")]
    if all(fields):
        return {"summary": fields[0], "reasoning": fields[1],
                "suggestion": fields[2]}, None
    return None, "输出字段不完整（存在空字段）: " + raw


def extract_json(raw: str) -> str:
    """v1 extractJson 等价：第一个 { 到最后一个 }；无边界即失败。"""
    start = raw.find("{")
    end = raw.rfind("}")
    if start < 0 or end < 0 or end <= start:
        raise ValueError("未找到 JSON 对象边界")
    return raw[start:end + 1]


def _text_or_none(node: Any, field: str) -> str | None:
    """v1 textOrNull 等价：缺失/null/空白串 → None；非对象节点全 None。

    容器语义对齐 v1 Jackson asText()：对象/数组 → "" → 空白 → null
    （进 repair，不放行 Python repr 垃圾——final review Important#1）；
    非字符串标量按 JSON 文本形态渲染（true/880，非 True/880 的 Python 形态）。
    """
    if not isinstance(node, dict):
        return None
    value = node.get(field)
    if value is None or isinstance(value, (dict, list)):
        return None
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
    return text if text.strip() else None
