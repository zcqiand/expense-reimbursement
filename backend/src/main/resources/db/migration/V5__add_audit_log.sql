-- V5: audit_log 表——第 38 章报表聚合 + 审计追溯的流水载体。
-- 记录报销单 / 票据的每一次状态变更（SUBMIT / APPROVE / REJECT / PAY / OCR），
-- 给运营对账与合规审计用。
--
-- 设计取舍：
-- - id 用 BIGSERIAL：与 expense_report / receipt 等表主键风格一致，也与
--   Entity @GeneratedValue IDENTITY 对齐
-- - action_type / entity_type 用 VARCHAR + CHECK 枚举：与 expense_report.status
--   同一风格——枚举值变更不必跑 ALTER TYPE，靠 CHECK 约束兜底合法性；Entity 侧
--   用 public static final String 常量供业务层引用
-- - detail 用 VARCHAR(2000) 且可空：审计定位靠 (action_type, entity_type,
--   entity_id, operator_id) 四元组，detail 是自由文本备注，可空
-- - 不加 updated_at：审计日志只追加不可改（UPDATE / DELETE 都会破坏追溯链），
--   这是合规审计的硬性要求
-- - 不加外键：审计要能追溯已删除的实体，硬外键反而坏事；靠 Service 层在写入
--   时校验实体存在性即可
-- - created_at 用 TIMESTAMPTZ：跨时区可移植，对齐其它表
-- - (entity_type, entity_id) 复合索引：按实体查审计流水是高频路径
--   （报销单详情页 / 票据详情页都要列出该实体的全部操作历史）

CREATE TABLE audit_log (
    id            BIGSERIAL PRIMARY KEY,
    action_type   VARCHAR(20) NOT NULL
                  CHECK (action_type IN
                         ('SUBMIT', 'APPROVE', 'REJECT', 'PAY', 'OCR')),
    entity_type   VARCHAR(30) NOT NULL
                  CHECK (entity_type IN ('EXPENSE_REPORT', 'RECEIPT')),
    entity_id     BIGINT NOT NULL,
    operator_id   BIGINT NOT NULL,
    detail        VARCHAR(2000),
    created_at    TIMESTAMPTZ NOT NULL
);

-- 按实体查审计流水：报销单 / 票据详情页列出该实体的全部操作历史
CREATE INDEX idx_audit_log_entity
    ON audit_log (entity_type, entity_id);
