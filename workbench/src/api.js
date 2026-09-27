// Small fetch wrapper for the quota ledger API.
async function request(path, options = {}) {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  const text = await res.text()
  const body = text ? JSON.parse(text) : null
  if (!res.ok) {
    const message = body && body.message ? body.message : `请求失败 (${res.status})`
    throw new Error(message)
  }
  return body
}

export const api = {
  reference: () => request('/api/reference'),
  accounts: (params = {}) => {
    const q = new URLSearchParams(
      Object.entries(params).filter(([, v]) => v !== '' && v != null)
    ).toString()
    return request(`/api/accounts${q ? `?${q}` : ''}`)
  },
  trace: (id) => request(`/api/accounts/${id}/trace`),
  issue: (payload) => request('/api/accounts/issue', {
    method: 'POST', body: JSON.stringify(payload)
  }),
  declareVoyage: (payload) => request('/api/voyages', {
    method: 'POST', body: JSON.stringify(payload)
  }),
  closeVoyage: (no) => request(`/api/voyages/${no}/close`, { method: 'POST' }),
  landing: (payload) => request('/api/landings', {
    method: 'POST', body: JSON.stringify(payload)
  }),
  voyages: (vessel) =>
    request(`/api/voyages${vessel ? `?vesselCode=${vessel}` : ''}`),
  transfers: () => request('/api/transfers'),
  transfer: (payload) => request('/api/transfers', {
    method: 'POST', body: JSON.stringify(payload)
  })
}
