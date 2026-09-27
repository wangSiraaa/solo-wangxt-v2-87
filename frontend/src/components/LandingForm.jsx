import React, { useEffect, useState } from 'react'
import { api } from '../api.js'
import { Select, TextInput, NumberInput, SubmitButton, ErrorLine } from './ui.jsx'

/** Landing entry: actual weights per certificate; this is what debits quota. */
export default function LandingForm({ ctx, onDone }) {
  const { ref, notify, reload } = ctx
  const [voyages, setVoyages] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [warnings, setWarnings] = useState([])
  const [cert, setCert] = useState('')
  const [voyageNo, setVoyageNo] = useState('')
  const [landedAt, setLandedAt] = useState('2026-03-10T14:00')
  const [portName, setPortName] = useState('北镜港')
  const [lines, setLines] = useState([{ speciesCode: '', areaCode: '', actualKg: '' }])

  useEffect(() => {
    api.voyages().then(setVoyages).catch(() => {})
  }, [])

  const selected = voyages.find((v) => v.voyageNo === voyageNo)

  const setLine = (i, k, v) =>
    setLines((ls) => ls.map((l, j) => (j === i ? { ...l, [k]: v } : l)))

  const pickVoyage = (no) => {
    setVoyageNo(no)
    const v = voyages.find((x) => x.voyageNo === no)
    if (v) {
      // prefill from the declared items; fill actuals per landing (multiple allowed)
      setLines(v.items.map((it) => ({
        speciesCode: it.speciesCode,
        areaCode: it.areaCode,
        actualKg: '',
      })))
    }
  }

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setWarnings([])
    try {
      const payload = {
        certificateNo: cert,
        voyageNo,
        landedAt: new Date(landedAt).toISOString(),
        portName,
        items: lines
          .filter((l) => l.speciesCode && l.areaCode && l.actualKg)
          .map((l) => ({
            speciesCode: l.speciesCode,
            areaCode: l.areaCode,
            actualKg: l.actualKg,
          })),
      }
      const res = await api.recordLanding(payload)
      setWarnings(res.warnings || [])
      notify(`卸货凭证 ${cert} 已录入，余额按实捕重量扣减`)
      setCert('')
      reload()
      onDone?.()
    } catch (err) {
      setError(`${err.code ? '[' + err.code + '] ' : ''}${err.message}`)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card form-card" onSubmit={submit}>
      <h3>靠港卸货录入 — 实捕重量（据此扣减余额）</h3>
      <p className="hint">
        卸货条目必须落在航次申报的同一物种/海区内；凭证号唯一，重复录入会被拒绝且不会重复扣减。
        同一航次可多次录入不同凭证（分批卸货）。
      </p>
      <div className="form-grid">
        <TextInput label="卸货凭证号" value={cert} onChange={setCert} placeholder="LC-2606-A" />
        <label className="field">
          <span>航次</span>
          <select value={voyageNo} required onChange={(e) => pickVoyage(e.target.value)}>
            <option value="">请选择…</option>
            {voyages.map((v) => (
              <option key={v.id} value={v.voyageNo}>
                {v.voyageNo} · {v.vesselCode} · {v.status}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>靠港时间</span>
          <input type="datetime-local" value={landedAt}
            onChange={(e) => setLandedAt(e.target.value)} required />
        </label>
        <TextInput label="靠港港口" value={portName} onChange={setPortName} />
      </div>

      {selected && (
        <div className="declared-box">
          申报条目：
          {selected.items.map((it, i) => (
            <span key={i} className="chip">
              {it.speciesCode}/{it.areaCode} 预计 {it.estimatedKg}，已卸 {it.landedKg}
            </span>
          ))}
        </div>
      )}

      <h4>本次实捕条目</h4>
      {lines.map((l, i) => (
        <div key={i} className="line-row">
          <Select label="物种" value={l.speciesCode}
            onChange={(v) => setLine(i, 'speciesCode', v)}
            options={ref.species.map((s) => ({ value: s.code, label: s.code }))} />
          <Select label="海区" value={l.areaCode}
            onChange={(v) => setLine(i, 'areaCode', v)}
            options={ref.areas.map((a) => ({ value: a.code, label: a.code }))} />
          <NumberInput label="实捕重量" value={l.actualKg}
            onChange={(v) => setLine(i, 'actualKg', v)} placeholder="0.000" />
        </div>
      ))}

      {warnings.length > 0 && (
        <div className="warn-box">
          {warnings.map((w, i) => <div key={i}>⚠️ {w}</div>)}
        </div>
      )}
      <ErrorLine error={error} />
      <div className="actions">
        <SubmitButton busy={busy}>录入并扣减</SubmitButton>
      </div>
    </form>
  )
}
