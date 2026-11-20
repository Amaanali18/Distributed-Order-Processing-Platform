import { useState } from 'react'
import { api } from '../api.js'
import { badgeClass, formatMoney, refundStatusOf, shortId } from '../format.js'

const OUTCOMES = ['SUCCEEDED', 'FAILED']

function StatusBadge({ status }) {
  return <span className={`badge ${badgeClass(status)}`}>{status}</span>
}

function latestPayment(payments, orderId) {
  return payments
    .filter((payment) => payment.orderId === orderId)
    .sort((a, b) => new Date(b.createdAt ?? 0) - new Date(a.createdAt ?? 0))[0]
}

export default function Orders({ orders, payments = [], onRefresh, onError, onViewPayments }) {
  const [busyId, setBusyId] = useState(null)
  const [outcomes, setOutcomes] = useState({})
  const [notice, setNotice] = useState(null)

  async function pay(order) {
    const outcome = outcomes[order.id] ?? 'SUCCEEDED'
    setBusyId(order.id)
    setNotice(null)
    try {
      let payment
      try {
        payment = await api.findPaymentByOrder(order.id)
      } catch {
        payment = await api.createPayment(
          order.id,
          order.totalAmount,
          order.currency,
          `web-${order.id}`,
        )
      }
      if (payment.status === 'PENDING') {
        payment = await api.confirmPayment(payment.id, outcome)
      }
      await onRefresh()
      if (payment.status === 'SUCCESS') {
        setNotice({
          type: 'success',
          message: `Payment ${shortId(payment.id)} succeeded. Orders and payment history refreshed.`,
        })
      } else {
        const detail = payment.failureMessage ? `: ${payment.failureMessage}` : ''
        const message = `Payment ${shortId(payment.id)} ended as ${payment.status}${detail}.`
        setNotice({ type: 'error', message })
        onError?.(message)
      }
    } catch (err) {
      setNotice({ type: 'error', message: err.message })
      onError?.(err.message)
    } finally {
      setBusyId(null)
    }
  }

  async function cancel(order) {
    setBusyId(order.id)
    setNotice(null)
    try {
      await api.cancelOrder(order.id)
      await onRefresh()
      setNotice({ type: 'success', message: `Order ${shortId(order.id)} was cancelled.` })
    } catch (err) {
      setNotice({ type: 'error', message: err.message })
      onError?.(err.message)
    } finally {
      setBusyId(null)
    }
  }

  if (orders.length === 0) {
    return <div className="notice info">No orders yet. Place one from the Cart tab.</div>
  }

  return (
    <div className="stack">
      {notice && (
        <div className={`notice ${notice.type}`}>
          {notice.message}
          <button type="button" className="link-button" onClick={() => setNotice(null)}>
            Dismiss
          </button>
        </div>
      )}

      {orders.map((order) => {
        const payment = latestPayment(payments, order.id)
        const refundStatus = refundStatusOf(payment)
        return (
          <div className="card" key={order.id}>
            <div className="row">
              <div>
                <div className="sku">#{shortId(order.id)}</div>
                <div className="hint">
                  {new Date(order.createdAt).toLocaleString()} ·{' '}
                  {order.items?.reduce((n, item) => n + item.quantity, 0) ?? 0} item(s)
                </div>
              </div>
              <StatusBadge status={order.status} />
            </div>

            <table style={{ marginTop: '0.6rem' }}>
              <tbody>
                {(order.items ?? []).map((item) => (
                  <tr key={item.productId}>
                    <td>
                      {item.name} <span className="sku">{item.sku}</span>
                    </td>
                    <td className="muted">×{item.quantity}</td>
                    <td>{formatMoney(Number(item.unitPrice) * item.quantity, order.currency)}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            <div className="row" style={{ marginTop: '0.75rem' }}>
              <div className="price">{formatMoney(order.totalAmount, order.currency)}</div>
              <div className="compact-row">
                {order.status === 'PENDING' && (
                  <>
                    <button onClick={() => cancel(order)} disabled={busyId === order.id}>
                      Cancel
                    </button>
                    <select
                      aria-label={`Payment outcome for order ${shortId(order.id)}`}
                      value={outcomes[order.id] ?? 'SUCCEEDED'}
                      onChange={(event) =>
                        setOutcomes((current) => ({ ...current, [order.id]: event.target.value }))
                      }
                      style={{ width: '9rem' }}
                    >
                      {OUTCOMES.map((option) => (
                        <option key={option} value={option}>
                          {option === 'SUCCEEDED' ? 'Succeed' : 'Fail'}
                        </option>
                      ))}
                    </select>
                    <button
                      className="primary"
                      onClick={() => pay(order)}
                      disabled={busyId === order.id}
                    >
                      {busyId === order.id ? 'Processing…' : 'Pay now'}
                    </button>
                  </>
                )}
              </div>
            </div>

            {payment && (
              <div className="row payment-summary">
                <span className="hint">
                  Payment <StatusBadge status={payment.status} />
                  {refundStatus && (
                    <>
                      {' · Refund '}
                      <StatusBadge status={refundStatus} />
                    </>
                  )}
                  {' · '}
                  {formatMoney(payment.amount, payment.currency)}
                </span>
                <button type="button" className="link-button" onClick={onViewPayments}>
                  View history
                </button>
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}
