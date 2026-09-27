import React from 'react'

export function Select({ label, value, onChange, options, required = true }) {
  return (
    <label className="field">
      <span>{label}</span>
      <select
        value={value}
        required={required}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">请选择…</option>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  )
}

export function TextInput({ label, value, onChange, placeholder, required = true }) {
  return (
    <label className="field">
      <span>{label}</span>
      <input
        type="text"
        value={value}
        placeholder={placeholder}
        required={required}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  )
}

export function NumberInput({ label, value, onChange, placeholder, step = '0.001', required = true }) {
  return (
    <label className="field">
      <span>{label} (kg)</span>
      <input
        type="number"
        min="0"
        step={step}
        value={value}
        placeholder={placeholder}
        required={required}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  )
}

export function DateInput({ label, value, onChange }) {
  return (
    <label className="field">
      <span>{label}</span>
      <input
        type="date"
        value={value}
        required
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  )
}

export function SubmitButton({ children, busy }) {
  return (
    <button className="btn primary" disabled={busy}>
      {busy ? '处理中…' : children}
    </button>
  )
}

export function ErrorLine({ error }) {
  if (!error) return null
  return <div className="error-box">⛔ {error}</div>
}
