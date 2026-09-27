// API client for the fictional quota ledger backend.
const BASE = '/api'

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: options.body ? { 'Content-Type': 'application/json' } : undefined,
    ...options,
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    const err = new Error(data?.message || `HTTP ${res.status}`)
    err.code = data?.code
    err.status = res.status
    throw err
  }
  return data
}

export const api = {
  reference: () =>
    Promise.all([
      request('/reference/vessels'),
      request('/reference/species'),
      request('/reference/areas'),
      request('/reference/seasons'),
    ]).then(([vessels, species, areas, seasons]) => ({
      vessels,
      species,
      areas,
      seasons,
    })),

  balances: (filters = {}) => {
    const q = new URLSearchParams(
      Object.entries(filters).filter(([, v]) => v)
    ).toString()
    return request('/balances' + (q ? `?${q}` : ''))
  },

  trace: (balanceId) => request(`/balances/${balanceId}/trace`),
  voyages: () => request('/voyages'),

  allocate: (payload) =>
    request('/allocations', { method: 'POST', body: JSON.stringify(payload) }),

  declareVoyage: (payload) =>
    request('/voyages', { method: 'POST', body: JSON.stringify(payload) }),

  recordLanding: (payload) =>
    request('/landings', { method: 'POST', body: JSON.stringify(payload) }),

  transfer: (payload) =>
    request('/transfers', { method: 'POST', body: JSON.stringify(payload) }),
}

// Show weights with exactly 3 decimals (ledger scale), preserving BigDecimal
// precision sent as JSON strings... (backend numbers arrive as JSON numbers,
// format from the raw string when possible to avoid float artifacts).
export function kg(value) {
  if (value === null || value === undefined) return '—'
  const n = typeof value === 'string' ? Number(value) : value
  return n.toLocaleString('zh-CN', {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  })
}
