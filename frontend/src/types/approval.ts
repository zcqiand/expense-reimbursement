/**
 * 审批类型——与后端 dto.ApprovalRequest / dto.ApprovalOpinion 字段对齐。
 *
 * 接口契约同步原则（CLAUDE.md）：后端改 DTO 字段，前端必须同步改对应类型。
 */

/** 审批级别——与后端 entity.ApprovalLevel 枚举一致（第 34/35 章两级审批） */
export type ApprovalLevel = 'MANAGER' | 'FINANCE_DIRECTOR'

/** 审批决定——与后端 entity.ApprovalDecision 枚举一致 */
export type ApprovalDecision = 'APPROVED' | 'REJECTED'

/** 审批请求体——与后端 dto.ApprovalRequest record 一致 */
export interface ApprovalRequest {
  approverId: number
  level: ApprovalLevel
  decision: ApprovalDecision
  reason: string
}

/** 结构化审批意见——与后端 dto.ApprovalOpinion record 一致（第 16 章）。
 *
 * 三个字段都非空才算合格（后端 isComplete 校验），前端可放心直接渲染。
 */
export interface ApprovalOpinion {
  summary: string
  reasoning: string
  suggestion: string
}
