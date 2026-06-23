-- V2: approval_record 表与外键索引。
-- 与 expense_report 一对多关系：每张报销单可累计多条审批记录（一级 + 二级）。
--
-- 设计取舍：
-- - approver_id BIGINT NOT NULL：谁审的，必须可追溯
-- - level VARCHAR：分级审批（MANAGER 直属经理 / FINANCE_DIRECTOR 财务总监）
-- - decision VARCHAR：APPROVED / REJECTED 二选一
-- - reason VARCHAR(500)：APPROVED 可空，REJECTED 必填——由 Service 层强制校验，
--   CHECK 约束在 DB 层做兜底（不允许 REJECTED 且 reason IS NULL）
-- - created_at TIMESTAMPTZ：保留审批时序，便于审计

CREATE TABLE approval_record (
    id              BIGSERIAL PRIMARY KEY,
    expense_id      BIGINT NOT NULL REFERENCES expense_report(id),
    approver_id     BIGINT NOT NULL,
    level           VARCHAR(30) NOT NULL
                    CHECK (level IN ('MANAGER', 'FINANCE_DIRECTOR')),
    decision        VARCHAR(20) NOT NULL
                    CHECK (decision IN ('APPROVED', 'REJECTED')),
    reason          VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_reject_must_have_reason
        CHECK (decision <> 'REJECTED' OR reason IS NOT NULL)
);

CREATE INDEX idx_approval_record_expense
    ON approval_record (expense_id, created_at);
