const STATUS_CLASS = {
  PENDING: 'warn',
  ACTIVE: 'ok',
  INACTIVE: 'neutral',
  SUCCESS: 'ok',
  SUCCEEDED: 'ok',
  DELIVERED: 'ok',
  SENT: 'ok',
  CONFIRMED: 'ok',
  FAILED: 'err',
  CANCELLED: 'neutral',
  REFUND_PENDING: 'warn',
  REFUNDED: 'neutral',
  COMPLETED: 'ok',
}

export function badgeClass(status) {
  return STATUS_CLASS[String(status ?? '').toUpperCase()] ?? 'neutral'
}

export function shortId(value) {
  if (!value) return '—'
  const text = String(value)
  return text.length > 8 ? text.slice(0, 8) : text
}

export function formatMoney(amount, currency) {
  const value = Number(amount)
  if (!Number.isFinite(value)) return `${currency ?? ''} —`.trim()
  return `${currency ?? ''} ${value.toFixed(2)}`.trim()
}

export function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString()
}

export function refundStatusOf(payment) {
  if (payment?.refundStatus) return String(payment.refundStatus)
  if (payment?.status === 'REFUND_PENDING') return 'PENDING'
  if (payment?.status === 'REFUNDED') return 'COMPLETED'
  return null
}

export function countBy(items, key) {
  return items.reduce((counts, item) => {
    const name = item?.[key] ?? 'UNKNOWN'
    counts[name] = (counts[name] ?? 0) + 1
    return counts
  }, {})
}

export function sortedCounts(counts) {
  return Object.entries(counts).sort((a, b) => b[1] - a[1] || String(a[0]).localeCompare(String(b[0])))
}
