# 目录结构与模块职责

> 从 CLAUDE.md 移出（L0 60 行门）。树是文件系统的复写，以仓库实际为准；
> 模块职责注解在此维护。

```text
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
