/**
 * 新建报销特性——功能规格「新建报销单」验收行 1:1：
 * 表单填申请人/金额/事由 → create 落 DRAFT → 提示可一键 submit 转SUBMITTED。
 */
import { useState } from 'react'
import { expenseApi } from '../../api/expense'
import type { ExpenseReport } from '../../types'

export function SubmitForm() {
  const [applicantId, setApplicantId] = useState('1001')
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')
  const [created, setCreated] = useState<ExpenseReport | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function create(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setCreated(null)
    try {
      const report = await expenseApi.create({
        applicantId: Number(applicantId),
        amount: Number(amount),
        reason,
      })
      setCreated(report)
      setAmount('')
      setReason('')
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function submitCreated() {
    if (!created) return
    setBusy(true)
    setError(null)
    try {
      const report = await expenseApi.submit(created.id)
      setCreated(report)
    } catch (err) {
      setError((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="panel">
      <h2 className="panel-title">新建报销单</h2>
      <form onSubmit={create} className="ledger-form">
        <label>
          <span>申请人 ID</span>
          <input
            value={applicantId}
            onChange={(e) => setApplicantId(e.target.value)}
            required
            inputMode="numeric"
          />
        </label>
        <label>
          <span>金额（元）</span>
          <input
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
            inputMode="decimal"
            placeholder="0.00"
          />
        </label>
        <label className="wide">
          <span>事由</span>
          <input
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="出差交通费…"
          />
        </label>
        <button type="submit" className="stamp-btn" disabled={busy}>
          登账
        </button>
      </form>

      {error && <p className="form-error">✕ {error}</p>}

      {created && (
        <div className="receipt-slip">
          <p className="slip-head">
            单号 #{created.id} ·{' '}
            <Stamp status={created.status} />
          </p>
          <p className="slip-line">
            金额 <strong className="amount">{created.amount.toFixed(2)}</strong> 元 ·
            申请人 {created.applicantId}
          </p>
          {created.status === 'DRAFT' ? (
            <p className="slip-line">
              草稿已登账，可提交进入审批流。{' '}
              <button
                type="button"
                className="stamp-btn"
                disabled={busy}
                onClick={submitCreated}
              >
                提交审批
              </button>
            </p>
          ) : (
            <p className="slip-line">已提交，等待审批。</p>
          )}
        </div>
      )}
    </section>
  )
}

/** 状态印章徽章——账簿票据风的视觉锚点，Dashboard/审批页共用。 */
export function Stamp(props: { status: string }) {
  return <span className={`stamp stamp-${props.status}`}>{props.status}</span>
}
