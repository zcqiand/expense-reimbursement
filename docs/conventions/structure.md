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
│       ├── types.ts            ← ExpenseReport/ApiResponse/报表类型（Java DTO 对齐）
│       ├── types/              ← 新增业务类型目录（仓约定：业务类型放 src/types/）
│       │   ├── approval.ts     ← ApprovalRequest/Opinion + 审批枚举
│       │   └── receipt.ts      ← Receipt + OcrStatus
│       ├── api/
│       │   ├── expense.ts      ← CRUD + submit/approve/pay
│       │   ├── report.ts       ← 报表聚合
│       │   ├── receipt.ts      ← multipart 上传（不走统一 request）
│       │   └── opinion.ts      ← 基路径 /api/expenses/{id}/opinion
│       ├── features/
│       │   ├── submit/SubmitForm.tsx
│       │   └── approval/Approval.tsx
│       └── pages/
│           ├── Dashboard.tsx   ← 统计卡 + 筛选 + 行详情（submit/OCR/意见）
│           └── Reports.tsx
└── .github/workflows/
    └── ci.yml
```
