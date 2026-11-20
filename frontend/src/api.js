const BASE = import.meta.env.VITE_API_BASE ?? '/api'

const TOKEN_KEY = 'dpp.accessToken'
const REFRESH_KEY = 'dpp.refreshToken'

export function getAccessToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY)
}

export function setTokens({ accessToken, refreshToken }) {
  if (accessToken) localStorage.setItem(TOKEN_KEY, accessToken)
  if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
}

export function clearTokens() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

export class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message ?? `Request failed with status ${status}`)
    this.status = status
    this.code = code
    this.details = details
  }
}

async function parseError(response) {
  let body = null
  try {
    body = await response.json()
  } catch {
    body = null
  }
  const code = body?.code ?? body?.errorCode ?? 'UNKNOWN'
  const message = body?.message ?? response.statusText
  return new ApiError(response.status, code, message, body?.details)
}

async function refreshTokens() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return false
  const response = await fetch(`${BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })
  if (!response.ok) {
    clearTokens()
    return false
  }
  setTokens(await response.json())
  return true
}

function pageQuery({ page = 0, size = 50, status } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status && status !== 'ALL') params.set('status', status)
  return params.toString()
}

async function request(path, { method = 'GET', body, auth = true, headers = {}, retry = true } = {}) {
  const finalHeaders = { ...headers }
  if (body !== undefined) finalHeaders['Content-Type'] = 'application/json'
  if (auth) {
    const token = getAccessToken()
    if (token) finalHeaders.Authorization = `Bearer ${token}`
  }

  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: finalHeaders,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (response.status === 401 && auth && retry) {
    const refreshed = await refreshTokens()
    if (refreshed) {
      return request(path, { method, body, auth, headers, retry: false })
    }
  }

  if (!response.ok) {
    throw await parseError(response)
  }

  if (response.status === 204) return null
  const text = await response.text()
  return text ? JSON.parse(text) : null
}

export const api = {
  register: (email, password) =>
    request('/auth/register', { method: 'POST', body: { email, password }, auth: false }),

  login: (email, password) =>
    request('/auth/login', { method: 'POST', body: { email, password }, auth: false }),

  me: () => request('/auth/me'),

  listProducts: ({ page = 0, size = 50 } = {}) =>
    request(`/products?page=${page}&size=${size}`),

  listOrders: ({ page = 0, size = 50 } = {}) =>
    request(`/orders?page=${page}&size=${size}`),

  createOrder: (items) =>
    request('/orders', { method: 'POST', body: { items } }),

  cancelOrder: (id) =>
    request(`/orders/${id}/cancel`, { method: 'POST' }),

  createPayment: (orderId, amount, currency, idempotencyKey) =>
    request('/payments', {
      method: 'POST',
      body: { orderId, amount, currency },
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
    }),

  findPaymentByOrder: (orderId) => request(`/payments/by-order/${orderId}`),

  confirmPayment: (id, outcome) =>
    request(`/payments/${id}/confirm`, { method: 'POST', body: { outcome } }),

  listNotifications: ({ page = 0, size = 50 } = {}) =>
    request(`/notifications?page=${page}&size=${size}`),

  // Administrator listings and inspection.
  listAllOrders: ({ page = 0, size = 50, status } = {}) =>
    request(`/admin/orders?${pageQuery({ page, size, status })}`),

  getOrder: (id) => request(`/admin/orders/${id}`),

  listAdminProducts: ({ page = 0, size = 50 } = {}) =>
    request(`/admin/products?${pageQuery({ page, size })}`),

  createProduct: (payload) =>
    request('/admin/products', { method: 'POST', body: payload }),

  updateProduct: (id, payload) =>
    request(`/admin/products/${id}`, { method: 'PUT', body: payload }),

  changeProductStatus: (id, status) =>
    request(`/admin/products/${id}/status`, { method: 'PATCH', body: { status } }),

  restockProduct: (id, quantity) =>
    request(`/admin/products/${id}/restock`, { method: 'POST', body: { quantity } }),

  // Payment history (customer) and payment inspection (administrator).
  listMyPayments: ({ page = 0, size = 50 } = {}) =>
    request(`/payments?${pageQuery({ page, size })}`),

  getPayment: (id) => request(`/payments/${id}`),

  listAdminPayments: ({ page = 0, size = 50 } = {}) =>
    request(`/admin/payments?${pageQuery({ page, size })}`),

  getAdminPayment: (id) => request(`/admin/payments/${id}`),

  adminRefund: (id, amount, reason) => {
    const body = { reason }
    if (amount !== undefined && amount !== null && amount !== '') body.amount = Number(amount)
    return request(`/admin/payments/${id}/refund`, { method: 'POST', body })
  },

  listAdminNotifications: ({ page = 0, size = 50 } = {}) =>
    request(`/admin/notifications?${pageQuery({ page, size })}`),
}
