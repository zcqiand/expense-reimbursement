-- V3: approval_record.opinion 列——大模型生成的审批意见。
--
-- 第 16 章「精准控制大模型」的实物落点：ApprovalOpinionService 调
-- claude-opus-4-7 生成结构化意见（summary/reasoning/suggestion），验证-
-- 修复循环通过后，把结果序列化写入此列。
--
-- 设计取舍：
-- - TEXT 而非 VARCHAR(n)：意见长度不可预期（reasoning 可能上百字），
--   PostgreSQL TEXT 与 H2 TEXT 兼容，不卡长度上限
-- - nullable：老数据与未生成意见的审批记录为 NULL；REJECTED 的意见即使
--   没生成也不影响业务（opinion 是辅助决策的展示字段，非状态机输入）
-- - 不加 CHECK：opinion 内容校验（三字段非空 / 合法 JSON）由 Service 层的
--   验证-修复循环把关，DB 层只保证持久化

ALTER TABLE approval_record ADD COLUMN opinion TEXT;
