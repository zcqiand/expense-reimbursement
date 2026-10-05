import { useState } from 'react'
import { Dashboard } from './pages/Dashboard'
import { Reports } from './pages/Reports'
import { SubmitForm } from './features/submit/SubmitForm'
import { Approval } from './features/approval/Approval'

type Page = 'dashboard' | 'submit' | 'approval' | 'reports'

const NAV: Array<{ key: Page; label: string }> = [
  { key: 'dashboard', label: '工作台' },
  { key: 'submit', label: '新建报销' },
  { key: 'approval', label: '审批' },
  { key: 'reports', label: '报表' },
]

export default function App() {
  const [page, setPage] = useState<Page>('dashboard')

  return (
    <>
      <header className="site-head">
        <h1>财务报销系统</h1>
        <nav className="site-nav" aria-label="主导航">
          {NAV.map((n) => (
            <button
              type="button"
              key={n.key}
              className={page === n.key ? 'nav-btn active' : 'nav-btn'}
              onClick={() => setPage(n.key)}
            >
              {n.label}
            </button>
          ))}
        </nav>
      </header>

      <main>
        {page === 'dashboard' && <Dashboard />}
        {page === 'submit' && <SubmitForm />}
        {page === 'approval' && <Approval />}
        {page === 'reports' && <Reports />}
      </main>
    </>
  )
}
