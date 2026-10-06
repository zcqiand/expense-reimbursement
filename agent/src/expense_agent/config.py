"""环境配置——LLM_MODE 必填 mock|live，fail-fast（禁 env 兜底，suite 铁律）。"""
from __future__ import annotations

import os
from dataclasses import dataclass


class ConfigError(RuntimeError):
    """配置缺失或非法——启动即失败，不留默认值兜底。"""


@dataclass(frozen=True)
class Settings:
    mode: str                 # "mock" | "live"
    llm_base_url: str | None  # live 三件套
    llm_api_key: str | None
    llm_model: str | None
    agent_port: int


def load_settings(env: dict[str, str] | None = None) -> Settings:
    """从 env dict（缺省 os.environ）读配置；缺失/非法即 ConfigError。"""
    env = dict(os.environ if env is None else env)

    mode = env.get("LLM_MODE")
    if mode not in ("mock", "live"):
        raise ConfigError(
            f"LLM_MODE 必填且只能是 mock|live，当前值: {mode!r}"
            "（禁止 env 默认值兜底——显式声明后再启动）"
        )

    base_url = env.get("LLM_BASE_URL")
    api_key = env.get("LLM_API_KEY")
    model = env.get("LLM_MODEL")
    if mode == "live" and not (base_url and api_key and model):
        raise ConfigError(
            "LLM_MODE=live 需要 LLM_BASE_URL / LLM_API_KEY / LLM_MODEL 三件套齐全 "
            f"(base_url={bool(base_url)} api_key={bool(api_key)} model={bool(model)})"
        )

    port_raw = env.get("AGENT_PORT", "8100")
    try:
        agent_port = int(port_raw)
    except ValueError as ex:
        raise ConfigError(f"AGENT_PORT 必须是整数，当前值: {port_raw!r}") from ex

    return Settings(mode=mode, llm_base_url=base_url, llm_api_key=api_key,
                    llm_model=model, agent_port=agent_port)
