import React, { useEffect, useState } from 'react'
import { api, kg } from '../api.js'

const TYPE_LABEL = {
  ALLOCATION: '分配',
  LANDING: '实捕扣减',
  TRANSFER_OUT: '调出',
  TRANSFER_IN: '调入',
}

export default function TraceDrawer({ balanceId, onClose }) {
  const [trace, setTrace] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    api.trace(balanceId).then(setTrace).catch((e) => setError(e.message))
  }, [balanceId])

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <aside className="drawer" onClick={(e) => e.stopPropagation()}>
        <div className="drawer-head">
          <h2>余额来源追溯</h2>
          <button className="btn small" onClick={onClose}>关闭 ✕</button>
        </div>

        {error && <div className="error-box">⛔ {error}</div>}
        {!trace && !error && <p>加载中…</p>}

        {trace && (
          <div className="drawer-body">
            <section className="card">
              <h3>账户</h3>
              <p>
                {trace.balance.vesselCode} · {trace.balance.speciesCode} ·{' '}
                {trace.balance.areaCode} · {trace.balance.seasonCode}
              </p>
              <p>
                当前余额 <strong className="big">{kg(trace.balance.remainingKg)} kg</strong>
                ，预测占用 {kg(trace.balance.forecastOccupiedKg)} kg，
                预测可用 {kg(trace.balance.forecastRemainingKg)} kg
              </p>
            </section>

            <section className="card">
              <h3>账本流水（只追加）</h3>
              <table className="grid compact">
                <thead>
                  <tr>
                    <th>时间</th><th>类型</th>
                    <th className="num">变动 (kg)</th>
                    <th className="num">变动后余额</th>
                    <th>单据 / 对方</th><th>备注</th>
                  </tr>
                </thead>
                <tbody>
                  {trace.ledger.map((e) => (
                    <tr key={e.id}>
                      <td><small>{e.entryDate.replace('T', ' ').slice(0, 19)}</small></td>
                      <td><span className={'tag tag-' + e.entryType}>
                        {TYPE_LABEL[e.entryType]}
                      </span></td>
                      <td className={'num ' + (Number(e.deltaKg) < 0 ? 'neg' : 'pos')}>
                        {Number(e.deltaKg) > 0 ? '+' : ''}{kg(e.deltaKg)}
                      </td>
                      <td className="num">{kg(e.remainingKg)}</td>
                      <td>
                        {e.refDoc || '—'}
                        {e.counterpartyVessel && (
                          <><br /><small>对方：{e.counterpartyVessel}</small></>
                        )}
                      </td>
                      <td><small>{e.note || ''}</small></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>

            <section className="card">
              <h3>关联航次（预计 / 实捕 / 差额）</h3>
              {trace.voyages.length === 0 && <p className="empty">无</p>}
              {trace.voyages.map((v) => (
                <div key={v.id} className="subcard">
                  <div className="subhead">
                    {v.voyageNo} · {v.status} · 出发 {v.departedAt.slice(0, 10)}
                  </div>
                  <table className="grid compact">
                    <thead>
                      <tr>
                        <th>物种</th><th>海区</th>
                        <th className="num">预计</th>
                        <th className="num">已实捕</th>
                        <th className="num">差额（预计−实捕）</th>
                      </tr>
                    </thead>
                    <tbody>
                      {v.items.map((it, i) => (
                        <tr key={i}>
                          <td>{it.speciesCode}</td>
                          <td>{it.areaCode}</td>
                          <td className="num">{kg(it.estimatedKg)}</td>
                          <td className="num">{kg(it.landedKg)}</td>
                          <td className={'num ' + (Number(it.differenceKg) > 0 ? '' : 'neg')}>
                            {kg(it.differenceKg)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  {v.landings.length > 0 && (
                    <div className="landings">
                      {v.landings.map((l) => (
                        <small key={l.id}>
                          🧾 {l.certificateNo} · {l.landedAt.slice(0, 10)} ·{' '}
                          {l.portName} ·{' '}
                          {l.lines
                            .map((x) => `${x.speciesCode}/${x.areaCode} ${kg(x.actualKg)}`)
                            .join('；')}
                        </small>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </section>

            <section className="card">
              <h3>关联调拨（来源 / 去向 / 生效期间）</h3>
              {trace.transfers.length === 0 && <p className="empty">无</p>}
              {trace.transfers.map((t) => (
                <div key={t.id} className="subcard">
                  <div className="subhead">
                    {t.transferNo} · {t.status}
                  </div>
                  <p>
                    {t.sourceVesselCode} → {t.destVesselCode} ·{' '}
                    {t.speciesCode} / {t.areaCode} / {t.seasonCode} ·{' '}
                    <strong>{kg(t.amountKg)} kg</strong>
                  </p>
                  <small>生效期间：{t.effectiveFrom} ~ {t.effectiveTo}</small>
                  {t.note && <><br /><small>{t.note}</small></>}
                </div>
              ))}
            </section>
          </div>
        )}
      </aside>
    </div>
  )
}
