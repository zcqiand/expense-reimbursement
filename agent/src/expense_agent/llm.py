"""LLM 客户端层——家族口径：LLM_MODE 分流，OpenAI 兼容直调（MiniMax-M3）。

MiniMax-M3 会在正文前内联 <think>…</think> 思维链（记忆 minimax-m3-integration-pitfalls），
必须在 JSON 抽取之前剥离——strip_think() 是唯一剥离点，graph.validate 调它。
"""
from __future__ import annotations

import json
import re

from openai import OpenAI

from .config import ConfigError, Settings

_THINK_RE = re.compile(r"<think>.*?</think>", re.DOTALL)


def strip_think(text: str) -> str:
    """剥成对的 <think>…</think>；无 <think> 原样返回（仅去首尾空白）。

    未闭合的残缺思维链不剥——交给 extractJson/解析失败进 repair，
    这是 v1 已有的容错路径（Review Focus 1 的畸形分支）。
    """
    return _THINK_RE.sub("", text).strip()


class LiveLLM:
    """OpenAI 兼容直调（api.minimaxi.com/v1 + MiniMax-M3，base_url/model 可配）。"""

    def __init__(self, settings: Settings):
        if settings.mode != "live":
            raise ConfigError("LiveLLM 只能在 LLM_MODE=live 下构造")
        self._client = OpenAI(base_url=settings.llm_base_url, api_key=settings.llm_api_key)
        self._model = settings.llm_model

    def chat(self, system: str, user: str) -> str:
        resp = self._client.chat.completions.create(
            model=self._model,
            messages=[{"role": "system", "content": system},
                      {"role": "user", "content": user}],
            temperature=0.2,
        )
        return resp.choices[0].message.content or ""


class MockLLM:
    """离线演示：固定合法意见 JSON——mock 全链无 Key（Review Focus 5）。"""

    OPINION = {
        "summary": "该笔报销单据齐全、金额在标准内，建议予以通过。",
        "reasoning": "报销事由明确，金额与提交凭证一致，未见超标或重复报销迹象；"
                     "审批链符合公司分级授权。",
        "suggestion": "同意报销。建议财务按标准流程付款，并将本次票据归档备查。",
    }

    def chat(self, system: str, user: str) -> str:
        return json.dumps(self.OPINION, ensure_ascii=False)


class FakeLLM:
    """pytest 脚本化序列：turns 依次弹出；BaseException 项即抛（注错用）。"""

    def __init__(self, turns: list[str | BaseException]):
        self.turns = list(turns)
        self.calls: list[tuple[str, str]] = []

    def chat(self, system: str, user: str) -> str:
        self.calls.append((system, user))
        turn = self.turns.pop(0)
        if isinstance(turn, BaseException):
            raise turn
        return turn


def build_llm(settings: Settings):
    """LLM_MODE 分流唯一入口（app 装配用）。"""
    if settings.mode == "mock":
        return MockLLM()
    return LiveLLM(settings)
