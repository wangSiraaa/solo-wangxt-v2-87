// All weights are BigDecimal on the server (NUMERIC(14,3)).
// On the client we keep the raw strings and only format for display.

export function fmt(value, digits = 3) {
  const n = Number(value)
  if (Number.isNaN(n)) return String(value)
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  })
}

export function kg(value) {
  if (value === null || value === undefined || value === '') return '—'
  return `${fmt(value)}`
}

export function signed(value) {
  if (value === null || value === undefined) return '—'
  const n = Number(value)
  const text = fmt(Math.abs(n))
  if (n > 0) return `+${text}`
  if (n < 0) return `-${text}`
  return text
}

export function fmtDt(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}
