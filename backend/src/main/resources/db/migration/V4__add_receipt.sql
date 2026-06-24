-- V4: receipt 表——第 37 章 OCR 票据识别的落库载体。
-- 与 expense_report 是弱关联一对多：一张报销单可挂多张票据，每张票据
-- 记录一次 OCR 识别结果（成功存正文、失败也留痕）。
--
-- 设计取舍：
-- - id 用 BIGSERIAL：与 expense_report / approval_record 等表主键风格一致，
--   也与 Entity @GeneratedValue IDENTITY 对齐
-- - expense_report_id 用 BIGINT NOT NULL 但不加 REFERENCES 外键：
--   票据与报销单弱关联——OCR 可在报销单提交前/后任意时刻上传，外键硬约束
--   会拖累清理与重导流程。靠 Service 层校验 expense_report_id 合法性即可
-- - ocr_text 用 TEXT：Tesseract 输出可能多行 + 含中文，TEXT 比 VARCHAR(N)
--   更稳妥；JPA 侧用 @Lob 映射为 CLOB
-- - ocr_status 用 VARCHAR(20) + CHECK 枚举：与 expense_report.status 同一
--   风格——枚举值变更不必跑 ALTER TYPE，靠 CHECK 约束兜底合法性
-- - created_at 用 TIMESTAMPTZ：跨时区可移植，对齐其它表
-- - 无 updated_at：票据 OCR 结果是一次性写入，无需更新时间戳
-- - (expense_report_id) 索引：报销单详情页要列出该单下所有票据，高频查询路径

CREATE TABLE receipt (
    id                 BIGSERIAL PRIMARY KEY,
    expense_report_id  BIGINT NOT NULL,
    file_path          VARCHAR(1000) NOT NULL,
    ocr_text           TEXT,
    ocr_status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                       CHECK (ocr_status IN ('PENDING', 'SUCCESS', 'FAILED')),
    created_at         TIMESTAMPTZ NOT NULL
);

-- 按报销单查票据：报销单详情页列出该单下所有上传过的票据
CREATE INDEX idx_receipt_expense_report
    ON receipt (expense_report_id);
