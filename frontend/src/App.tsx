import { useState } from 'react'
import { Dashboard } from './pages/Dashboard'
import { Reports } from './pages/Reports'

type Page = 'dashboard' | 'reports'

const navButtonStyle = (active: boolean): React.CSSProperties => ({
  marginRight: '0.5rem',
  fontWeight: active ? 700 : 400,
  cursor: 'pointer',
})

export default function App() {
  const [page, setPage] = useState<Page>('dashboard')

  return (
    <>
      <nav style={{ marginBottom: '1.5rem' }}>
        <button
          type="button"
          style={navButtonStyle(page === 'dashboard')}
          onClick={() => setPage('dashboard')}
        >
          工作台
        </button>
        <button
          type="button"
          style={navButtonStyle(page === 'reports')}
          onClick={() => setPage('reports')}
        >
          报表
        </button>
      </nav>

      {page === 'dashboard' ? <Dashboard /> : <Reports />}
    </>
  )
}
