/**
 * 报表聚合 API 封装——第 39 章前端工作台案例。
 *
 * 风格对齐 expense.ts：复用统一 ApiResponse 包装与 request 工具，
 * 组件不直接调 fetch。端点对应后端 ReportController。
 */
import type { ApiResponse, ReportSummary } from '../types'

const BASE = '/api/v1/reports'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  const body: ApiResponse<T> = await res.json()
  if (!body.success || body.data === null) {
    throw new Error(body.error?.message ?? '未知错误')
  }
  return body.data
}

export const reportApi = {
  /** 调 GET /api/v1/reports/summary；month 可选，格式 YYYY-MM。 */
  summary(month?: string): Promise<ReportSummary> {
    const qs = month ? `?month=${encodeURIComponent(month)}` : ''
    return request<ReportSummary>(`${BASE}/summary${qs}`)
  },
}
