"""config fail-fast 锚——LLM_MODE 必填 mock|live，禁 env 兜底（suite 铁律）。"""
import pytest

from expense_agent.config import ConfigError, load_settings


def test_mode_missing_raises():
    with pytest.raises(ConfigError, match="LLM_MODE"):
        load_settings({})


def test_mode_invalid_raises():
    with pytest.raises(ConfigError, match="mock\\|live"):
        load_settings({"LLM_MODE": "auto"})


def test_live_requires_full_trio():
    with pytest.raises(ConfigError, match="LLM_BASE_URL"):
        load_settings({"LLM_MODE": "live", "LLM_API_KEY": "sk-x", "LLM_MODEL": "MiniMax-M3"})
    with pytest.raises(ConfigError, match="LLM_API_KEY"):
        load_settings({"LLM_MODE": "live",
                       "LLM_BASE_URL": "https://api.minimaxi.com/v1",
                       "LLM_MODEL": "MiniMax-M3"})
    with pytest.raises(ConfigError, match="LLM_MODEL"):
        load_settings({"LLM_MODE": "live",
                       "LLM_BASE_URL": "https://api.minimaxi.com/v1",
                       "LLM_API_KEY": "sk-x"})


def test_live_ok_defaults_port():
    s = load_settings({"LLM_MODE": "live",
                       "LLM_BASE_URL": "https://api.minimaxi.com/v1",
                       "LLM_API_KEY": "sk-x", "LLM_MODEL": "MiniMax-M3"})
    assert s.mode == "live"
    assert s.agent_port == 8100
    assert s.llm_base_url == "https://api.minimaxi.com/v1"


def test_mock_needs_no_key():
    s = load_settings({"LLM_MODE": "mock"})
    assert s.mode == "mock"
    assert s.llm_api_key is None


def test_agent_port_custom():
    s = load_settings({"LLM_MODE": "mock", "AGENT_PORT": "8200"})
    assert s.agent_port == 8200


def test_agent_port_invalid_raises():
    with pytest.raises(ConfigError, match="AGENT_PORT"):
        load_settings({"LLM_MODE": "mock", "AGENT_PORT": "abc"})
