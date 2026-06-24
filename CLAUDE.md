# expense-reimbursement — Claude Code 项目级上下文

> 本文件是《Harness 工程：围绕 Claude Code 构建可靠系统》第 11 章「项目规划与架构设计」的实物示例。

## 项目定位

中小企业财务报销流程系统。员工提交报销单、经理审批、财务付款。用于演示用 Claude Code 完成一个**生产级、多语言、多服务**项目的全流程。

## 技术栈与版本（与书籍 version-lock.json 一致）

| 类别 | 选型 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.3.x |
| 后端语言 | Java | 21 LTS |
| 数据库迁移 | Flyway | 10.x |
| 数据库 | PostgreSQL | 16 |
| ORM | Spring Data JPA + Hibernate | 6.x |
| 前端框架 | React | 18.x |
| 构建工具 | Vite | 5.x |
| 前端语言 | TypeScript | 5.x |
| 容器编排 | Docker Compose | v2 |
| API 文档 | springdoc-openapi | 2.x |

## 目录结构

```
expense-reimbursement/
├── docker-compose.yml             ← 三服务编排
├── .env.example
├── CLAUDE.md                       ← 本文件
├── .claude/settings.json
├── backend/                        ← Spring Boot 后端
│   ├── pom.xml
│   ├── Dockerfile
│   ├── src/main/java/com/zcqiand/expense/
│   │   ├── ExpenseApplication.java
│   │   ├── controller/             ← REST API（第 12 章）
│   │   │   ├── ExpenseReportController.java
│   │   │   ├── ApprovalOpinionController.java  ← 第 16 章审批意见端点
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── service/                ← 审批流（第 12 章）+ 意见生成（第 16 章）
│   │   │   ├── ExpenseService.java
│   │   │   ├── ApprovalOpinionService.java     ← 第 16 章结构化输出 + 验证-修复循环
│   │   │   ├── OpinionModelClient.java         ← 大模型端口（测试可打桩）
│   │   │   └── AnthropicOpinionClient.java     ← claude-opus-4-7 实现
│   │   ├── repository/             ← JPA Repositories
│   │   ├── entity/                 ← JPA 实体（第 12 章 Schema）
│   │   ├── dto/                    ← ApprovalOpinion / ApprovalRequest / ApiResponse
│   │   ├── exception/              ← BusinessException 族 + OpinionGenerationException
│   │   └── config/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/           ← Flyway V1__init.sql 等
│   └── src/test/java/              ← 单元/集成测试（第 14-15 章）
├── frontend/                       ← React 前端
│   ├── package.json
│   ├── Dockerfile
│   ├── vite.config.ts
│   └── src/
│       ├── App.tsx
│       ├── pages/Dashboard.tsx     ← 工作台（第 13 章）
│       ├── pages/SubmitForm.tsx    ← 表单提交（第 13 章）
│       ├── pages/Approval.tsx      ← 审批界面（第 13 章）
│       └── api/expense.ts
└── .github/workflows/ci.yml        ← CI/CD（第 15 章）
```

## 编码约定

- **零伪代码**：禁止 `// TODO`、`throw new UnsupportedOperationException()`、`return null;` 占位。
- **数据库迁移走 Flyway**：禁止用 `ddl-auto: update`，所有 schema 变更必须以新的 Flyway 迁移文件登记。
- **API 必须有 OpenAPI 注解**：所有 Controller 方法必须有 `@Operation` + `@ApiResponse` 注解。
- **前后端接口契约同步**：后端改 DTO，前端必须同步改对应 TypeScript 类型。
- **环境变量配置**：所有敏感信息走 `.env`，禁止硬编码进代码。

## 危险操作

以下操作需要用户二次确认：

- 删除 Flyway 已应用的迁移文件
- 修改 `application.yml` 中的数据库连接配置
- `docker compose down -v`（会删除 postgres volume）

## 业务规则

- 报销单状态：`DRAFT → SUBMITTED → APPROVED|REJECTED → PAID`
- 金额 < 1000 元：直属经理审批
- 金额 ≥ 1000 元：部门经理 + 财务总监两级审批
- 拒绝必须填写理由（service 层强制校验）

## 与本书的关系

| 章节 | 本仓库对应 |
|------|----------|
| 第 11 章 项目规划 | 本文件 + `docker-compose.yml` |
| 第 12 章 数据库与 API | `backend/src/main/java/...` + `db/migration/` |
| 第 13 章 前端与 UI | `frontend/src/pages/` |
| 第 14 章 调试技巧 | Spring Boot Actuator + React DevTools 集成 |
| 第 15 章 CI/CD | `.github/workflows/ci.yml`（backend + frontend 双 job） |
| 第 16 章 精准控制大模型 | `backend/src/main/java/com/zcqiand/expense/service/ApprovalOpinionService.java` + `controller/ApprovalOpinionController.java` |
