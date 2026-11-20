export default function Cart({ items, onChangeQuantity, onRemove, onCheckout, busy, error }) {
  const total = items.reduce((sum, item) => sum + Number(item.price) * item.quantity, 0)
  const currency = items[0]?.currency ?? 'USD'

  if (items.length === 0) {
    return <div className="notice info">Cart is empty. Add a product from the Products tab.</div>
  }

  return (
    <div className="stack">
      {error && <div className="notice error">{error}</div>}

      <div className="card">
        <table>
          <thead>
            <tr>
              <th>Product</th>
              <th>Unit price</th>
              <th>Qty</th>
              <th>Subtotal</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.productId}>
                <td>
                  <div>{item.name}</div>
                  <div className="sku">{item.sku}</div>
                </td>
                <td>
                  {item.currency} {Number(item.price).toFixed(2)}
                </td>
                <td>
                  <input
                    type="number"
                    min={1}
                    value={item.quantity}
                    onChange={(e) => onChangeQuantity(item.productId, Number(e.target.value))}
                    style={{ width: '4.5rem' }}
                  />
                </td>
                <td>
                  {item.currency} {(Number(item.price) * item.quantity).toFixed(2)}
                </td>
                <td>
                  <button onClick={() => onRemove(item.productId)}>Remove</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="row card">
        <div>
          <div className="muted hint">Order total</div>
          <div className="price">
            {currency} {total.toFixed(2)}
          </div>
        </div>
        <button className="primary" onClick={onCheckout} disabled={busy}>
          {busy ? 'Placing order…' : 'Place order'}
        </button>
      </div>
    </div>
  )
}
