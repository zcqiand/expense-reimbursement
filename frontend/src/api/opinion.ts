/**
 * 审批意见 API 封装——第 16 章「精准控制大模型」。
 *
 * 注意端点基路径与报销单 API 不同：/api/expenses/{id}/opinion
 * （后端 ApprovalOpinionController），不是 /api/v1/expense-reports。
 *
 * 风格对齐 expense.ts：组件不直接调 fetch。
 */
import type { ApiResponse } from '../types'
import type { ApprovalOpinion } from '../types/approval'

const BASE = '/api/expenses'

export const opinionApi = {
  /** 为报销单生成结构化审批意见（summary/reasoning/suggestion）。 */
  generate(reportId: number): Promise<ApprovalOpinion> {
    return fetch(`${BASE}/${reportId}/opinion`, { method: 'POST' }).then(
      async (res) => {
        const body: ApiResponse<ApprovalOpinion> = await res.json()
        if (!body.success || body.data === null) {
          throw new Error(body.error?.message ?? '未知错误')
        }
        return body.data
      }
    )
  },
}
