import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { badgeClass, formatDate, formatMoney } from '../format.js'
import Pager from './Pager.jsx'

const PAGE_SIZE = 20
const STATUSES = ['ACTIVE', 'INACTIVE']
const EMPTY_FORM = {
  sku: '',
  name: '',
  description: '',
  price: '',
  currency: 'USD',
  totalStock: '',
}

function validateProduct(form) {
  const problems = []
  const sku = String(form.sku ?? '').trim()
  const name = String(form.name ?? '').trim()
  const currency = String(form.currency ?? '').trim()
  const price = Number(form.price)
  const stock = Number(form.totalStock)

  if (sku.length < 3 || sku.length > 64) problems.push('SKU must be 3–64 characters.')
  if (!name || name.length > 160) problems.push('Name is required (max 160 characters).')
  if (String(form.description ?? '').length > 2000) {
    problems.push('Description must be at most 2000 characters.')
  }
  if (form.price === '' || !Number.isFinite(price) || price < 0) {
    problems.push('Price must be a non-negative number.')
  }
  if (!/^[A-Z]{3}$/.test(currency.toUpperCase())) {
    problems.push('Currency must be a 3-letter code (for example, USD).')
  }
  if (!Number.isInteger(stock) || stock < 0) {
    problems.push('Total stock must be a non-negative whole number.')
  }
  return problems
}

function toPayload(form) {
  return {
    sku: String(form.sku ?? '').trim(),
    name: String(form.name ?? '').trim(),
    description: String(form.description ?? '').trim(),
    price: Number(form.price),
    currency: String(form.currency ?? '').trim().toUpperCase(),
    totalStock: Number(form.totalStock),
  }
}

export default function AdminProducts({ onChanged }) {
  const [pageNumber, setPageNumber] = useState(0)
  const [page, setPage] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)
  const [createForm, setCreateForm] = useState(EMPTY_FORM)
  const [edit, setEdit] = useState(null)
  const [statusDrafts, setStatusDrafts] = useState({})
  const [restock, setRestock] = useState({ id: null, quantity: '1' })

  useEffect(() => {
    let active = true
    api
      .listAdminProducts({ page: pageNumber, size: PAGE_SIZE })
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

  function reload() {
    setLoading(true)
    setRefreshKey((value) => value + 1)
    onChanged?.()
  }

  function goToPage(number) {
    setLoading(true)
    setPageNumber(number)
  }

  function report(err) {
    setNotice(null)
    setError(err?.message ?? 'Request failed')
  }

  async function run(action, successMessage) {
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await action()
      setNotice({ type: 'success', message: successMessage })
      reload()
    } catch (err) {
      report(err)
    } finally {
      setBusy(false)
    }
  }

  function submitCreate(event) {
    event.preventDefault()
    const problems = validateProduct(createForm)
    if (problems.length > 0) {
      report({ message: problems.join(' ') })
      return
    }
    const payload = toPayload(createForm)
    run(async () => {
      await api.createProduct(payload)
      setCreateForm(EMPTY_FORM)
    }, `Created ${payload.sku}.`)
  }

  function saveEdit(id) {
    const problems = validateProduct(edit.values)
    if (problems.length > 0) {
      report({ message: problems.join(' ') })
      return
    }
    const payload = toPayload(edit.values)
    run(async () => {
      await api.updateProduct(id, payload)
      setEdit(null)
    }, `Updated ${payload.sku}.`)
  }

  function applyStatus(product) {
    const status = statusDrafts[product.id] ?? product.status
    if (!STATUSES.includes(status)) {
      report({ message: `Unsupported status ${status}.` })
      return
    }
    run(async () => {
      await api.changeProductStatus(product.id, status)
      setStatusDrafts((drafts) => {
        const next = { ...drafts }
        delete next[product.id]
        return next
      })
    }, `${product.sku} is now ${status}.`)
  }

  function applyRestock(product) {
    const quantity = Number(restock.quantity)
    if (!Number.isInteger(quantity) || quantity <= 0) {
      report({ message: 'Restock quantity must be a positive whole number.' })
      return
    }
    run(async () => {
      await api.restockProduct(product.id, quantity)
      setRestock({ id: null, quantity: '1' })
    }, `Added ${quantity} unit(s) to ${product.sku}.`)
  }

  function setCreateField(field, value) {
    setCreateForm((form) => ({ ...form, [field]: value }))
  }

  function setEditField(field, value) {
    setEdit((current) => ({ ...current, values: { ...current.values, [field]: value } }))
  }

  const products = page?.content ?? []

  return (
    <div className="stack">
      {error && <div className="notice error">{error}</div>}
      {notice && <div className={`notice ${notice.type}`}>{notice.message}</div>}

      <div className="card">
        <h3>Create product</h3>
        <form className="form-grid" onSubmit={submitCreate}>
          <div className="field">
            <label htmlFor="product-sku">SKU</label>
            <input
              id="product-sku"
              value={createForm.sku}
              onChange={(event) => setCreateField('sku', event.target.value)}
              placeholder="WIDGET-001"
              required
            />
          </div>
          <div className="field">
            <label htmlFor="product-name">Name</label>
            <input
              id="product-name"
              value={createForm.name}
              onChange={(event) => setCreateField('name', event.target.value)}
              required
            />
          </div>
          <div className="field span-2">
            <label htmlFor="product-description">Description</label>
            <input
              id="product-description"
              value={createForm.description}
              onChange={(event) => setCreateField('description', event.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="product-price">Price</label>
            <input
              id="product-price"
              type="number"
              min="0"
              step="0.01"
              value={createForm.price}
              onChange={(event) => setCreateField('price', event.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="product-currency">Currency</label>
            <input
              id="product-currency"
              value={createForm.currency}
              onChange={(event) => setCreateField('currency', event.target.value)}
              maxLength={3}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="product-stock">Total stock</label>
            <input
              id="product-stock"
              type="number"
              min="0"
              step="1"
              value={createForm.totalStock}
              onChange={(event) => setCreateField('totalStock', event.target.value)}
              required
            />
          </div>
          <div className="field align-end">
            <button className="primary" type="submit" disabled={busy}>
              {busy ? 'Saving…' : 'Create product'}
            </button>
          </div>
        </form>
      </div>

      <div className="card">
        <div className="row">
          <h3>Catalog</h3>
          <button type="button" onClick={reload} disabled={loading}>
            {loading ? 'Loading…' : 'Refresh'}
          </button>
        </div>

        {loading ? (
          <p className="muted">Loading products…</p>
        ) : products.length === 0 ? (
          <div className="notice info">No products found. Create one above.</div>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Price</th>
                  <th>Stock</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {products.map((product) => {
                  const isEditing = edit?.id === product.id
                  const isRestocking = restock.id === product.id
                  const draftStatus = statusDrafts[product.id] ?? product.status
                  return (
                    <tr key={product.id}>
                      <td>
                        {isEditing ? (
                          <div className="stack compact">
                            <input
                              aria-label="SKU"
                              value={edit.values.sku}
                              onChange={(event) => setEditField('sku', event.target.value)}
                            />
                            <input
                              aria-label="Name"
                              value={edit.values.name}
                              onChange={(event) => setEditField('name', event.target.value)}
                            />
                            <input
                              aria-label="Description"
                              value={edit.values.description}
                              onChange={(event) => setEditField('description', event.target.value)}
                            />
                          </div>
                        ) : (
                          <>
                            <div>{product.name}</div>
                            <div className="sku">{product.sku}</div>
                            <div className="hint">{product.description}</div>
                          </>
                        )}
                      </td>
                      <td>
                        {isEditing ? (
                          <div className="stack compact">
                            <input
                              aria-label="Price"
                              type="number"
                              min="0"
                              step="0.01"
                              value={edit.values.price}
                              onChange={(event) => setEditField('price', event.target.value)}
                            />
                            <input
                              aria-label="Currency"
                              value={edit.values.currency}
                              onChange={(event) => setEditField('currency', event.target.value)}
                              maxLength={3}
                            />
                          </div>
                        ) : (
                          <>{formatMoney(product.price, product.currency)}</>
                        )}
                      </td>
                      <td>
                        {isRestocking ? (
                          <div className="stack compact">
                            <input
                              aria-label="Restock quantity"
                              type="number"
                              min="1"
                              step="1"
                              value={restock.quantity}
                              onChange={(event) =>
                                setRestock((current) => ({ ...current, quantity: event.target.value }))
                              }
                            />
                            <div className="row compact-row">
                              <button type="button" onClick={() => applyRestock(product)} disabled={busy}>
                                Add
                              </button>
                              <button
                                type="button"
                                onClick={() => setRestock({ id: null, quantity: '1' })}
                                disabled={busy}
                              >
                                Cancel
                              </button>
                            </div>
                          </div>
                        ) : (
                          <>
                            <div>{product.availableStock} available</div>
                            <div className="hint">
                              {product.totalStock} total · {product.reservedStock} reserved
                            </div>
                          </>
                        )}
                      </td>
                      <td>
                        <span className={`badge ${badgeClass(product.status)}`}>{product.status}</span>
                        {!isEditing && (
                          <div className="compact-row">
                            <select
                              aria-label={`Status for ${product.sku}`}
                              value={draftStatus}
                              onChange={(event) =>
                                setStatusDrafts((drafts) => ({
                                  ...drafts,
                                  [product.id]: event.target.value,
                                }))
                              }
                            >
                              {STATUSES.map((status) => (
                                <option key={status} value={status}>
                                  {status}
                                </option>
                              ))}
                            </select>
                            <button
                              type="button"
                              onClick={() => applyStatus(product)}
                              disabled={busy || draftStatus === product.status}
                            >
                              Apply
                            </button>
                          </div>
                        )}
                      </td>
                      <td>
                        <div className="compact-row">
                          {isEditing ? (
                            <>
                              <button
                                type="button"
                                className="primary"
                                onClick={() => saveEdit(product.id)}
                                disabled={busy}
                              >
                                Save
                              </button>
                              <button type="button" onClick={() => setEdit(null)} disabled={busy}>
                                Cancel
                              </button>
                            </>
                          ) : (
                            <>
                              <button
                                type="button"
                                onClick={() =>
                                  setEdit({ id: product.id, values: { ...product } })
                                }
                                disabled={busy}
                              >
                                Edit
                              </button>
                              <button
                                type="button"
                                onClick={() => setRestock({ id: product.id, quantity: '1' })}
                                disabled={busy}
                              >
                                Restock
                              </button>
                            </>
                          )}
                        </div>
                        <div className="hint">Added {formatDate(product.createdAt)}</div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        <Pager page={page} onPageChange={goToPage} disabled={busy} />
      </div>
    </div>
  )
}
