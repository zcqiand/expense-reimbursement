# 财务报销系统

中小企业财务报销流程系统。员工提交报销单、经理审批、财务付款。

## 快速开始

```bash
docker compose up -d

# 访问
# - 后端 API 文档: http://localhost:8080/swagger-ui.html
# - 前端工作台:    http://localhost:5173
# - 数据库:        localhost:5432 (user: expense / db: expense)

docker compose down
```

## 功能特性

- 报销单提交与审批（金额 < 1000 直属经理审，≥ 1000 部门经理 + 财务总监两级审）
- OCR 票据识别（多语言 Tesseract OCR + Mock 降级，零 Key 即可跑）
- 结构化审批意见生成（JSON Schema + 验证-修复循环）
- 报表聚合与导出（按部门/状态/时间维度汇总）
- 审计日志与权限矩阵

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

## 配套书籍及章节映射

| 章 | 主题 | 对应源文件 |
| :--- | :--- | :--- |
| 第 11 / 34 章 | 项目规划与架构设计 | `CLAUDE.md` + `docker-compose.yml` |
| 第 12 / 35 章 | 数据库与 API 开发 | `backend/src/main/java/com/zcqiand/expense/` + `db/migration/` |
| 第 13 / 36 章 | 前端开发与 UI 实现 | `frontend/src/pages/` |
| 第 14 / 37 章 | 调试技巧 | 后端日志策略 + 前端 DevTools 集成 |
| 第 15 / 38 章 | 自动化测试与 CI/CD | `backend/src/test/` + `.github/workflows/ci.yml` |
| 第 16 / 39 章 | 精准控制大模型 | `backend/service/ApprovalOpinionService.java` |

## 快速链接

- [功能规格文档.md](功能规格文档.md) — 功能名称、描述与验收标准
- [CLAUDE.md](CLAUDE.md) — 开发约定与编码规范
