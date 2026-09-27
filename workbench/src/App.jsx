import React, { useEffect, useMemo, useState } from 'react'
import { api } from './api.js'
import { fmt, kg, signed, fmtDt } from './format.js'

function useToast() {
  const [toast, setToast] = useState(null)
  const notify = (message, kind = 'ok') => {
    setToast({ message, kind })
    setTimeout(() => setToast(null), 5000)
  }
  return [toast, notify]
}

export default function App() {
  const [ref, setRef] = useState(null)
  const [accounts, setAccounts] = useState([])
  const [voyages, setVoyages] = useState([])
  const [transfers, setTransfers] = useState([])
  const [filter, setFilter] = useState({ vesselCode: '', speciesCode: '', areaCode: '', seasonCode: '' })
  const [trace, setTrace] = useState(null)
  const [toast, notify] = useToast()

  async function reload() {
    const [a, v, t] = await Promise.all([
      api.accounts(filter), api.voyages(), api.transfers()
    ])
    setAccounts(a)
    setVoyages(v)
    setTransfers(t)
  }

  useEffect(() => {
    api.reference().then(setRef).catch((e) => notify(e.message, 'err'))
  }, [])
  useEffect(() => { reload().catch((e) => notify(e.message, 'err')) }, [
    filter.vesselCode, filter.speciesCode, filter.areaCode, filter.seasonCode
  ])

  async function openTrace(id) {
    try {
      setTrace(await api.trace(id))
    } catch (e) {
      notify(e.message, 'err')
    }
  }

  if (!ref) return <div className="loading">加载虚构参考数据中……</div>

  return (
    <div>
      <header className="topbar">
        <h1>🐟 渔业配额核对工作台</h1>
        <p className="disclaimer">
          ⚠️ 虚构演示系统：所有物种、许可规则、船舶与额度均为虚构，
          <b>不连接任何监管系统，也不构成真实捕捞许可</b>。重量单位：千克（kg，3 位小数）。
        </p>
      </header>

      <nav className="tabs">
        <a href="#balances">额度余额</a>
        <a href="#voyage">航次申报</a>
        <a href="#landing">靠港卸货</a>
        <a href="#transfer">配额调拨</a>
        <a href="#records">航次与调拨总览</a>
      </nav>

      <section id="balances" className="card">
        <h2>额度余额（船舶 × 物种 × 海区 × 季节）</h2>
        <Filters ref={ref} filter={filter} setFilter={setFilter} />
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>船舶</th><th>物种</th><th>海区</th><th>季节</th>
                <th className="num">初始发放</th>
                <th className="num">已预留</th>
                <th className="num">已实捕扣减</th>
                <th className="num">调入</th>
                <th className="num">调出</th>
                <th className="num">可用余额</th>
                <th>来源追溯</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((a) => (
                <tr key={a.id} className={Number(a.availableQty) <= 0 ? 'zero' : ''}>
                  <td>{a.vesselCode}</td>
                  <td>{nameOf(ref.species, a.speciesCode)}</td>
                  <td>{nameOf(ref.areas, a.areaCode)}</td>
                  <td>{nameOf(ref.seasons, a.seasonCode)}</td>
                  <td className="num">{kg(a.issuedQty)}</td>
                  <td className="num">{kg(a.reservedQty)}</td>
                  <td className="num">{kg(a.landedQty)}</td>
                  <td className="num in">{kg(a.transferredInQty)}</td>
                  <td className="num out">{kg(a.transferredOutQty)}</td>
                  <td className="num strong">{kg(a.availableQty)}</td>
                  <td><button onClick={() => openTrace(a.id)}>查看航次/调拨/台账</button></td>
                </tr>
              ))}
              {accounts.length === 0 && (
                <tr><td colSpan="11" className="empty">没有符合筛选条件的余额账户</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <div className="grid2">
        <DeclareVoyageCard refData={ref} done={async (m) => { notify(m); await reload() }} fail={(m) => notify(m, 'err')} />
        <LandingCard refData={ref} done={async (m) => { notify(m); await reload() }} fail={(m) => notify(m, 'err')} />
      </div>

      <div className="grid2">
        <TransferCard refData={ref} done={async (m) => { notify(m); await reload() }} fail={(m) => notify(m, 'err')} />
        <IssueCard refData={ref} done={async (m) => { notify(m); await reload() }} fail={(m) => notify(m, 'err')} />
      </div>

      <RecordsCard voyages={voyages} transfers={transfers} refData={ref}
        onClose={async (no) => {
          try { await api.closeVoyage(no); notify(`航次 ${no} 已结算关闭，剩余预留已释放`); await reload() }
          catch (e) { notify(e.message, 'err') }
        }}
        onTrace={openTrace} />

      {trace && <TraceDialog data={trace} refData={ref} onClose={() => setTrace(null)} />}
      {toast && (
        <div className={`toast ${toast.kind}`} onClick={() => { /* close */ }}>
          {toast.message}
        </div>
      )}
    </div>
  )
}

function nameOf(list, code) {
  const item = list.find((x) => x.code === code)
  return item ? `${item.name}（${code}）` : code
}

function Filters({ ref: refData, filter, setFilter }) {
  return (
    <div className="filters">
      <select value={filter.vesselCode}
        onChange={(e) => setFilter({ ...filter, vesselCode: e.target.value })}>
        <option value="">全部船舶</option>
        {refData.vessels.map((v) => <option key={v.code} value={v.code}>{v.name}（{v.code}）</option>)}
      </select>
      <select value={filter.speciesCode}
        onChange={(e) => setFilter({ ...filter, speciesCode: e.target.value })}>
        <option value="">全部物种</option>
        {refData.species.map((s) => <option key={s.code} value={s.code}>{s.name}（{s.code}）</option>)}
      </select>
      <select value={filter.areaCode}
        onChange={(e) => setFilter({ ...filter, areaCode: e.target.value })}>
        <option value="">全部海区</option>
        {refData.areas.map((a) => <option key={a.code} value={a.code}>{a.name}（{a.code}）</option>)}
      </select>
      <select value={filter.seasonCode}
        onChange={(e) => setFilter({ ...filter, seasonCode: e.target.value })}>
        <option value="">全部季节</option>
        {refData.seasons.map((s) => <option key={s.code} value={s.code}>{s.name}（{s.code}）</option>)}
      </select>
    </div>
  )
}

function Field({ label, children }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  )
}

function DeclareVoyageCard({ refData, done, fail }) {
  const [form, setForm] = useState({
    voyageNo: '', vesselCode: '', areaCode: '', seasonCode: 'S2026-Q3', departsOn: '2026-08-01'
  })
  const [lines, setLines] = useState([{ speciesCode: '', estimatedQty: '' }])
  const [busy, setBusy] = useState(false)

  const permits = useMemo(() => {
    return refData.permits.filter((p) =>
      (!form.areaCode || p.areaCode === form.areaCode) &&
      (!form.seasonCode || p.seasonCode === form.seasonCode))
  }, [refData.permits, form.areaCode, form.seasonCode])

  async function submit() {
    setBusy(true)
    try {
      const payload = {
        ...form,
        departsOn: form.departsOn,
        lines: lines
          .filter((l) => l.speciesCode && l.estimatedQty)
          .map((l) => ({ speciesCode: l.speciesCode, estimatedQty: l.estimatedQty }))
      }
      const v = await api.declareVoyage(payload)
      const total = v.lines.reduce((s, l) => s + Number(l.estimatedQty), 0)
      done(`航次 ${v.voyageNo} 已申报：按 ${fmt(total)} kg 预留预计用量（未实际扣减）`)
      setLines([{ speciesCode: '', estimatedQty: '' }])
      setForm({ ...form, voyageNo: '' })
    } catch (e) { fail(e.message) } finally { setBusy(false) }
  }

  return (
    <section id="voyage" className="card">
      <h2>① 航次申报（记录预计用量，先预留）</h2>
      <div className="form-grid">
        <Field label="航次编号"><input value={form.voyageNo}
          onChange={(e) => setForm({ ...form, voyageNo: e.target.value })} placeholder="例如 TRIP-08" /></Field>
        <Field label="船舶">
          <select value={form.vesselCode} onChange={(e) => setForm({ ...form, vesselCode: e.target.value })}>
            <option value="">选择船舶</option>
            {refData.vessels.map((v) => <option key={v.code} value={v.code}>{v.name}（{v.code}）</option>)}
          </select>
        </Field>
        <Field label="作业海区">
          <select value={form.areaCode} onChange={(e) => setForm({ ...form, areaCode: e.target.value })}>
            <option value="">选择海区</option>
            {refData.areas.map((a) => <option key={a.code} value={a.code}>{a.name}（{a.code}）</option>)}
          </select>
        </Field>
        <Field label="季节">
          <select value={form.seasonCode} onChange={(e) => setForm({ ...form, seasonCode: e.target.value })}>
            {refData.seasons.map((s) => <option key={s.code} value={s.code}>{s.name}（{s.code}）</option>)}
          </select>
        </Field>
        <Field label="出海日期"><input type="date" value={form.departsOn}
          onChange={(e) => setForm({ ...form, departsOn: e.target.value })} /></Field>
      </div>
      <div className="lines">
        <div className="lines-head">
          <b>预计用量（按物种）</b>
          <button type="button" className="ghost"
            onClick={() => setLines([...lines, { speciesCode: '', estimatedQty: '' }])}>+ 添加物种</button>
        </div>
        {lines.map((l, i) => (
          <div className="line-row" key={i}>
            <select value={l.speciesCode} onChange={(e) => {
              const next = [...lines]; next[i] = { ...l, speciesCode: e.target.value }; setLines(next)
            }}>
              <option value="">选择物种</option>
              {permits.map((p) => <option key={p.speciesCode} value={p.speciesCode}>
                {nameOf(refData.species, p.speciesCode)}
              </option>)}
            </select>
            <input placeholder="预计 kg" value={l.estimatedQty}
              onChange={(e) => {
                const next = [...lines]; next[i] = { ...l, estimatedQty: e.target.value }; setLines(next)
              }} />
            {lines.length > 1 && (
              <button type="button" className="ghost danger"
                onClick={() => setLines(lines.filter((_, j) => j !== i))}>删除</button>
            )}
          </div>
        ))}
        <p className="hint">
          仅可选择该海区+季节虚构许可规则允许的物种；预计用量必须有足够可用余额，整单原子预留。
        </p>
      </div>
      <button className="primary" disabled={busy} onClick={submit}>{busy ? '提交中…' : '提交航次申报'}</button>
    </section>
  )
}

function LandingCard({ refData, done, fail }) {
  const [form, setForm] = useState({ voucherNo: '', voyageNo: '', speciesCode: '', landedQty: '' })
  const [busy, setBusy] = useState(false)
  async function submit() {
    setBusy(true)
    try {
      const res = await api.landing(form)
      done(`卸货凭证 ${res.voucherNo} 已录入，实捕 ${kg(res.landedQty)} 已扣减；预计与实际的差额可在航次结算中查看`)
      setForm({ voucherNo: '', voyageNo: '', speciesCode: '', landedQty: '' })
    } catch (e) { fail(e.message) } finally { setBusy(false) }
  }
  return (
    <section id="landing" className="card">
      <h2>② 靠港卸货（录入实际重量并扣减）</h2>
      <div className="form-grid">
        <Field label="卸货凭证号（幂等键）"><input value={form.voucherNo}
          onChange={(e) => setForm({ ...form, voucherNo: e.target.value })} placeholder="例如 VCH-08，重复提交不会重复扣减" /></Field>
        <Field label="航次编号"><input value={form.voyageNo}
          onChange={(e) => setForm({ ...form, voyageNo: e.target.value })} placeholder="例如 TRIP-08" /></Field>
        <Field label="物种（须在申报中）">
          <select value={form.speciesCode} onChange={(e) => setForm({ ...form, speciesCode: e.target.value })}>
            <option value="">选择物种</option>
            {refData.species.map((s) => <option key={s.code} value={s.code}>{s.name}（{s.code}）</option>)}
          </select>
        </Field>
        <Field label="实际重量 kg"><input value={form.landedQty}
          onChange={(e) => setForm({ ...form, landedQty: e.target.value })} placeholder="例如 250.000" /></Field>
      </div>
      <p className="hint">
        物种/海区/季节必须与航次一致；实捕先冲减该航次的预计预留，超出预计的部分需要额外的可用额度。
        同一凭证号重复录入只返回原记录，<b>不会二次扣减</b>。
      </p>
      <button className="primary" disabled={busy} onClick={submit}>{busy ? '提交中…' : '录入卸货并扣减'}</button>
    </section>
  )
}

function TransferCard({ refData, done, fail }) {
  const [form, setForm] = useState({
    transferNo: '', speciesCode: '', areaCode: '', seasonCode: 'S2026-Q3',
    fromVesselCode: '', toVesselCode: '', qty: '',
    effectiveFrom: '2026-07-01', effectiveTo: '2026-09-30', memo: ''
  })
  const [busy, setBusy] = useState(false)
  async function submit() {
    setBusy(true)
    try {
      const t = await api.transfer(form)
      done(`调拨 ${t.transferNo} 已生效：${t.fromVesselCode} → ${t.toVesselCode} ${kg(t.qty)}，已同时登记来源与去向两条台账`)
      setForm({ ...form, transferNo: '', fromVesselCode: '', toVesselCode: '', qty: '' })
    } catch (e) { fail(e.message) } finally { setBusy(false) }
  }
  return (
    <section id="transfer" className="card">
      <h2>③ 配额调拨（来源、去向、生效期间成对登记）</h2>
      <div className="form-grid">
        <Field label="调拨单号"><input value={form.transferNo}
          onChange={(e) => setForm({ ...form, transferNo: e.target.value })} placeholder="例如 TRF-02" /></Field>
        <Field label="物种">
          <select value={form.speciesCode} onChange={(e) => setForm({ ...form, speciesCode: e.target.value })}>
            <option value="">选择物种</option>
            {refData.species.map((s) => <option key={s.code} value={s.code}>{s.name}（{s.code}）</option>)}
          </select>
        </Field>
        <Field label="海区">
          <select value={form.areaCode} onChange={(e) => setForm({ ...form, areaCode: e.target.value })}>
            <option value="">选择海区</option>
            {refData.areas.map((a) => <option key={a.code} value={a.code}>{a.name}（{a.code}）</option>)}
          </select>
        </Field>
        <Field label="季节">
          <select value={form.seasonCode} onChange={(e) => setForm({ ...form, seasonCode: e.target.value })}>
            {refData.seasons.map((s) => <option key={s.code} value={s.code}>{s.name}（{s.code}）</option>)}
          </select>
        </Field>
        <Field label="调出船舶">
          <select value={form.fromVesselCode} onChange={(e) => setForm({ ...form, fromVesselCode: e.target.value })}>
            <option value="">选择来源船舶</option>
            {refData.vessels.map((v) => <option key={v.code} value={v.code}>{v.name}（{v.code}）</option>)}
          </select>
        </Field>
        <Field label="调入船舶">
          <select value={form.toVesselCode} onChange={(e) => setForm({ ...form, toVesselCode: e.target.value })}>
            <option value="">选择去向船舶</option>
            {refData.vessels.map((v) => <option key={v.code} value={v.code}>{v.name}（{v.code}）</option>)}
          </select>
        </Field>
        <Field label="数量 kg"><input value={form.qty}
          onChange={(e) => setForm({ ...form, qty: e.target.value })} placeholder="例如 100.000" /></Field>
        <Field label="生效起"><input type="date" value={form.effectiveFrom}
          onChange={(e) => setForm({ ...form, effectiveFrom: e.target.value })} /></Field>
        <Field label="生效止"><input type="date" value={form.effectiveTo}
          onChange={(e) => setForm({ ...form, effectiveTo: e.target.value })} /></Field>
        <Field label="备注"><input value={form.memo}
          onChange={(e) => setForm({ ...form, memo: e.target.value })} placeholder="可选" /></Field>
      </div>
      <p className="hint">
        调拨仅在<b>同一物种、同一海区、同一季节</b>的账户间进行，生效期间必须落在季节内；
        一次调拨同时生成调出（TRANSFER_OUT）与调入（TRANSFER_IN）两条记录，不会只改一个余额。
      </p>
      <button className="primary" disabled={busy} onClick={submit}>{busy ? '提交中…' : '提交调拨'}</button>
    </section>
  )
}

function IssueCard({ refData, done, fail }) {
  const [form, setForm] = useState({
    vesselCode: '', speciesCode: '', areaCode: '', seasonCode: 'S2026-Q3', qty: '', memo: ''
  })
  const [busy, setBusy] = useState(false)
  async function submit() {
    setBusy(true)
    try {
      const a = await api.issue(form)
      done(`已向 ${a.vesselCode}/${a.speciesCode}/${a.areaCode}/${a.seasonCode} 追加发放 ${kg(form.qty)}`)
      setForm({ ...form, qty: '' })
    } catch (e) { fail(e.message) } finally { setBusy(false) }
  }
  return (
    <section className="card">
      <h2>④ 追加额度发放（管理操作，可选）</h2>
      <div className="form-grid">
        <Field label="船舶">
          <select value={form.vesselCode} onChange={(e) => setForm({ ...form, vesselCode: e.target.value })}>
            <option value="">选择船舶</option>
            {refData.vessels.map((v) => <option key={v.code} value={v.code}>{v.name}（{v.code}）</option>)}
          </select>
        </Field>
        <Field label="物种">
          <select value={form.speciesCode} onChange={(e) => setForm({ ...form, speciesCode: e.target.value })}>
            <option value="">选择物种</option>
            {refData.species.map((s) => <option key={s.code} value={s.code}>{s.name}（{s.code}）</option>)}
          </select>
        </Field>
        <Field label="海区">
          <select value={form.areaCode} onChange={(e) => setForm({ ...form, areaCode: e.target.value })}>
            <option value="">选择海区</option>
            {refData.areas.map((a) => <option key={a.code} value={a.code}>{a.name}（{a.code}）</option>)}
          </select>
        </Field>
        <Field label="季节">
          <select value={form.seasonCode} onChange={(e) => setForm({ ...form, seasonCode: e.target.value })}>
            {refData.seasons.map((s) => <option key={s.code} value={s.code}>{s.name}（{s.code}）</option>)}
          </select>
        </Field>
        <Field label="发放数量 kg"><input value={form.qty}
          onChange={(e) => setForm({ ...form, qty: e.target.value })} placeholder="例如 500.000" /></Field>
        <Field label="备注"><input value={form.memo}
          onChange={(e) => setForm({ ...form, memo: e.target.value })} placeholder="可选" /></Field>
      </div>
      <button className="primary" disabled={busy} onClick={submit}>{busy ? '提交中…' : '发放'}</button>
    </section>
  )
}

function RecordsCard({ voyages, transfers, refData, onClose, onTrace }) {
  const [tab, setTab] = useState('voyages')
  return (
    <section id="records" className="card">
      <h2>航次与调拨记录总览</h2>
      <div className="subtabs">
        <button className={tab === 'voyages' ? 'active' : ''} onClick={() => setTab('voyages')}>
          航次（{voyages.length}）
        </button>
        <button className={tab === 'transfers' ? 'active' : ''} onClick={() => setTab('transfers')}>
          调拨（{transfers.length}）
        </button>
      </div>
      {tab === 'voyages' && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>航次</th><th>船舶</th><th>海区/季节</th><th>出海/状态</th>
                <th>物种</th><th className="num">预计</th><th className="num">实捕</th>
                <th className="num">差额(实-预)</th><th>卸货凭证</th><th>操作</th>
              </tr>
            </thead>
            <tbody>
              {voyages.map((v) => (
                <React.Fragment key={v.id}>
                  {v.lines.map((l, i) => (
                    <tr key={l.speciesCode}>
                      {i === 0 && (
                        <td rowSpan={Math.max(v.lines.length, 1)}>{v.voyageNo}</td>
                      )}
                      {i === 0 && (
                        <td rowSpan={v.lines.length}>{v.vesselCode}</td>
                      )}
                      {i === 0 && (
                        <td rowSpan={v.lines.length}>
                          {nameOf(refData.areas, v.areaCode)}<br />
                          {nameOf(refData.seasons, v.seasonCode)}
                        </td>
                      )}
                      {i === 0 && (
                        <td rowSpan={v.lines.length}>
                          {v.departsOn}<br />
                          <span className={`badge ${v.status === 'CLOSED' ? 'ok' : 'warn'}`}>
                            {v.status === 'CLOSED' ? '已结算关闭' : '未关闭'}
                          </span>
                        </td>
                      )}
                      <td>{nameOf(refData.species, l.speciesCode)}</td>
                      <td className="num">{kg(l.estimatedQty)}</td>
                      <td className="num">{kg(l.landedQty)}</td>
                      <td className={`num ${Number(l.varianceQty) < 0 ? 'in' : Number(l.varianceQty) > 0 ? 'out' : ''}`}>
                        {signed(l.varianceQty)}
                      </td>
                      {i === 0 && (
                        <td rowSpan={v.lines.length}>
                          {v.landings.length === 0 && <span className="empty">—</span>}
                          {v.landings.map((ld) => (
                            <div key={ld.id} className="voucher">
                              {ld.voucherNo}: {kg(ld.landedQty)}
                            </div>
                          ))}
                        </td>
                      )}
                      {i === 0 && (
                        <td rowSpan={v.lines.length}>
                          {v.status === 'OPEN' && (
                            <button onClick={() => onClose(v.voyageNo)}>结算并关闭</button>
                          )}
                        </td>
                      )}
                    </tr>
                  ))}
                </React.Fragment>
              ))}
              {voyages.length === 0 && <tr><td colSpan="10" className="empty">还没有航次</td></tr>}
            </tbody>
          </table>
        </div>
      )}
      {tab === 'transfers' && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>调拨单号</th><th>物种</th><th>海区/季节</th><th className="num">数量</th>
                <th>来源</th><th>去向</th><th>生效期间</th><th>备注</th>
              </tr>
            </thead>
            <tbody>
              {transfers.map((t) => (
                <tr key={t.id}>
                  <td>{t.transferNo}</td>
                  <td>{nameOf(refData.species, t.speciesCode)}</td>
                  <td>{nameOf(refData.areas, t.areaCode)}<br />{nameOf(refData.seasons, t.seasonCode)}</td>
                  <td className="num strong">{kg(t.qty)}</td>
                  <td>{t.fromVesselCode} <button className="link" onClick={() => onTrace(t.fromAccountId)}>余额↗</button></td>
                  <td>{t.toVesselCode} <button className="link" onClick={() => onTrace(t.toAccountId)}>余额↗</button></td>
                  <td>{t.effectiveFrom} ~ {t.effectiveTo}</td>
                  <td>{t.memo || '—'}</td>
                </tr>
              ))}
              {transfers.length === 0 && <tr><td colSpan="8" className="empty">还没有调拨</td></tr>}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

const EVENT_LABELS = {
  ISSUE: '初始发放', RESERVE: '航次预留', LANDING: '实捕扣减',
  SETTLE_RESERVE: '结算释放预留', TRANSFER_OUT: '调拨调出', TRANSFER_IN: '调拨调入'
}

function TraceDialog({ data, refData, onClose }) {
  const a = data.account
  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>来源追溯：{a.vesselCode} / {nameOf(refData.species, a.speciesCode)} /{' '}
            {nameOf(refData.areas, a.areaCode)} / {nameOf(refData.seasons, a.seasonCode)}</h3>
          <button className="ghost" onClick={onClose}>关闭</button>
        </div>

        <div className="balance-strip">
          <span>发放 <b>{kg(a.issuedQty)}</b></span>
          <span>预留 <b>{kg(a.reservedQty)}</b></span>
          <span>实捕 <b>{kg(a.landedQty)}</b></span>
          <span className="in">调入 <b>{kg(a.transferredInQty)}</b></span>
          <span className="out">调出 <b>{kg(a.transferredOutQty)}</b></span>
          <span>可用 <b>{kg(a.availableQty)}</b></span>
        </div>

        <h4>相关航次与卸货</h4>
        <div className="table-wrap small">
          <table>
            <thead><tr><th>航次</th><th>状态</th><th>物种行（预计/实捕/差额）</th><th>卸货凭证</th></tr></thead>
            <tbody>
              {data.voyages.map((v) => (
                <tr key={v.id}>
                  <td>{v.voyageNo}<br /><span className="muted">{v.departsOn}</span></td>
                  <td>{v.status === 'CLOSED' ? '已结算' : '未关闭'}</td>
                  <td>
                    {v.lines.map((l) => (
                      <div key={l.speciesCode}>
                        {l.speciesCode}: 预 {kg(l.estimatedQty)} / 实 {kg(l.landedQty)} /{' '}
                        <span className={Number(l.varianceQty) < 0 ? 'in' : 'out'}>
                          差 {signed(l.varianceQty)}
                        </span>
                      </div>
                    ))}
                  </td>
                  <td>
                    {v.landings.length === 0 ? '—' : v.landings.map((ld) => (
                      <div key={ld.id}>{ld.voucherNo}: {kg(ld.landedQty)}</div>
                    ))}
                  </td>
                </tr>
              ))}
              {data.voyages.length === 0 && <tr><td colSpan="4" className="empty">无相关航次</td></tr>}
            </tbody>
          </table>
        </div>

        <h4>相关调拨（来源 / 去向 / 生效期间）</h4>
        <div className="table-wrap small">
          <table>
            <thead><tr><th>单号</th><th>方向</th><th className="num">数量</th><th>对方</th><th>生效期间</th></tr></thead>
            <tbody>
              {data.transfers.map((t) => (
                <tr key={t.id}>
                  <td>{t.transferNo}</td>
                  <td>{t.fromAccountId === a.id ? '调出 →' : '← 调入'}</td>
                  <td className="num">{kg(t.qty)}</td>
                  <td>{t.fromAccountId === a.id ? t.toVesselCode : t.fromVesselCode}</td>
                  <td>{t.effectiveFrom} ~ {t.effectiveTo}</td>
                </tr>
              ))}
              {data.transfers.length === 0 && <tr><td colSpan="5" className="empty">无相关调拨</td></tr>}
            </tbody>
          </table>
        </div>

        <h4>台账流水（append-only，结存 = 可用余额）</h4>
        <div className="table-wrap small">
          <table>
            <thead><tr><th>#</th><th>事件</th><th className="num">发生额</th><th className="num">结存</th><th>时间</th><th>说明</th></tr></thead>
            <tbody>
              {data.ledger.map((e) => (
                <tr key={e.id}>
                  <td>{e.id}</td>
                  <td><span className="evt">{EVENT_LABELS[e.eventType] || e.eventType}</span></td>
                  <td className={`num ${Number(e.amount) < 0 ? 'out' : 'in'}`}>{signed(e.amount)}</td>
                  <td className="num">{kg(e.balanceAfter)}</td>
                  <td className="muted">{fmtDt(e.eventDate)}</td>
                  <td className="memo">{e.memo || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
