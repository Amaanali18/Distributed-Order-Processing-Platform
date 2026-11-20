import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { badgeClass, formatDate, formatMoney, refundStatusOf, shortId } from '../format.js'
import Pager from './Pager.jsx'

const PAGE_SIZE = 20
const EMPTY_REFUND = { amount: '', reason: '' }

export default function AdminPayments({ onChanged }) {
  const [pageNumber, setPageNumber] = useState(0)
  const [page, setPage] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [expandedId, setExpandedId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState(null)
  const [refundForm, setRefundForm] = useState(EMPTY_REFUND)
  const [refundBusy, setRefundBusy] = useState(false)

  useEffect(() => {
    let active = true
    api
      .listAdminPayments({ page: pageNumber, size: PAGE_SIZE })
      .then((result) => {
        if (!active) return
        setPage(result)
        setError(null)
      })
      .catch((err) => {
        if (active) setError(err.message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [pageNumber, refreshKey])

  async function loadDetail(id) {
    setDetail(null)
    setDetailError(null)
    setDetailLoading(true)
    try {
      setDetail(await api.getAdminPayment(id))
    } catch (err) {
      setDetailError(err.message)
    } finally {
      setDetailLoading(false)
    }
  }

  function toggleDetails(id) {
    if (expandedId === id) {
      setExpandedId(null)
      setDetail(null)
      setDetailError(null)
      setRefundForm(EMPTY_REFUND)
      return
    }
    setExpandedId(id)
    setNotice(null)
    setRefundForm(EMPTY_REFUND)
    loadDetail(id)
  }

  function refresh() {
    setLoading(true)
    setRefreshKey((value) => value + 1)
    onChanged?.()
  }

  function goToPage(number) {
    setLoading(true)
    setPageNumber(number)
  }

  async function submitRefund(event) {
    event.preventDefault()
    const reason = String(refundForm.reason ?? '').trim()
    const amountText = String(refundForm.amount ?? '').trim()

    if (!reason) {
      setError('A refund reason is required.')
      return
    }
    if (reason.length > 256) {
      setError('Refund reason must be at most 256 characters.')
      return
    }
    if (amountText !== '') {
      const amount = Number(amountText)
      if (!Number.isFinite(amount) || amount < 0.01) {
        setError('Refund amount must be at least 0.01, or leave it blank for the full amount.')
        return
      }
    }

    setError(null)
    setNotice(null)
    setRefundBusy(true)
    try {
      const result = await api.adminRefund(
        expandedId,
        amountText === '' ? undefined : Number(amountText),
        reason,
      )
      setNotice({
        type: 'success',
        message: `Refund ${result.status ?? 'requested'} · ${formatMoney(result.amount, result.currency)} for payment ${shortId(expandedId)}.`,
      })
      setRefundForm(EMPTY_REFUND)
      refresh()
      await loadDetail(expandedId)
    } catch (err) {
      setError(err.message)
    } finally {
      setRefundBusy(false)
    }
  }

  const payments = page?.content ?? []

  return (
    <div className="stack">
      {error && <div className="notice error">{error}</div>}
      {notice && <div className={`notice ${notice.type}`}>{notice.message}</div>}

      <div className="card">
        <div className="row">
          <p className="hint" style={{ margin: 0 }}>
            Inspect attempts and issue simulated refunds. Refund status is reported by the payment
            service.
          </p>
          <button type="button" onClick={refresh} disabled={loading}>
            {loading ? 'Loading…' : 'Refresh'}
          </button>
        </div>

        {loading ? (
          <p className="muted">Loading payments…</p>
        ) : payments.length === 0 ? (
          <div className="notice info">No payment attempts found.</div>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Payment</th>
                  <th>Order</th>
                  <th>Customer</th>
                  <th>Status</th>
                  <th>Amount</th>
                  <th>Provider</th>
                  <th>Created</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {payments.map((payment) => (
                  <PaymentRow
                    key={payment.id}
                    payment={payment}
                    expanded={expandedId === payment.id}
                    detail={expandedId === payment.id ? detail : null}
                    detailLoading={expandedId === payment.id && detailLoading}
                    detailError={expandedId === payment.id ? detailError : null}
                    refundForm={refundForm}
                    refundBusy={refundBusy}
                    onToggle={() => toggleDetails(payment.id)}
                    onRefundChange={(field, value) =>
                      setRefundForm((form) => ({ ...form, [field]: value }))
                    }
                    onRefundSubmit={submitRefund}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}

        <Pager page={page} onPageChange={goToPage} disabled={loading} />
      </div>
    </div>
  )
}

function PaymentRow({
  payment,
  expanded,
  detail,
  detailLoading,
  detailError,
  refundForm,
  refundBusy,
  onToggle,
  onRefundChange,
  onRefundSubmit,
}) {
  return (
    <>
      <tr>
        <td>
          <span className="sku">#{shortId(payment.id)}</span>
          <div className="hint">{payment.id}</div>
        </td>
        <td className="mono">{shortId(payment.orderId)}</td>
        <td className="mono">{shortId(payment.customerId)}</td>
        <td>
          <span className={`badge ${badgeClass(payment.status)}`}>{payment.status}</span>
          {refundStatusOf(payment) && <div className="hint">Refund {refundStatusOf(payment)}</div>}
        </td>
        <td>{formatMoney(payment.amount, payment.currency)}</td>
        <td>{payment.provider ?? '—'}</td>
        <td className="muted">{formatDate(payment.createdAt)}</td>
        <td>
          <button type="button" onClick={onToggle}>
            {expanded ? 'Hide details' : 'Details'}
          </button>
        </td>
      </tr>
      {expanded && (
        <tr className="detail-row">
          <td colSpan={8}>
            {detailLoading ? (
              <p className="muted">Loading payment details…</p>
            ) : detailError ? (
              <div className="notice error">{detailError}</div>
            ) : detail ? (
              <div className="stack compact">
                <div className="detail-grid">
                  <div>
                    <span className="muted hint">Payment ID</span>
                    <div className="mono">{detail.id}</div>
                  </div>
                  <div>
                    <span className="muted hint">Order ID</span>
                    <div className="mono">{detail.orderId}</div>
                  </div>
                  <div>
                    <span className="muted hint">Provider reference</span>
                    <div className="mono">{detail.providerPaymentId ?? '—'}</div>
                  </div>
                  <div>
                    <span className="muted hint">Updated</span>
                    <div>{formatDate(detail.updatedAt)}</div>
                  </div>
                </div>
                {(detail.failureCode || detail.failureMessage) && (
                  <div className="hint err-text">
                    {detail.failureCode ?? 'FAILED'}: {detail.failureMessage ?? 'No details'}
                  </div>
                )}
                {refundStatusOf(detail) && (
                  <div className="hint">Refund status: {refundStatusOf(detail)}</div>
                )}
                <form className="refund-form" onSubmit={onRefundSubmit}>
                  <div className="field">
                    <label>Amount (optional)</label>
                    <input
                      aria-label="Refund amount"
                      type="number"
                      min="0.01"
                      step="0.01"
                      placeholder="Full amount"
                      value={refundForm.amount}
                      onChange={(event) => onRefundChange('amount', event.target.value)}
                    />
                  </div>
                  <div className="field grow">
                    <label>Reason</label>
                    <input
                      aria-label="Refund reason"
                      value={refundForm.reason}
                      maxLength={256}
                      placeholder="Customer request"
                      onChange={(event) => onRefundChange('reason', event.target.value)}
                    />
                  </div>
                  <button type="submit" className="primary" disabled={refundBusy}>
                    {refundBusy ? 'Refunding…' : 'Issue refund'}
                  </button>
                </form>
              </div>
            ) : null}
          </td>
        </tr>
      )}
    </>
  )
}
