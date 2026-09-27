import React, { useEffect, useState } from 'react'
import { api } from './api.js'
import BalancePanel from './components/BalancePanel.jsx'
import VoyagePanel from './components/VoyagePanel.jsx'
import LandingForm from './components/LandingForm.jsx'
import TransferForm from './components/TransferForm.jsx'

const TABS = [
  { key: 'balances', label: '配额余额' },
  { key: 'voyages', label: '航次与卸货' },
  { key: 'landing', label: '录入实捕' },
  { key: 'transfer', label: '配额调拨' },
]

export default function App() {
  const [ref, setRef] = useState(null)
  const [error, setError] = useState(null)
  const [tab, setTab] = useState('balances')
  const [toast, setToast] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)

  const reload = () => setRefreshKey((k) => k + 1)

  const notify = (message, kind = 'ok') => {
    setToast({ message, kind })
    setTimeout(() => setToast(null), 5000)
  }

  useEffect(() => {
    api
      .reference()
      .then(setRef)
      .catch((e) => setError(e.message))
  }, [])

  if (error) {
    return (
      <div className="fatal">
        <h2>无法连接配额账本服务</h2>
        <p>{error}</p>
        <p>请先启动 Spring Boot 后端（默认 http://localhost:8080）。</p>
      </div>
    )
  }
  if (!ref) return <div className="loading">正在加载虚构配额账本…</div>

  const ctx = { ref, notify, reload, refreshKey }

  return (
    <div>
      <header className="topbar">
        <h1>渔业配额核对工作台</h1>
        <span className="badge-fictional">
          虚构物种与许可规则 · 不连接监管系统 · 非真实捕捞许可
        </span>
      </header>

      <nav className="tabs">
        {TABS.map((t) => (
          <button
            key={t.key}
            className={tab === t.key ? 'tab active' : 'tab'}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </nav>

      <main className="content">
        {tab === 'balances' && <BalancePanel ctx={ctx} />}
        {tab === 'voyages' && <VoyagePanel ctx={ctx} />}
        {tab === 'landing' && (
          <LandingForm ctx={ctx} onDone={() => { reload(); setTab('voyages') }} />
        )}
        {tab === 'transfer' && (
          <TransferForm ctx={ctx} onDone={() => { reload(); setTab('balances') }} />
        )}
      </main>

      {toast && (
        <div className={`toast toast-${toast.kind}`}>{toast.message}</div>
      )}
    </div>
  )
}
