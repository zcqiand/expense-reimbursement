# expense-reimbursement

《Harness 工程：围绕 Claude Code 构建可靠系统》一书**卷三**的可部署案例项目——财务报销流程系统。

本仓库是书中讲解项目规划、数据库与 API、前端开发、调试、CI/CD、精准控制大模型等概念的实物载体。

## 章节映射

| 章节 | 对应代码 |
|------|---------|
| 第 11 章 项目规划与架构设计 | `CLAUDE.md` + `docker-compose.yml` |
| 第 12 章 数据库与 API 开发 | `backend/src/main/java/com/zcqiand/expense/` + `db/migration/` |
| 第 13 章 前端开发与 UI 实现 | `frontend/src/pages/` |
| 第 14 章 调试技巧 | 后端日志策略 + 前端 DevTools 集成 |
| 第 15 章 自动化测试与 CI/CD | `backend/src/test/` + `frontend/tests/` + `.github/workflows/ci.yml` |
| 第 16 章 精准控制大模型 | 后端 Prompt Engineering 模块（审批意见生成） |

## 技术栈

- 后端：Spring Boot 3.3.x + Java 21 LTS + Flyway
- 数据库：PostgreSQL 16
- 前端：React 18 + Vite 5 + TypeScript
- 编排：Docker Compose v2

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

- **书名**：Harness 工程：围绕 Claude Code 构建可靠系统
- **作者**：南荣相如
- **代码片段索引**：[claudecode-harness-book](https://github.com/zcqiand/claudecode-harness-book)
- **Issues**：https://github.com/zcqiand/expense-reimbursement/issues
