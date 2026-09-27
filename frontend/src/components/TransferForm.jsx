import React, { useState } from 'react'
import { api } from '../api.js'
import { Select, TextInput, NumberInput, DateInput, SubmitButton, ErrorLine } from './ui.jsx'

/**
 * Quota transfer: a paired debit/credit between two balances.
 * Species, sea area and season must match on both legs.
 */
export default function TransferForm({ ctx, onDone }) {
  const { ref, notify, reload } = ctx
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [form, setForm] = useState({
    sourceVesselCode: '',
    destVesselCode: '',
    speciesCode: '',
    areaCode: '',
    seasonCode: ref.seasons[0]?.code || '',
    amountKg: '',
    effectiveFrom: '2026-02-01',
    effectiveTo: '2026-10-31',
    note: '',
  })

  const set = (k) => (v) => setForm((f) => ({ ...f, [k]: v }))

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.transfer(form)
      notify('调拨已登记：来源与去向各生成一条流水，余额已配对扣补')
      setForm((f) => ({ ...f, sourceVesselCode: '', destVesselCode: '', amountKg: '' }))
      reload()
      onDone?.()
    } catch (err) {
      setError(`${err.code ? '[' + err.code + '] ' : ''}${err.message}`)
    } finally {
      setBusy(false)
    }
  }

  const sameVessel = form.sourceVesselCode && form.sourceVesselCode === form.destVesselCode

  return (
    <form className="card form-card" onSubmit={submit}>
      <h3>配额调拨 — 来源 / 去向 / 生效期间（配对扣补，非单边改余额）</h3>
      <p className="hint">
        调入与调出船舶都必须已持有<strong>同一物种、同一海区、同一季节</strong>的额度账户；
        生效期间必须落在季节范围内。不匹配维度的额度无法调拨或使用。
      </p>
      <div className="form-grid">
        <Select label="来源船舶（调出）" value={form.sourceVesselCode}
          onChange={set('sourceVesselCode')}
          options={ref.vessels.map((v) => ({ value: v.code, label: `${v.code} ${v.name}` }))} />
        <Select label="去向船舶（调入）" value={form.destVesselCode}
          onChange={set('destVesselCode')}
          options={ref.vessels.map((v) => ({ value: v.code, label: `${v.code} ${v.name}` }))} />
        <Select label="物种" value={form.speciesCode} onChange={set('speciesCode')}
          options={ref.species.map((s) => ({ value: s.code, label: `${s.code} ${s.commonName}` }))} />
        <Select label="海区" value={form.areaCode} onChange={set('areaCode')}
          options={ref.areas.map((a) => ({ value: a.code, label: `${a.code} ${a.name}` }))} />
        <Select label="季节" value={form.seasonCode} onChange={set('seasonCode')}
          options={ref.seasons.map((s) => ({ value: s.code, label: s.code }))} />
        <NumberInput label="调拨重量" value={form.amountKg} onChange={set('amountKg')}
          placeholder="0.000" />
        <DateInput label="生效开始" value={form.effectiveFrom} onChange={set('effectiveFrom')} />
        <DateInput label="生效结束" value={form.effectiveTo} onChange={set('effectiveTo')} />
        <TextInput label="备注" value={form.note} onChange={set('note')} required={false}
          placeholder="可选" />
      </div>
      {sameVessel && <div className="error-box">⛔ 来源与去向不能是同一船舶</div>}
      <ErrorLine error={error} />
      <div className="actions">
        <SubmitButton busy={busy}>登记调拨（配对扣补）</SubmitButton>
      </div>
    </form>
  )
}
