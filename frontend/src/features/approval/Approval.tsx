/**
 * 审批特性——功能规格「审批工作台」验收：
 * SUBMITTED 单列表 + 每单审批表单（approverId/level/decision/reason）→ approve；
 * APPROVED 单可 pay；「生成 AI 审批意见」→ 意见卡（summary/reasoning/suggestion）。
 */
import { useEffect, useState } from 'react'
import { expenseApi } from '../../api/expense'
import { opinionApi } from '../../api/opinion'
import type { ExpenseReport } from '../../types'
import type { ApprovalDecision, ApprovalLevel, ApprovalOpinion } from '../../types/approval'
import { Stamp } from '../submit/SubmitForm'

/** 每单展开的审批操作卡：审批表单 + 付款 + AI 意见。 */
function ApprovalCard(props: { report: ExpenseReport; onChanged: () => void }) {
  const { report } = props
  const [approverId, setApproverId] = useState('2001')
  const [level, setLevel] = useState<ApprovalLevel>('MANAGER')
  const [decision, setDecision] = useState<ApprovalDecision>('APPROVED')
  const [reason, setReason] = useState('')
  const [opinion, setOpinion] = useState<ApprovalOpinion | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

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

  const inReview = report.status === 'SUBMITTED'

  return (
    <article className="approval-card">
      <header className="card-head">
        <span className="report-no">#{report.id}</span>
        <span className="amount">¥ {report.amount.toFixed(2)}</span>
        <Stamp status={report.status} />
        <span className="muted">
          申请人 {report.applicantId} · {report.reason || '无事由'}
        </span>
      </header>

      {inReview && (
        <form
          className="ledger-form"
          onSubmit={(e) => {
            e.preventDefault()
            run(() =>
              expenseApi.approve(report.id, {
                approverId: Number(approverId),
                level,
                decision,
                reason,
              })
            )
          }}
        >
          <label>
            <span>审批人 ID</span>
            <input
              value={approverId}
              onChange={(e) => setApproverId(e.target.value)}
              required
              inputMode="numeric"
            />
          </label>
          <label>
            <span>审批级别</span>
            <select
              value={level}
              onChange={(e) => setLevel(e.target.value as ApprovalLevel)}
            >
              <option value="MANAGER">MANAGER（经理）</option>
              <option value="FINANCE_DIRECTOR">FINANCE_DIRECTOR（财务总监）</option>
            </select>
          </label>
          <label>
            <span>决定</span>
            <select
              value={decision}
              onChange={(e) => setDecision(e.target.value as ApprovalDecision)}
            >
              <option value="APPROVED">批准</option>
              <option value="REJECTED">驳回</option>
            </select>
          </label>
          <label className="wide">
            <span>审批意见{decision === 'REJECTED' ? '（驳回必填）' : ''}</span>
            <input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="同意报销 / 凭证不全…"
            />
          </label>
          <button type="submit" className="stamp-btn" disabled={busy}>
            落章
          </button>
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
        </form>
      )}

      {report.status === 'APPROVED' && (
        <div className="pay-row">
          <button
            type="button"
            className="stamp-btn"
            disabled={busy}
            onClick={() => run(() => expenseApi.pay(report.id))}
          >
            财务付款
          </button>
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
        </div>
      )}

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
    </article>
  )
}

export function Approval() {
  const [reports, setReports] = useState<ExpenseReport[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  function refresh() {
    expenseApi
      .list()
      .then(setReports)
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }

  useEffect(refresh, [])

  const actionable = reports.filter(
    (r) => r.status === 'SUBMITTED' || r.status === 'APPROVED'
  )

  if (loading) return <p>加载中…</p>
  if (error && reports.length === 0)
    return <p style={{ color: 'crimson' }}>加载失败：{error}</p>

  return (
    <section className="page">
      <h2 className="page-title">审批工作台</h2>
      {actionable.length === 0 ? (
        <p className="muted">暂无待审批 / 待付款单据。</p>
      ) : (
        actionable.map((r) => <ApprovalCard key={r.id} report={r} onChanged={refresh} />)
      )}
    </section>
  )
}
