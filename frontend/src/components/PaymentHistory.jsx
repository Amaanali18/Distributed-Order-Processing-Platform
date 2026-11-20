import { badgeClass, formatDate, formatMoney, refundStatusOf, shortId } from '../format.js'

export default function PaymentHistory({ payments, loading, error, onRefresh, onViewOrder }) {
  const rows = [...(payments ?? [])].sort((a, b) => {
    const left = new Date(a.createdAt ?? 0).getTime()
    const right = new Date(b.createdAt ?? 0).getTime()
    return right - left
  })

  return (
    <div className="stack">
      <div className="row">
        <p className="hint" style={{ margin: 0 }}>
          Every payment attempt for your account, including simulated failures and refunds.
        </p>
        <button type="button" onClick={onRefresh} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      {error && <div className="notice error">{error}</div>}

      {loading ? (
        <div className="card">
          <p className="muted">Loading payments…</p>
        </div>
      ) : rows.length === 0 ? (
        <div className="notice info">
          No payments yet. Place an order, then pay it from the Orders tab.
        </div>
      ) : (
        <div className="card">
          <table>
            <thead>
              <tr>
                <th>Order</th>
                <th>Status</th>
                <th>Amount</th>
                <th>Provider</th>
                <th>Created</th>
                <th>Updated</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((payment) => (
                <tr key={payment.id}>
                  <td>
                    <button
                      type="button"
                      className="link-button"
                      title={payment.orderId}
                      onClick={() => onViewOrder?.(payment.orderId)}
                    >
                      #{shortId(payment.orderId)}
                    </button>
                    <div className="sku">{shortId(payment.id)}</div>
                  </td>
                  <td>
                    <span className={`badge ${badgeClass(payment.status)}`}>{payment.status}</span>
                    {refundStatusOf(payment) && (
                      <div className="hint">Refund {refundStatusOf(payment)}</div>
                    )}
                    {payment.failureMessage && (
                      <div className="hint err-text" title={payment.failureMessage}>
                        {payment.failureCode ?? 'FAILED'}: {payment.failureMessage}
                      </div>
                    )}
                  </td>
                  <td>{formatMoney(payment.amount, payment.currency)}</td>
                  <td>{payment.provider ?? '—'}</td>
                  <td className="muted">{formatDate(payment.createdAt)}</td>
                  <td className="muted">{formatDate(payment.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
