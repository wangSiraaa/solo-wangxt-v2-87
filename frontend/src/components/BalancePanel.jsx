import React, { useEffect, useState } from 'react'
import { api, kg } from '../api.js'
import TraceDrawer from './TraceDrawer.jsx'

export default function BalancePanel({ ctx }) {
  const { ref, refreshKey } = ctx
  const [filters, setFilters] = useState({
    vesselId: '',
    speciesId: '',
    areaId: '',
    seasonId: '',
  })
  const [balances, setBalances] = useState([])
  const [loading, setLoading] = useState(true)
  const [traceId, setTraceId] = useState(null)

  useEffect(() => {
    setLoading(true)
    api
      .balances(filters)
      .then(setBalances)
      .catch(() => setBalances([]))
      .finally(() => setLoading(false))
  }, [refreshKey, filters.vesselId, filters.speciesId, filters.areaId, filters.seasonId])

  const opts = (list, labelFn) =>
    list.map((x) => ({ value: x.id, label: labelFn(x) }))

  const set = (k) => (v) => setFilters((f) => ({ ...f, [k]: v }))

  return (
    <div>
      <div className="filters">
        <FilterSelect label="船舶" value={filters.vesselId} onChange={set('vesselId')}
          options={opts(ref.vessels, (v) => `${v.code} ${v.name}`)} />
        <FilterSelect label="物种" value={filters.speciesId} onChange={set('speciesId')}
          options={opts(ref.species, (s) => `${s.code} ${s.commonName}`)} />
        <FilterSelect label="海区" value={filters.areaId} onChange={set('areaId')}
          options={opts(ref.areas, (a) => `${a.code} ${a.name}`)} />
        <FilterSelect label="季节" value={filters.seasonId} onChange={set('seasonId')}
          options={opts(ref.seasons, (s) => s.code)} />
        <button className="btn" onClick={() => setFilters({ vesselId: '', speciesId: '', areaId: '', seasonId: '' })}>
          清除筛选
        </button>
      </div>

      <p className="hint">
        「预测占用」= 已申报航次的预计用量 − 已实捕量（申报本身不扣减余额）；
        「预测可用」= 当前余额 − 预测占用，为负表示预计不足。点击「追溯」查看该余额的流水、航次与调拨来源去向。
      </p>

      {loading ? (
        <p>加载中…</p>
      ) : (
        <table className="grid">
          <thead>
            <tr>
              <th>船舶</th><th>物种（虚构）</th><th>海区</th><th>季节</th>
              <th className="num">余额 (kg)</th>
              <th className="num">预测占用 (kg)</th>
              <th className="num">预测可用 (kg)</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {balances.map((b) => (
              <tr key={b.id}>
                <td>{b.vesselCode}<br /><small>{b.vesselName}</small></td>
                <td>{b.speciesCode}<br /><small>{b.speciesName}</small></td>
                <td>{b.areaCode}<br /><small>{b.areaName}</small></td>
                <td>{b.seasonCode}<br />
                  <small>{b.seasonStart} ~ {b.seasonEnd}</small></td>
                <td className="num strong">{kg(b.remainingKg)}</td>
                <td className="num">{kg(b.forecastOccupiedKg)}</td>
                <td className={'num ' + (Number(b.forecastRemainingKg) < 0 ? 'neg' : '')}>
                  {kg(b.forecastRemainingKg)}
                </td>
                <td>
                  <button className="btn small" onClick={() => setTraceId(b.id)}>
                    追溯
                  </button>
                </td>
              </tr>
            ))}
            {balances.length === 0 && (
              <tr><td colSpan={8} className="empty">暂无余额账户</td></tr>
            )}
          </tbody>
        </table>
      )}

      {traceId && <TraceDrawer balanceId={traceId} onClose={() => setTraceId(null)} />}
    </div>
  )
}

function FilterSelect({ label, value, onChange, options }) {
  return (
    <label className="field inline">
      <span>{label}</span>
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">全部</option>
        {options.map((o) => (
          <option key={o.value} value={o.value}>{o.label}</option>
        ))}
      </select>
    </label>
  )
}
