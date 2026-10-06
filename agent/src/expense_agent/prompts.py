"""prompt 原文——v1 Java（ApprovalOpinionService）逐字搬移；教学口径：schema 内嵌 + 错误回喂。"""
from __future__ import annotations

OPINION_SCHEMA = """\
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
"""

SYSTEM_PROMPT = """\
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

若之前的输出有错误，错误信息会作为新输入回传你，请据此修正后
重新输出严格符合 Schema 的 JSON。
"""


def build_user_prompt(context: dict, repair_hint: str | None = None) -> str:
    """v1 buildUserPrompt 等价——报销单信息 + 最新审批记录 +（可选）修复回喂。

    字段行序与措辞逐字对齐 v1 Java；null reason → "(无)" 占位。
    """
    expense = context["expense"]
    approval = context["latest_approval"]
    lines = [
        "报销单信息：",
        f"- 报销单 ID: {expense['id']}",
        f"- 申请人 ID: {expense['applicant_id']}",
        f"- 金额: {expense['amount']}",
        f"- 事由: {expense['reason'] if expense['reason'] is not None else '(无)'}",
        f"- 当前状态: {expense['status']}",
        "",
        "最新审批记录：",
        f"- 审批人 ID: {approval['approver_id']}",
        f"- 审批级别: {approval['level']}",
        f"- 审批决定: {approval['decision']}",
        f"- 审批理由: {approval['reason'] if approval['reason'] is not None else '(无)'}",
    ]
    if repair_hint:
        lines += ["", f"【上一轮输出错误】{repair_hint}",
                  "请严格按 JSON Schema 重新输出，不要重复错误。"]
    else:
        lines += ["", "请按系统提示中的 JSON Schema 输出审批意见。"]
    return "\n".join(lines)
