/**
 * 前端类型定义——与后端 Java DTO 字段对齐。
 *
 * 接口契约同步原则（CLAUDE.md）：后端改 DTO/Entity 字段，
 * 前端必须同步改对应 TypeScript 类型；strict 模式下编译期就能发现不一致。
 */

/** 报销单状态——与后端 com.zcqiand.expense.entity.ExpenseStatus 枚举一致 */
export type ExpenseStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'PAID'

/** 报销单——与后端 ExpenseReport Entity 字段一致 */
export interface ExpenseReport {
  id: number
  applicantId: number
  amount: number
  reason: string | null
  status: ExpenseStatus
  createdAt: string
  updatedAt: string
}

/** 统一响应包装——与后端 dto.ApiResponse<T> record 一致 */
export interface ApiResponse<T> {
  success: boolean
  data: T | null
  error: { code: string; message: string } | null
}
