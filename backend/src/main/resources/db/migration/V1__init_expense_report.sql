-- Flyway 第一份迁移：建报销单表骨架。
--
-- 后续阶段会加 expense_item 明细表、approval_record 审批记录表、user 表
-- 等——它们各走 V2 / V3 / V4，本文件不再追加（已应用的迁移不可改）。
--
-- 设计取舍：
-- - id 用 BIGSERIAL：PostgreSQL 16 标准自增模式，与 Entity @GeneratedValue
--   IDENTITY 对齐
-- - status 用 VARCHAR 而非 PostgreSQL ENUM 类型：枚举值变更不必跑 ALTER TYPE
-- - amount 用 NUMERIC(12, 2)：与 Entity BigDecimal precision/scale 对齐，
--   绝对禁止 FLOAT/DOUBLE
-- - created_at / updated_at 用 TIMESTAMPTZ：跨时区可移植

CREATE TABLE expense_report (
    id            BIGSERIAL PRIMARY KEY,
    applicant_id  BIGINT NOT NULL,
    amount        NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    reason        VARCHAR(500),
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                  CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PAID')),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

-- 申请人 + 状态联合索引：审批列表（找某人的 SUBMITTED）与个人列表
-- （某人的所有报销单）都会高频走这两个字段
CREATE INDEX idx_expense_report_applicant_status
    ON expense_report (applicant_id, status);

-- 按状态查的索引：财务付款列表（status = APPROVED）专用
CREATE INDEX idx_expense_report_status
    ON expense_report (status);
