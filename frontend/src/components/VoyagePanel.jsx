import React, { useEffect, useState } from 'react'
import { api, kg } from '../api.js'
import DeclareVoyageForm from './DeclareVoyageForm.jsx'

export default function VoyagePanel({ ctx }) {
  const { refreshKey } = ctx
  const [voyages, setVoyages] = useState([])

  useEffect(() => {
    api.voyages().then(setVoyages).catch(() => setVoyages([]))
  }, [refreshKey])

  return (
    <div>
      <DeclareVoyageForm ctx={ctx} onDone={() => {}} />
      <p className="hint">
        航次先记录<strong>预计用量</strong>（不扣余额），靠港后每张卸货凭证按<strong>实捕重量</strong>扣减；
        同一航次支持多次卸货，差额 = 预计 − 累计实捕。
      </p>
      {voyages.map((v) => (
        <section key={v.id} className="card">
          <div className="subhead">
            🚢 {v.voyageNo} · {v.vesselCode} {v.vesselName} · {v.seasonCode} ·{' '}
            出发 {v.departedAt.slice(0, 10)} ·{' '}
            <span className={'status status-' + v.status}>{v.status}</span>
            {v.note && <> · <small>{v.note}</small></>}
          </div>

          <table className="grid compact">
            <thead>
              <tr>
                <th>物种（虚构）</th><th>海区</th>
                <th className="num">预计用量</th>
                <th className="num">累计实捕</th>
                <th className="num">差额</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              {v.items.map((it, i) => (
                <tr key={i}>
                  <td>{it.speciesCode}<br /><small>{it.speciesName}</small></td>
                  <td>{it.areaCode}<br /><small>{it.areaName}</small></td>
                  <td className="num">{kg(it.estimatedKg)}</td>
                  <td className="num">{kg(it.landedKg)}</td>
                  <td className={'num ' + (Number(it.differenceKg) > 0 ? 'warn' : 'neg')}>
                    {kg(it.differenceKg)}
                  </td>
                  <td>
                    {it.fullyLanded ? (
                      <span className="ok">已达预计量</span>
                    ) : (
                      <span className="warn">尚有 {kg(it.differenceKg)} 未卸</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {v.landings.length > 0 && (
            <div className="landings">
              <strong>卸货凭证（{v.landings.length} 张）：</strong>
              {v.landings.map((l) => (
                <div key={l.id} className="landing-chip">
                  🧾 {l.certificateNo} · {l.landedAt.slice(0, 10)} · {l.portName}
                  <ul>
                    {l.lines.map((x, i) => (
                      <li key={i}>
                        {x.speciesCode} / {x.areaCode}：<strong>{kg(x.actualKg)} kg</strong>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}
        </section>
      ))}
      {voyages.length === 0 && <p className="empty">暂无航次</p>}
    </div>
  )
}
