"""FastAPI app——GET /api/health（家族探活契约）+ POST /api/opinions（同步 JSON，无 SSE）。"""
from __future__ import annotations

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from .config import Settings, load_settings
from .graph import build_graph
from .llm import build_llm


class ExpenseCtx(BaseModel):
    id: int
    applicant_id: int
    amount: str          # BigDecimal 以字符串透传（Java toPlainString()），防精度漂移
    reason: str | None = None
    status: str


class ApprovalCtx(BaseModel):
    approver_id: int
    level: str
    decision: str
    reason: str | None = None


class OpinionRequest(BaseModel):
    expense: ExpenseCtx
    latest_approval: ApprovalCtx


def create_app(settings: Settings | None = None, llm=None) -> FastAPI:
    """llm 参数是测试缝（注入 FakeLLM 钉 422 形状）；生产传 None 走 build_llm。"""
    settings = settings or load_settings()
    llm = llm or build_llm(settings)
    graph = build_graph(llm)
    app = FastAPI(title="expense-agent", version="2.0.0")

    @app.exception_handler(RequestValidationError)
    async def validation_handler(request, exc):
        # 请求体 schema 错 = Spring 组装 bug（非模型问题）→ 400 invalid_request，
        # 与 422 model_output_invalid 严格区分（spec §3.4）
        return JSONResponse(status_code=400, content={
            "error": "invalid_request",
            "detail": str(exc.errors()[:3]),
        })

    @app.get("/api/health")
    def health() -> dict:
        return {"ok": True, "mode": settings.mode}

    @app.post("/api/opinions")
    def opinions(req: OpinionRequest) -> dict:
        result = graph.invoke({"context": req.model_dump()})
        if result.get("final") is not None:
            return result["final"]
        return JSONResponse(status_code=422, content={
            "error": "model_output_invalid",
            "detail": result.get("error") or "模型输出不合规",
        })

    return app
