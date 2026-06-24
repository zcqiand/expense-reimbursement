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

/**
 * 报表聚合类型——与后端 dto 报表 record 一致（第 38/39 章）。
 *
 * 后端用 BigDecimal 序列化为 JSON number；前端按 number 处理。
 * MonthlySummary.month 是后端 YearMonth，序列化为 "YYYY-MM" 字符串。
 * ReportSummary.monthlySummary 仅当请求带 month 参数时存在（后端 @JsonInclude NON_NULL）。
 */

/** 按状态分组——与后端 dto.StatusBreakdown record 一致 */
export interface StatusBreakdown {
  status: ExpenseStatus | string
  count: number
  totalAmount: number
}

/** 指定月份汇总——与后端 dto.MonthlySummary record 一致 */
export interface MonthlySummary {
  month: string
  count: number
  totalAmount: number
}

/** 按申请人分组——与后端 dto.ApplicantBreakdown record 一致 */
export interface ApplicantBreakdown {
  applicantId: number
  count: number
  totalAmount: number
}

/** 报表汇总响应——与后端 dto.ReportSummary record 一致 */
export interface ReportSummary {
  statusBreakdown: StatusBreakdown[]
  monthlySummary?: MonthlySummary | null
  topApplicants: ApplicantBreakdown[]
}
