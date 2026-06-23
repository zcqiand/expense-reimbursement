/**
 * 工作台首页——骨架阶段最小可跑：列出所有报销单。
 *
 * 第 13 章会扩展为完整工作台（统计卡片 + 列表 + 筛选 + 分页）。
 */
import { useEffect, useState } from 'react'
import { expenseApi } from '../api/expense'
import type { ExpenseReport } from '../types'

export function Dashboard() {
  const [reports, setReports] = useState<ExpenseReport[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    expenseApi
      .list()
      .then(setReports)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <p>加载中…</p>
  if (error) return <p style={{ color: 'crimson' }}>加载失败：{error}</p>

  return (
    <div>
      <h1>财务报销系统</h1>
      <h2>报销单列表（{reports.length}）</h2>
      {reports.length === 0 ? (
        <p>暂无报销单</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>申请人</th>
              <th>金额</th>
              <th>状态</th>
              <th>理由</th>
            </tr>
          </thead>
          <tbody>
            {reports.map((r) => (
              <tr key={r.id}>
                <td>{r.id}</td>
                <td>{r.applicantId}</td>
                <td>{r.amount.toFixed(2)}</td>
                <td>{r.status}</td>
                <td>{r.reason ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}
