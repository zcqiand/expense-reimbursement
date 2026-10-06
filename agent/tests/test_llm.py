"""LLM 层锚——<think> 剥离（Review Focus 1）+ 双件 + mode 分流。"""
import json

import pytest

from expense_agent.config import ConfigError, Settings
from expense_agent.llm import FakeLLM, LiveLLM, MockLLM, build_llm, strip_think

GOOD = '{"summary":"s","reasoning":"r","suggestion":"g"}'


def _settings(mode="mock", **kw):
    return Settings(mode=mode, llm_base_url=kw.get("base_url"),
                    llm_api_key=kw.get("api_key"), llm_model=kw.get("model"),
                    agent_port=8100)


def test_strip_think_removes_paired_block():
    raw = "<think>用户报了一笔 880 元差旅……先对 schema。</think>" + GOOD
    assert strip_think(raw) == GOOD


def test_strip_think_passthrough_without_think():
    assert strip_think(GOOD) == GOOD


def test_strip_think_unclosed_left_intact_enters_repair_path():
    """未闭合 <think> 不剥——残留文本交给 extractJson/解析失败 → repair（Review Focus 1）。"""
    raw = '<think>只有开头…… {"summary": "被思维链污染'
    assert strip_think(raw) == raw.strip()


def test_mock_llm_returns_valid_opinion_json():
    opinion = json.loads(MockLLM().chat("sys", "user"))
    assert set(opinion) == {"summary", "reasoning", "suggestion"}
    assert all(isinstance(v, str) and v.strip() for v in opinion.values())


def test_fake_llm_sequence_records_calls_and_raises():
    fake = FakeLLM([GOOD, RuntimeError("LLM 崩了")])
    assert fake.chat("sys1", "u1") == GOOD
    with pytest.raises(RuntimeError, match="LLM 崩了"):
        fake.chat("sys2", "u2")
    assert fake.calls == [("sys1", "u1"), ("sys2", "u2")]


def test_live_llm_rejects_mock_settings():
    with pytest.raises(ConfigError):
        LiveLLM(_settings("mock"))


def test_build_llm_dispatch_by_mode():
    assert isinstance(build_llm(_settings("mock")), MockLLM)
    assert isinstance(build_llm(_settings("live", base_url="https://api.minimaxi.com/v1",
                                             api_key="sk-x", model="MiniMax-M3")), LiveLLM)
