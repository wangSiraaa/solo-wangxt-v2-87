import React, { useState } from 'react'
import { api } from '../api.js'
import { Select, TextInput, NumberInput, SubmitButton, ErrorLine } from './ui.jsx'

/** Voyage declaration: only records ESTIMATED usage, never debits a balance. */
export default function DeclareVoyageForm({ ctx, onDone }) {
  const { ref, notify, reload } = ctx
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [warnings, setWarnings] = useState([])
  const [form, setForm] = useState({
    voyageNo: '',
    vesselCode: '',
    seasonCode: ref.seasons[0]?.code || '',
    departedAt: '2026-03-02T06:00',
    note: '',
  })
  const [lines, setLines] = useState([
    { speciesCode: '', areaCode: '', estimatedKg: '' },
  ])

  if (!open) {
    return (
      <button className="btn" onClick={() => setOpen(true)}>
        ➕ 申报新航次（记录预计用量）
      </button>
    )
  }

  const set = (k) => (v) => setForm((f) => ({ ...f, [k]: v }))
  const setLine = (i, k, v) =>
    setLines((ls) => ls.map((l, j) => (j === i ? { ...l, [k]: v } : l)))

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setWarnings([])
    try {
      const payload = {
        voyageNo: form.voyageNo,
        vesselCode: form.vesselCode,
        seasonCode: form.seasonCode,
        departedAt: new Date(form.departedAt).toISOString(),
        note: form.note,
        items: lines
          .filter((l) => l.speciesCode && l.areaCode && l.estimatedKg)
          .map((l) => ({
            speciesCode: l.speciesCode,
            areaCode: l.areaCode,
            estimatedKg: l.estimatedKg,
          })),
      }
      const res = await api.declareVoyage(payload)
      setWarnings(res.warnings || [])
      notify(`航次 ${form.voyageNo} 已申报（预计用量已登记，未扣减余额）`)
      setOpen(false)
      reload()
      onDone?.()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card form-card" onSubmit={submit}>
      <h3>申报航次 — 预计用量（不扣减余额）</h3>
      <div className="form-grid">
        <TextInput label="航次编号" value={form.voyageNo} onChange={set('voyageNo')} placeholder="V-2606" />
        <Select label="船舶" value={form.vesselCode} onChange={set('vesselCode')}
          options={ref.vessels.map((v) => ({ value: v.code, label: `${v.code} ${v.name}` }))} />
        <Select label="季节" value={form.seasonCode} onChange={set('seasonCode')}
          options={ref.seasons.map((s) => ({ value: s.code, label: s.code }))} />
        <label className="field">
          <span>出发时间</span>
          <input type="datetime-local" value={form.departedAt}
            onChange={(e) => set('departedAt')(e.target.value)} required />
        </label>
        <TextInput label="备注" value={form.note} onChange={set('note')} required={false} placeholder="可选" />
      </div>

      <h4>预计捕捞条目</h4>
      {lines.map((l, i) => (
        <div key={i} className="line-row">
          <Select label="物种" value={l.speciesCode}
            onChange={(v) => setLine(i, 'speciesCode', v)}
            options={ref.species.map((s) => ({ value: s.code, label: `${s.code} ${s.commonName}` }))} />
          <Select label="海区" value={l.areaCode}
            onChange={(v) => setLine(i, 'areaCode', v)}
            options={ref.areas.map((a) => ({ value: a.code, label: `${a.code} ${a.name}` }))} />
          <NumberInput label="预计重量" value={l.estimatedKg}
            onChange={(v) => setLine(i, 'estimatedKg', v)} placeholder="0.000" />
          {lines.length > 1 && (
            <button type="button" className="btn small danger"
              onClick={() => setLines((ls) => ls.filter((_, j) => j !== i))}>
              删除
            </button>
          )}
        </div>
      ))}
      <button type="button" className="btn small"
        onClick={() => setLines((ls) => [...ls, { speciesCode: '', areaCode: '', estimatedKg: '' }])}>
        ＋ 增加条目
      </button>

      {warnings.length > 0 && (
        <div className="warn-box">
          {warnings.map((w, i) => <div key={i}>⚠️ {w}</div>)}
        </div>
      )}
      <ErrorLine error={error} />
      <div className="actions">
        <SubmitButton busy={busy}>申报航次</SubmitButton>
        <button type="button" className="btn" onClick={() => setOpen(false)}>取消</button>
      </div>
    </form>
  )
}
