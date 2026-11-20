import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { badgeClass, formatDate, formatMoney, shortId } from '../format.js'
import Pager from './Pager.jsx'

const PAGE_SIZE = 20
const STATUS_OPTIONS = [
  'ALL',
  'PENDING',
  'CONFIRMED',
  'FAILED',
  'CANCELLED',
  'REFUND_PENDING',
  'REFUNDED',
]

export default function AdminOrders() {
  const [status, setStatus] = useState('ALL')
  const [pageNumber, setPageNumber] = useState(0)
  const [page, setPage] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [expandedId, setExpandedId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState(null)

  useEffect(() => {
    let active = true
    api
      .listAllOrders({ page: pageNumber, size: PAGE_SIZE, status })
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
  }, [pageNumber, status, refreshKey])

  function changeStatus(value) {
    setLoading(true)
    setStatus(value)
    setPageNumber(0)
    setExpandedId(null)
    setDetail(null)
  }

  function goToPage(number) {
    setLoading(true)
    setPageNumber(number)
  }

  function refresh() {
    setLoading(true)
    setRefreshKey((value) => value + 1)
  }

  async function toggleDetails(id) {
    if (expandedId === id) {
      setExpandedId(null)
      setDetail(null)
      setDetailError(null)
      return
    }
    setExpandedId(id)
    setDetail(null)
    setDetailError(null)
    setDetailLoading(true)
    try {
      setDetail(await api.getOrder(id))
    } catch (err) {
      setDetailError(err.message)
    } finally {
      setDetailLoading(false)
    }
  }

  const orders = page?.content ?? []

  return (
    <div className="stack">
      {error && <div className="notice error">{error}</div>}

      <div className="card">
        <div className="row">
          <div className="toolbar">
            <label htmlFor="admin-order-status">Status</label>
            <select
              id="admin-order-status"
              value={status}
              onChange={(event) => changeStatus(event.target.value)}
              style={{ width: '12rem' }}
            >
              {STATUS_OPTIONS.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
          </div>
          <button type="button" onClick={refresh} disabled={loading}>
            {loading ? 'Loading…' : 'Refresh'}
          </button>
        </div>

        {loading ? (
          <p className="muted">Loading orders…</p>
        ) : orders.length === 0 ? (
          <div className="notice info">No orders match this filter.</div>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Customer</th>
                  <th>Status</th>
                  <th>Total</th>
                  <th>Items</th>
                  <th>Created</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {orders.map((order) => (
                  <OrderRow
                    key={order.id}
                    order={order}
                    expanded={expandedId === order.id}
                    detail={expandedId === order.id ? detail : null}
                    detailLoading={expandedId === order.id && detailLoading}
                    detailError={expandedId === order.id ? detailError : null}
                    onToggle={() => toggleDetails(order.id)}
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

function OrderRow({ order, expanded, detail, detailLoading, detailError, onToggle }) {
  const items = detail?.items ?? order.items ?? []

  return (
    <>
      <tr>
        <td>
          <span className="sku">#{shortId(order.id)}</span>
          <div className="hint">{order.id}</div>
        </td>
        <td>{order.customerEmail ?? shortId(order.customerId)}</td>
        <td>
          <span className={`badge ${badgeClass(order.status)}`}>{order.status}</span>
        </td>
        <td>{formatMoney(order.totalAmount, order.currency)}</td>
        <td>{order.items?.length ?? 0}</td>
        <td className="muted">{formatDate(order.createdAt)}</td>
        <td>
          <button type="button" onClick={onToggle}>
            {expanded ? 'Hide details' : 'Details'}
          </button>
        </td>
      </tr>
      {expanded && (
        <tr className="detail-row">
          <td colSpan={7}>
            {detailLoading ? (
              <p className="muted">Loading order details…</p>
            ) : detailError ? (
              <div className="notice error">{detailError}</div>
            ) : detail ? (
              <div className="stack compact">
                <div className="detail-grid">
                  <div>
                    <span className="muted hint">Order ID</span>
                    <div className="mono">{detail.id}</div>
                  </div>
                  <div>
                    <span className="muted hint">Customer</span>
                    <div>{detail.customerEmail ?? detail.customerId}</div>
                  </div>
                  <div>
                    <span className="muted hint">Created</span>
                    <div>{formatDate(detail.createdAt)}</div>
                  </div>
                  <div>
                    <span className="muted hint">Updated</span>
                    <div>{formatDate(detail.updatedAt)}</div>
                  </div>
                </div>
                <table>
                  <thead>
                    <tr>
                      <th>SKU</th>
                      <th>Product</th>
                      <th>Qty</th>
                      <th>Unit price</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((item) => (
                      <tr key={item.productId}>
                        <td className="sku">{item.sku}</td>
                        <td>{item.name}</td>
                        <td>{item.quantity}</td>
                        <td>{formatMoney(item.unitPrice, detail.currency)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : null}
          </td>
        </tr>
      )}
    </>
  )
}
