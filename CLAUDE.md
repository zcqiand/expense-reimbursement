# expense-reimbursement — 仓库工作约定（供 Claude Code）

本仓为《Harness 工程：围绕 Claude Code 构建可靠系统》卷三/卷四「财务报销系统」的可运行配套工程，是书稿代码块的 **source of truth**。

## 项目定位

中小企业财务报销流程系统。员工提交报销单、经理审批、财务付款。用于演示用 Claude Code 完成一个**生产级、多语言、多服务**项目的全流程。

## 铁律

- **TDD**：每个模块先写失败测试 → 跑确认失败 → 实现 → 跑确认绿 → commit。
- **版本钉死**：依赖与 `version-lock.json` 的 `version_lock` 一致；不引入 lock 外的库。
- **tag 即放行**：全量回归绿后打 `v<MAJOR>.<MINOR>-<NNN>`（NNN=项目号）。
- **只增不改**：扩充时不动现有模块签名/行为；新模块独立测试，CI 双跑（旧测试 + 新测试都绿）。
- **mock-friendly**：`npm install && npm test` 必须在无 Key、无 Docker、无网下全绿。

## 技术栈与版本（钉死于 version-lock.json）

- Spring Boot 3.3.x
- Java 21 LTS
- PostgreSQL 16
- Flyway 10.x
- React 18.x
- Vite 5.x
- TypeScript 5.x
- Docker Compose v2
- springdoc-openapi 2.x

## 验收

```bash
docker compose up -d   # 拉起 postgres + backend + frontend
docker compose down    # 关停
```

## 目录结构

```
expense-reimbursement/
├── docker-compose.yml
├── .env.example
├── CLAUDE.md
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── src/main/java/com/zcqiand/expense/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   ├── dto/
│   │   ├── exception/
│   │   └── config/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   └── src/test/java/
├── frontend/
│   ├── package.json
│   ├── Dockerfile
│   ├── vite.config.ts
│   └── src/
│       ├── App.tsx
│       ├── pages/
│       └── api/
└── .github/workflows/
    └── ci.yml
```

## 编码约定

- 所有业务类型放在 `src/types/`（前端）
- 所有 HTTP 客户端封装在 `src/api/`（前端）
- 特性按 `src/features/` 组织（前端）
- API 必须有 OpenAPI 注解：所有 Controller 方法必须有 `@Operation` + `@ApiResponse` 注解
- 前后端接口契约同步：后端改 DTO，前端必须同步改对应 TypeScript 类型
- 环境变量配置：所有敏感信息走 `.env`，禁止硬编码进代码
