# expense-reimbursement — 仓库工作约定（供 Claude Code）

本仓为可运行配套工程，是书稿代码块的 **source of truth**。

## 项目定位

中小企业财务报销流程系统。员工提交报销单、经理审批、财务付款。用于演示用 Claude Code 完成一个**生产级、多语言、多服务**项目的全流程。

## 铁律

- **TDD**：每个模块先写失败测试 → 跑确认失败 → 实现 → 跑确认绿 → commit。
- **版本钉死**：依赖与 `version-lock.json` 的 `version_lock` 一致；不引入 lock 外的库。
- **只增不改**：扩充时不动现有模块签名/行为；新模块独立测试，CI 双跑（旧测试 + 新测试都绿）。
- **mock-friendly**：`npm install && npm test` 必须在无 Key、无 Docker、无网下全绿。

## 技术栈（版本钉死于 `version-lock.json`）

Spring Boot 3.3 / Java 21 / PostgreSQL 16 / Flyway / React 18 / Vite / TypeScript / Docker Compose / springdoc-openapi
agent/：Python 3.11 / FastAPI / langgraph `>=0.4,<0.6` 单框架（禁 langchain 顶层、CrewAI、checkpointer/interrupt——书 ch11 LangGraph 口径）

## env 契约（v2）

- `LLM_MODE`：必填 `mock|live`，无默认兜底（缺省启动即失败）
- live 追加：`LLM_BASE_URL`（OpenAI 兼容端点）/ `LLM_API_KEY` / `LLM_MODEL`
- `AGENT_PORT`：sidecar 监听端口（容器内 8100）
- `AGENT_BASE_URL`：Spring → sidecar 地址（compose 用 `http://agent:8100`）
- 全集见 `.env.example`

## 验收

```bash
LLM_MODE=mock docker compose up -d   # 拉起 db + agent + backend + frontend
docker compose down    # 关停
cd agent && python -m pytest   # sidecar 测试门（无 Key 无网）
cd backend && mvn test         # Spring 测试门
cd frontend && npm run build   # 前端构建门（tsc -b strict + vite；npm 走 npmmirror）
```

## 编码约定

- 所有业务类型放在 `src/types/`（前端）
- 所有 HTTP 客户端封装在 `src/api/`（前端）
- 特性按 `src/features/` 组织（前端）
- API 必须有 OpenAPI 注解：所有 Controller 方法必须有 `@Operation` + `@ApiResponse` 注解
- 前后端接口契约同步：后端改 DTO，前端必须同步改对应 TypeScript 类型
- 环境变量配置：所有敏感信息走 `.env`，禁止硬编码进代码

## 细则（docs/conventions/，按需引用）

- 目录结构与模块职责 → `docs/conventions/structure.md`
- Tag 规约：Release 格式 `v<MAJOR>.<MINOR>.<PATCH>-<YYYYMMDD>`，禁止删/覆盖，
  禁止 `git push --tags`（显式 `push origin <tag>`）→ `docs/conventions/tagging.md`
