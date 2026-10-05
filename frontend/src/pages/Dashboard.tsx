/**
 * 工作台首页——完整版（原骨架仅列表；本版按功能规格扩展）：
 *   1. 统计卡：待审批 / 已批准 / 已付款 / 累计金额（client 端聚合）
 *   2. 状态筛选：全部 + 五状态按钮组
 *   3. 报销单账簿表：行点击展开详情
 * 详情面板：submit（DRAFT 单）+ OCR 票据上传 + AI 审批意见生成。
 *
 * 注意：后端只提供 POST 上传/生成端点，无「票据列表 / 历史意见」查询端点，
 * 故详情面板中票据与意见为本次操作后的即时展示（后端零改动的边界）；
 * 审批/付款流转在「审批」页（features/approval）。
 */
import { Fragment, useEffect, useState } from 'react'
import { expenseApi } from '../api/expense'
import { opinionApi } from '../api/opinion'
import { receiptApi } from '../api/receipt'
import type { ExpenseReport, ExpenseStatus } from '../types'
import type { ApprovalOpinion } from '../types/approval'
import type { Receipt } from '../types/receipt'
import { Stamp } from '../features/submit/SubmitForm'

const STATUS_FILTERS: Array<'ALL' | ExpenseStatus> = [
  'ALL',
  'DRAFT',
  'SUBMITTED',
  'APPROVED',
  'REJECTED',
  'PAID',
]

/** 展开详情面板：单据操作 + 凭证 + 意见。 */
function DetailPanel(props: { report: ExpenseReport; onChanged: () => void }) {
  const { report } = props
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [receipt, setReceipt] = useState<Receipt | null>(null)
  const [opinion, setOpinion] = useState<ApprovalOpinion | null>(null)

  async function run(fn: () => Promise<unknown>) {
    setBusy(true)
    setError(null)
    try {
      await fn()
      props.onChanged()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function upload(file: File) {
    setBusy(true)
    setError(null)
    setReceipt(null)
    try {
      setReceipt(await receiptApi.upload(report.id, file))
      props.onChanged()
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="detail-panel">
      <p className="detail-meta">
        单号 #{report.id} · 申请人 {report.applicantId} · 更新于{' '}
        {new Date(report.updatedAt).toLocaleString()}
      </p>

      {report.status === 'DRAFT' && (
        <button
          type="button"
          className="stamp-btn"
          disabled={busy}
          onClick={() => run(() => expenseApi.submit(report.id))}
        >
          提交审批
        </button>
      )}

      <div className="upload-row">
        <label className="file-label">
          上传票据图片（OCR）
          <input
            type="file"
            accept="image/*"
            disabled={busy}
            onChange={(e) => {
              const f = e.target.files?.[0]
              if (f) upload(f)
              e.target.value = ''
            }}
          />
        </label>
      </div>

      {receipt && (
        <div className="receipt-slip">
          <p className="slip-head">
            票据 #{receipt.id} · <Stamp status={receipt.ocrStatus} />
          </p>
          {receipt.ocrText ? (
            <pre className="ocr-text">{receipt.ocrText}</pre>
          ) : (
            <p className="slip-line">OCR 未产出文本（状态 {receipt.ocrStatus}）。</p>
          )}
        </div>
      )}

      <button
        type="button"
        className="ghost-btn"
        disabled={busy || opinion !== null}
        onClick={() =>
          run(async () => setOpinion(await opinionApi.generate(report.id)))
        }
      >
        {opinion ? '意见已生成' : '生成 AI 审批意见'}
      </button>

      {opinion && (
        <dl className="opinion-card">
          <div>
            <dt>结论</dt>
            <dd>{opinion.summary}</dd>
          </div>
          <div>
            <dt>依据</dt>
            <dd>{opinion.reasoning}</dd>
          </div>
          <div>
            <dt>建议</dt>
            <dd>{opinion.suggestion}</dd>
          </div>
        </dl>
      )}

      {error && <p className="form-error">✕ {error}</p>}
    </div>
  )
}

export function Dashboard() {
  const [reports, setReports] = useState<ExpenseReport[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<(typeof STATUS_FILTERS)[number]>('ALL')
  const [openId, setOpenId] = useState<number | null>(null)

  function refresh() {
    expenseApi
      .list()
      .then(setReports)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(refresh, [])

  if (loading) return <p>加载中…</p>
  if (error && reports.length === 0)
    return <p style={{ color: 'crimson' }}>加载失败：{error}</p>

  const count = (s: ExpenseStatus) => reports.filter((r) => r.status === s).length
  const total = reports.reduce((sum, r) => sum + r.amount, 0)
  const shown =
    filter === 'ALL' ? reports : reports.filter((r) => r.status === filter)

  return (
    <section className="page">
      <div className="stat-cards">
        <div className="stat-card">
          <span className="stat-num">{count('SUBMITTED')}</span>
          <span className="stat-label">待审批</span>
        </div>
        <div className="stat-card">
          <span className="stat-num">{count('APPROVED')}</span>
          <span className="stat-label">已批准</span>
        </div>
        <div className="stat-card">
          <span className="stat-num">{count('PAID')}</span>
          <span className="stat-label">已付款</span>
        </div>
        <div className="stat-card">
          <span className="stat-num amount">{total.toFixed(2)}</span>
          <span className="stat-label">累计金额（元）</span>
        </div>
      </div>

      <div className="filter-row" role="group" aria-label="状态筛选">
        {STATUS_FILTERS.map((s) => (
          <button
            type="button"
            key={s}
            className={filter === s ? 'filter-btn active' : 'filter-btn'}
            onClick={() => setFilter(s)}
          >
            {s === 'ALL' ? '全部' : s}
          </button>
        ))}
      </div>

      <h2 className="page-title">报销单账簿（{shown.length}）</h2>
      {shown.length === 0 ? (
        <p className="muted">该状态下暂无单据。</p>
      ) : (
        <div className="ledger-wrap">
          <table className="ledger">
          <thead>
            <tr>
              <th>单号</th>
              <th>申请人</th>
              <th className="num">金额</th>
              <th>状态</th>
              <th>事由</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((r) => (
              <Fragment key={r.id}>
                <tr
                  className={openId === r.id ? 'row-open' : ''}
                  onClick={() => setOpenId(openId === r.id ? null : r.id)}
                >
                  <td>#{r.id}</td>
                  <td>{r.applicantId}</td>
                  <td className="num">{r.amount.toFixed(2)}</td>
                  <td>
                    <Stamp status={r.status} />
                  </td>
                  <td>{r.reason ?? '—'}</td>
                </tr>
                {openId === r.id && (
                  <tr className="detail-row">
                    <td colSpan={5}>
                      <DetailPanel report={r} onChanged={refresh} />
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
