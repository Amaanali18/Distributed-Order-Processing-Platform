import { useCallback, useEffect, useState } from 'react'
import { api, clearTokens, getAccessToken } from './api.js'
import AdminOrders from './components/AdminOrders.jsx'
import AdminPayments from './components/AdminPayments.jsx'
import AdminProducts from './components/AdminProducts.jsx'
import Cart from './components/Cart.jsx'
import Login from './components/Login.jsx'
import Notifications from './components/Notifications.jsx'
import OpsDashboard from './components/OpsDashboard.jsx'
import Orders from './components/Orders.jsx'
import PaymentHistory from './components/PaymentHistory.jsx'
import Products from './components/Products.jsx'

const CUSTOMER_TABS = ['Products', 'Cart', 'Orders', 'Payments']
const ADMIN_TABS = ['Ops', 'Admin Products', 'Admin Orders', 'Admin Payments']

function hasAdminRole(user) {
  const roles = Array.isArray(user?.roles) ? user.roles : []
  return roles.some((role) => String(role).toUpperCase().replace(/^ROLE_/, '') === 'ADMIN')
}

export default function App() {
  const [user, setUser] = useState(null)
  const [booting, setBooting] = useState(Boolean(getAccessToken()))
  const [tab, setTab] = useState('Products')

  const [cart, setCart] = useState([])
  const [orders, setOrders] = useState([])
  const [payments, setPayments] = useState([])
  const [paymentsLoading, setPaymentsLoading] = useState(Boolean(getAccessToken()))
  const [paymentsError, setPaymentsError] = useState(null)
  const [notificationRefresh, setNotificationRefresh] = useState(0)
  const [productRefresh, setProductRefresh] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!getAccessToken()) return
    api
      .me()
      .then(setUser)
      .catch(() => clearTokens())
      .finally(() => setBooting(false))
  }, [])

  const loadOrders = useCallback(async () => {
    try {
      const page = await api.listOrders()
      setOrders(page.content ?? [])
    } catch (err) {
      setError(err.message)
    }
  }, [])

  const loadPayments = useCallback(async () => {
    setPaymentsLoading(true)
    setPaymentsError(null)
    try {
      const page = await api.listMyPayments()
      setPayments(page.content ?? [])
    } catch (err) {
      setPaymentsError(err.message)
    } finally {
      setPaymentsLoading(false)
    }
  }, [])

  const refreshAll = useCallback(async () => {
    await Promise.all([loadOrders(), loadPayments()])
    setNotificationRefresh((value) => value + 1)
  }, [loadOrders, loadPayments])

  useEffect(() => {
    if (!user) return
    let active = true
    api
      .listOrders()
      .then((page) => {
        if (active) setOrders(page.content ?? [])
      })
      .catch((err) => {
        if (active) setError(err.message)
      })
    api
      .listMyPayments()
      .then((page) => {
        if (active) setPayments(page.content ?? [])
      })
      .catch((err) => {
        if (active) setPaymentsError(err.message)
      })
      .finally(() => {
        if (active) setPaymentsLoading(false)
      })
    return () => {
      active = false
    }
  }, [user])

  function signOut() {
    clearTokens()
    setUser(null)
    setCart([])
    setOrders([])
    setPayments([])
    setPaymentsLoading(false)
    setError(null)
    setPaymentsError(null)
    setTab('Products')
  }

  function addToCart(product) {
    setError(null)
    setCart((prev) => {
      const existing = prev.find((item) => item.productId === product.id)
      if (existing) {
        return prev.map((item) =>
          item.productId === product.id ? { ...item, quantity: item.quantity + 1 } : item,
        )
      }
      return [
        ...prev,
        {
          productId: product.id,
          sku: product.sku,
          name: product.name,
          price: product.price,
          currency: product.currency,
          quantity: 1,
        },
      ]
    })
  }

  function changeQuantity(productId, quantity) {
    setCart((prev) =>
      prev.map((item) =>
        item.productId === productId ? { ...item, quantity: Math.max(1, quantity || 1) } : item,
      ),
    )
  }

  function removeFromCart(productId) {
    setCart((prev) => prev.filter((item) => item.productId !== productId))
  }

  async function checkout() {
    setError(null)
    setBusy(true)
    try {
      await api.createOrder(
        cart.map((item) => ({ productId: item.productId, quantity: item.quantity })),
      )
      setCart([])
      await refreshAll()
      setProductRefresh((value) => value + 1)
      setTab('Orders')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (booting) {
    return <p className="muted" style={{ textAlign: 'center', marginTop: '20vh' }}>Loading…</p>
  }

  if (!user) {
    return <Login onAuthenticated={setUser} />
  }

  const isAdmin = hasAdminRole(user)
  const tabs = [
    ...CUSTOMER_TABS,
    ...(isAdmin ? ADMIN_TABS : []),
    'Notifications',
  ]

  return (
    <div className="app">
      <div className="topbar">
        <h1>Distributed Order &amp; Payment Platform</h1>
        <div className="row" style={{ gap: '0.75rem' }}>
          <span className={`badge ${isAdmin ? 'warn' : 'neutral'}`}>
            {isAdmin ? 'ADMIN' : 'CUSTOMER'}
          </span>
          <span className="who">{user.email}</span>
          <button onClick={signOut}>Sign out</button>
        </div>
      </div>

      <div className="tabs">
        {tabs.map((name) => (
          <button
            key={name}
            className={`tab ${tab === name ? 'active' : ''}`}
            onClick={() => setTab(name)}
          >
            {name}
            {name === 'Cart' && cart.length > 0 ? ` (${cart.length})` : ''}
          </button>
        ))}
      </div>

      {tab === 'Products' && <Products onAdd={addToCart} refreshKey={productRefresh} />}

      {tab === 'Cart' && (
        <Cart
          items={cart}
          onChangeQuantity={changeQuantity}
          onRemove={removeFromCart}
          onCheckout={checkout}
          busy={busy}
          error={error}
        />
      )}

      {tab === 'Orders' && (
        <>
          {error && <div className="notice error">{error}</div>}
          <div className="row" style={{ marginBottom: '1rem' }}>
            <p className="hint" style={{ margin: 0 }}>
              Placing an order publishes <code>OrderCreated</code>; choosing Succeed or Fail below
              drives the simulated payment outcome for the order → inventory → payment saga.
            </p>
            <button onClick={() => refreshAll()}>Refresh</button>
          </div>
          <Orders
            orders={orders}
            payments={payments}
            onRefresh={refreshAll}
            onViewPayments={() => setTab('Payments')}
          />
        </>
      )}

      {tab === 'Payments' && (
        <PaymentHistory
          payments={payments}
          loading={paymentsLoading}
          error={paymentsError}
          onRefresh={loadPayments}
          onViewOrder={() => setTab('Orders')}
        />
      )}

      {isAdmin && tab === 'Ops' && <OpsDashboard />}

      {isAdmin && tab === 'Admin Products' && (
        <AdminProducts onChanged={() => setProductRefresh((value) => value + 1)} />
      )}

      {isAdmin && tab === 'Admin Orders' && <AdminOrders />}

      {isAdmin && tab === 'Admin Payments' && <AdminPayments onChanged={loadPayments} />}

      {tab === 'Notifications' && (
        <>
          <div className="row" style={{ marginBottom: '1rem' }}>
            <p className="hint" style={{ margin: 0 }}>
              Every event is delivered asynchronously through Kafka.
            </p>
            <button onClick={() => setNotificationRefresh((value) => value + 1)}>Refresh</button>
          </div>
          <Notifications refreshKey={notificationRefresh} />
        </>
      )}
    </div>
  )
}
