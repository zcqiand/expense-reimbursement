/**
 * 后端 API 封装层。
 *
 * 所有 fetch 调用集中在此文件，组件不直接调 fetch——这让"接口契约"集中可维护。
 * 第 13 章扩展时只改这一个文件即可加新端点。
 */
import type { ApiResponse, ExpenseReport } from '../types'

const BASE = '/api/v1/expense-reports'

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

export const expenseApi = {
  list(): Promise<ExpenseReport[]> {
    return request<ExpenseReport[]>(BASE)
  },

  get(id: number): Promise<ExpenseReport> {
    return request<ExpenseReport>(`${BASE}/${id}`)
  },

  create(payload: { applicantId: number; amount: number; reason: string }): Promise<ExpenseReport> {
    return request<ExpenseReport>(BASE, {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  submit(id: number): Promise<ExpenseReport> {
    return request<ExpenseReport>(`${BASE}/${id}/submit`, { method: 'POST' })
  },
}
