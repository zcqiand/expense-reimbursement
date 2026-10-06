# 财务报销系统

中小企业财务报销流程系统。员工提交报销单、经理审批、财务付款。

## 架构（v2）

Spring Boot 主应用（web/API/PG，零改动换芯）+ Python sidecar 承载 LLM 意见生成内核：

```text
frontend(nginx) ──/api/──> backend(Spring Boot :8080) ──HTTP──> agent(FastAPI+LangGraph :8100)
                                  │                                │
                                  └── PostgreSQL 16 <──────────────┘   LLM（OpenAI 兼容，mock 可跑）
```

- `agent/`：LangGraph 生成图 `assemble → draft → validate →(pass) finish / (fail≤2) repair / (穷尽) fallback`，
  JSON Schema 约束 + 验证-修复循环内核；`LLM_MODE=mock|live`（mock 固定三段中文意见，零 Key 可跑）
- `backend/` 经 `AGENT_BASE_URL` 薄客户端调 sidecar `POST /api/opinions`；
  422 → OPINION_MODEL_OUTPUT_INVALID、不可达/超时 → OPINION_AGENT_UNAVAILABLE（均 409 形状）

## 快速开始

```bash
cp .env.example .env    # LLM_MODE=mock 开箱可跑；live 需填 LLM_API_KEY
docker compose up -d

# 访问
# - 后端 API 文档: http://localhost:8080/swagger-ui.html
# - 前端工作台:    http://localhost:5173
# - agent 探活:    http://localhost:8806/api/health → {"ok":true,"mode":"mock"}
# - 数据库:        localhost:5432 (user: expense / db: expense)

docker compose down
```

## 功能特性

- 报销单提交与审批（金额 < 1000 直属经理审，≥ 1000 部门经理 + 财务总监两级审）
- 前端工作台四页：工作台（统计卡/状态筛选/行详情）+ 新建报销 + 审批（两级/驳回/付款/AI 意见）+ 报表
- OCR 票据识别（多语言 Tesseract OCR + Mock 降级，零 Key 即可跑）
- 结构化审批意见生成（v2：JSON Schema + 验证-修复循环内核在 Python sidecar LangGraph 图；Spring 薄客户端经 HTTP 调用）
- 报表聚合与导出（按部门/状态/时间维度汇总）
- 审计日志与权限矩阵

> JWT 认证在功能规格中提及但后端未实现（无 Security 依赖）；前端不做假登录，
> 如实留空。前后端契约以 Java DTO 逐字段对齐（frontend/src/types/ + api/）。

## 技术栈

| 技术 | 版本 |
| :--- | :--- |
| Spring Boot | 3.3.x |
| Java | 21 LTS |
| React | 18.x |
| Vite | 5.x |
| TypeScript | 5.x |
| PostgreSQL | 16 |
| Flyway | 10.x |
| Docker Compose | v2 |
| Python (agent) | 3.11 |
| FastAPI (agent) | 0.141.x |
| LangGraph (agent) | 0.5.x（钉 `>=0.4,<0.6`） |

## 配套书籍及章节映射

### 书一《Harness 工程：围绕 Claude Code 构建可靠系统》（卷三）

配套版本：`v2.0.1-20260909`（本书第 11—16 章引用源文件以此 tag 为准；其后提交仅为文档修订，代码未变）

| 章 | 主题 | 对应源文件 |
| :--- | :--- | :--- |
| 11 | 项目规划与架构设计 | `CLAUDE.md` + `docker-compose.yml` |
| 12 | 数据库与 API 开发 | `backend/src/main/java/com/zcqiand/expense/` + `backend/src/main/resources/db/migration/` |
| 13 | 前端开发与 UI 实现 | `frontend/src/pages/` + `frontend/src/features/` |
| 14 | AI 调试方法 | 后端日志策略 + 前端 DevTools 集成 |
| 15 | 自动化测试与 CI/CD | `backend/src/test/` + `.github/workflows/ci.yml` |
| 16 | 一次到位（结构化输出与验证-修复循环） | `agent/src/expense_agent/graph.py`（v2：内核已迁 Python sidecar；Spring 薄客户端 `backend/src/main/java/com/zcqiand/expense/client/`） |

## 快速链接

- [CLAUDE.md](CLAUDE.md) — 开发约定与编码规范
- [功能规格.md](docs/功能规格.md) — 功能名称、描述与验收标准
