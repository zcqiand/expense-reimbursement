# 财务报销系统

> 《Harness 工程：围绕 Claude Code 构建可靠系统》**卷三·卷四**的可部署配套案例——财务报销流程系统。

本仓库是书中讲解项目规划、数据库与 API、前端开发、调试、CI/CD、精准控制大模型、OCR 识别、报表审计等概念的**全功能实物载体**。

## 项目背景

为什么用「财务报销系统」来配书？报销流程的复杂度刚好够用：

- **要素齐全**：状态机（报销单流转）、多角色权限（员工/经理/财务）、持久化（PostgreSQL）、外部集成（OCR/通知）、结构化输出（LLM 审批意见）一应俱全；
- **可分可合**：卷三讲报销骨架（CRUD + 审批流），卷四在其上扩展 OCR/报表/审计，两套需求共用同一份 schema，互不打架；
- **贴近实战**：中小企业报销是每个人都熟悉流程的业务，读者能快速理解领域逻辑，专注工程实现。

## 功能特性

### 业务功能

- 报销单提交与审批（金额 < 1000 直属经理审，≥ 1000 部门经理 + 财务总监两级审）
- OCR 票据识别（多语言 Tesseract OCR + Mock 降级，零 Key 即可跑）
- 结构化审批意见生成（JSON Schema + 验证-修复循环，接入 Claude Opus 4-7）
- 报表聚合与导出（按部门/状态/时间维度汇总）
- 审计日志与权限矩阵（32 个权限组合测试覆盖）

### 工程特性（教学要点）

- Spring Boot 3.3 + Java 21 LTS + Flyway 数据库迁移
- React 18 + Vite 5 + TypeScript 前端，REST + JWT 认证
- Docker Compose 一键拉起三服务（postgres + backend + frontend）
- 前后端双向 OpenAPI 注解，接口契约同步
- CI/CD 双 job（backend `mvn verify` + frontend `npm run build`）

## 章节映射

### 书一《Harness 工程：围绕 Claude Code 构建可靠系统》（卷三）

| 章节 | 对应代码 |
|------|---------|
| 第 11 章 项目规划与架构设计 | `CLAUDE.md` + `docker-compose.yml` |
| 第 12 章 数据库与 API 开发 | `backend/src/main/java/com/zcqiand/expense/` + `db/migration/` |
| 第 13 章 前端开发与 UI 实现 | `frontend/src/pages/` |
| 第 14 章 调试技巧 | 后端日志策略 + 前端 DevTools 集成 |
| 第 15 章 自动化测试与 CI/CD | `backend/src/test/` + `.github/workflows/ci.yml` |
| 第 16 章 精准控制大模型 | `backend/.../service/ApprovalOpinionService.java`（JSON Schema + 验证-修复循环）|

### 书二《Claude Code 从入门到项目实践》（卷四）

| 章节 | 对应代码 |
|------|---------|
| 第 34 章 项目立项与架构设计 | `CLAUDE.md` + `docker-compose.yml` |
| 第 35 章 数据库与 API 开发 | `backend/src/main/java/com/zcqiand/expense/` + `db/migration/` |
| 第 36 章 前端开发与 UI 实现 | `frontend/src/pages/` |
| 第 37 章 调试技巧 | 后端日志策略 + 前端 DevTools 集成 |
| 第 38 章 自动化测试与 CI/CD | `backend/src/test/` + `.github/workflows/ci.yml` |
| 第 39 章 精准控制大模型 | `backend/.../service/ApprovalOpinionService.java`（JSON Schema + 验证-修复循环）|
| 第 40 章 运维、监控与部署 | `docker-compose.prod.yml` + `nginx.conf` |

## 快速开始

```bash
# 一键拉起三服务（postgres + backend + frontend）
docker compose up -d

# 访问
# - 后端 API 文档: http://localhost:8080/swagger-ui.html
# - 前端工作台:    http://localhost:5173
# - 数据库:        localhost:5432 (user: expense / db: expense)

# 关停
docker compose down
```

## 部署架构

```
       浏览器
          │
          ▼
┌─────────────────┐
│  frontend:5173  │  React 18 + Vite
└────────┬────────┘
         │ REST + JWT
         ▼
┌─────────────────┐
│  backend:8080   │  Spring Boot 3.3 + Java 21
└────────┬────────┘
         │ JPA
         ▼
┌─────────────────┐
│ postgres:5432   │  PostgreSQL 16 + Flyway
└─────────────────┘
```

## 配套书籍

本仓库是以下书籍的可部署配套案例（**共两本，共用同一代码库**）：

- **《Harness 工程：围绕 Claude Code 构建可靠系统》**（卷三·卷四）— 南荣相如

  - 对应卷三：报销骨架（项目规划 / 数据库与 API / 前端 / 调试 / CI/CD）
  - 对应卷四：OCR 识别 / 报表聚合 / 审计日志
  - 代码片段索引：[claudecode-harness-book](https://github.com/zcqiand/claudecode-harness-book)

- **《Claude Code 从入门到项目实践》**（卷三）— 南荣相如

  - 通用工程实践（Agent Loop / 上下文治理 / 多文件协作 / 错误恢复 / 持久化）
  - 代码片段索引：[claude-code-book](https://github.com/zcqiand/claude-code-book)- 
  - 电子书籍网址：[亚马逊](https://www.amazon.com/dp/B0H3M3B8GG)

**Issues**：[https://github.com/zcqiand/expense-reimbursement/issues](https://github.com/zcqiand/expense-reimbursement/issues)
