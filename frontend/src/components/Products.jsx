import { useEffect, useState } from 'react'
import { api } from '../api.js'

export default function Products({ onAdd, refreshKey = 0 }) {
  const [products, setProducts] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    api
      .listProducts()
      .then((page) => {
        if (active) setProducts(page.content ?? [])
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
  }, [refreshKey])

  if (loading) return <p className="muted">Loading products…</p>
  if (error) return <div className="notice error">{error}</div>
  if (products.length === 0) {
    return <div className="notice info">No products available. Seed the catalog to demo ordering.</div>
  }

  return (
    <div className="grid">
      {products.map((product) => (
        <div className="card" key={product.id}>
          <h3>{product.name}</h3>
          <div className="sku">{product.sku}</div>
          <p className="muted" style={{ fontSize: '0.85rem', minHeight: '2.4em' }}>
            {product.description}
          </p>
          <div className="price">
            {product.currency} {Number(product.price).toFixed(2)}
          </div>
          <div className="row">
            <span className="hint">
              {product.availableStock} in stock
            </span>
            <button
              className="primary"
              onClick={() => onAdd(product)}
              disabled={product.availableStock <= 0}
            >
              Add to cart
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}
