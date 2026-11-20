import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { badgeClass, countBy, sortedCounts } from '../format.js'

const PAGE_SIZE = 100
const SOURCES = [
  { key: 'orders', label: 'Orders', load: () => api.listAllOrders({ size: PAGE_SIZE }) },
  { key: 'payments', label: 'Payments', load: () => api.listAdminPayments({ size: PAGE_SIZE }) },
  { key: 'products', label: 'Products', load: () => api.listAdminProducts({ size: PAGE_SIZE }) },
  {
    key: 'notifications',
    label: 'Notifications',
    load: () => api.listAdminNotifications({ size: PAGE_SIZE }),
  },
]

function sampleLabel(page) {
  const shown = page?.content?.length ?? 0
  const total = page?.totalElements
  return total == null ? `${shown} shown` : `${shown} of ${total} shown`
}

function StatusList({ counts }) {
  const rows = sortedCounts(counts)
  const total = rows.reduce((sum, [, count]) => sum + count, 0)
  if (rows.length === 0) return <p className="muted hint">No records in this sample.</p>
  return (
    <div className="status-list">
      {rows.map(([name, count]) => (
        <div className="status-row" key={name}>
          <span className={`badge ${badgeClass(name)}`}>{name}</span>
          <div className="dist-bar">
            <span style={{ width: total ? `${Math.round((count / total) * 100)}%` : '0%' }} />
          </div>
          <span className="mono">{count}</span>
        </div>
      ))}
    </div>
  )
}

export default function OpsDashboard() {
  const [data, setData] = useState({})
  const [errors, setErrors] = useState([])
  const [loading, setLoading] = useState(true)
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    let active = true
    Promise.allSettled(SOURCES.map((source) => source.load())).then((results) => {
      if (!active) return
      const nextData = {}
      const nextErrors = []
      results.forEach((result, index) => {
        const source = SOURCES[index]
        if (result.status === 'fulfilled') {
          nextData[source.key] = result.value
        } else {
          nextErrors.push(`${source.label}: ${result.reason?.message ?? 'request failed'}`)
        }
      })
      setData(nextData)
      setErrors(nextErrors)
      setLoading(false)
    })
    return () => {
      active = false
    }
  }, [refreshKey])

  const orders = data.orders?.content ?? []
  const payments = data.payments?.content ?? []
  const products = data.products?.content ?? []
  const notifications = data.notifications?.content ?? []

  function refresh() {
    setLoading(true)
    setRefreshKey((value) => value + 1)
  }

  return (
    <div className="stack">
      <div className="row">
        <p className="hint" style={{ margin: 0 }}>
          Counts and status distributions are computed from the first page of each admin listing
          endpoint. They are not Prometheus metrics.
        </p>
        <button type="button" onClick={refresh} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      {errors.length > 0 && (
        <div className="notice error">
          <div>Could not load every source:</div>
          <ul className="compact-list">
            {errors.map((message) => (
              <li key={message}>{message}</li>
            ))}
          </ul>
        </div>
      )}

      {loading ? (
        <div className="card">
          <p className="muted">Loading operational snapshot…</p>
        </div>
      ) : (
        <div className="dash-grid">
          <div className="card">
            <div className="row">
              <h3>Orders</h3>
              <span className="stat">{orders.length}</span>
            </div>
            <div className="hint">{sampleLabel(data.orders)}</div>
            <StatusList counts={countBy(orders, 'status')} />
          </div>

          <div className="card">
            <div className="row">
              <h3>Payments</h3>
              <span className="stat">{payments.length}</span>
            </div>
            <div className="hint">{sampleLabel(data.payments)}</div>
            <StatusList counts={countBy(payments, 'status')} />
          </div>

          <div className="card">
            <div className="row">
              <h3>Products</h3>
              <span className="stat">{products.length}</span>
            </div>
            <div className="hint">{sampleLabel(data.products)}</div>
            <StatusList counts={countBy(products, 'status')} />
            <div className="hint" style={{ marginTop: '0.5rem' }}>
              {products.filter((product) => Number(product.availableStock) <= 0).length} out of
              stock · {products.reduce((sum, product) => sum + Number(product.reservedStock ?? 0), 0)}{' '}
              reserved
            </div>
          </div>

          <div className="card">
            <div className="row">
              <h3>Notifications</h3>
              <span className="stat">{notifications.length}</span>
            </div>
            <div className="hint">{sampleLabel(data.notifications)}</div>
            <StatusList counts={countBy(notifications, 'status')} />
          </div>
        </div>
      )}
    </div>
  )
}
