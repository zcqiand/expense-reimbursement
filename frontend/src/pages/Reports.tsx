/**
 * 报表页——第 39 章前端工作台案例。
 *
 * 消费 GET /api/v1/reports/summary，展示三组聚合：
 *   1. 按状态分组（count + amount）
 *   2. 按申请人 top 5（count + amount）
 *   3. 可选月份汇总（带 month 参数时后端才填充）
 *
 * 风格对齐 Dashboard.tsx：同样的 loading/error 范式、原生 table、
 * 内联 style 处理局部布局，全局表格样式来自 styles.css。
 */
import { useEffect, useState } from 'react'
import { reportApi } from '../api/report'
import type { ReportSummary } from '../types'

function formatAmount(n: number): string {
  return n.toFixed(2)
}

export function Reports() {
  const [month, setMonth] = useState<string>('')
  const [summary, setSummary] = useState<ReportSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    reportApi
      .summary(month || undefined)
      .then(setSummary)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [month])

  if (loading) return <p>加载中…</p>
  if (error) return <p style={{ color: 'crimson' }}>加载失败：{error}</p>
  if (!summary) return <p>暂无报表数据</p>

  return (
    <div>
      <h1>报表汇总</h1>

      <div style={{ marginBottom: '1rem' }}>
        <label htmlFor="month-filter" style={{ marginRight: '0.5rem' }}>
          月份筛选（可选）：
        </label>
        <input
          id="month-filter"
          type="month"
          value={month}
          onChange={(e) => setMonth(e.target.value)}
        />
        {month && (
          <button
            type="button"
            onClick={() => setMonth('')}
            style={{ marginLeft: '0.5rem' }}
          >
            清除
          </button>
        )}
      </div>

      {summary.monthlySummary && (
        <section>
          <h2>{summary.monthlySummary.month} 月汇总</h2>
          <table>
            <thead>
              <tr>
                <th>月份</th>
                <th>笔数</th>
                <th>金额合计</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>{summary.monthlySummary.month}</td>
                <td>{summary.monthlySummary.count}</td>
                <td>{formatAmount(summary.monthlySummary.totalAmount)}</td>
              </tr>
            </tbody>
          </table>
        </section>
      )}

      <section>
        <h2>按状态分组（{summary.statusBreakdown.length}）</h2>
        {summary.statusBreakdown.length === 0 ? (
          <p>暂无数据</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>状态</th>
                <th>笔数</th>
                <th>金额合计</th>
              </tr>
            </thead>
            <tbody>
              {summary.statusBreakdown.map((s) => (
                <tr key={s.status}>
                  <td>{s.status}</td>
                  <td>{s.count}</td>
                  <td>{formatAmount(s.totalAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section>
        <h2>申请人 Top（{summary.topApplicants.length}）</h2>
        {summary.topApplicants.length === 0 ? (
          <p>暂无数据</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>申请人 ID</th>
                <th>笔数</th>
                <th>金额合计</th>
              </tr>
            </thead>
            <tbody>
              {summary.topApplicants.map((a) => (
                <tr key={a.applicantId}>
                  <td>{a.applicantId}</td>
                  <td>{a.count}</td>
                  <td>{formatAmount(a.totalAmount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
