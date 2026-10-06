"""python -m expense_agent——uvicorn 启动入口（容器 CMD 指向这里）。"""
from __future__ import annotations

import uvicorn

from .app import create_app
from .config import load_settings


def main() -> None:
    settings = load_settings()
    create_app(settings)  # 构造即 fail-fast（LLM_MODE/live 三件套在此校验）
    uvicorn.run(
        "expense_agent.app:create_app",
        factory=True,
        host="0.0.0.0",
        port=settings.agent_port,
    )


if __name__ == "__main__":
    main()
