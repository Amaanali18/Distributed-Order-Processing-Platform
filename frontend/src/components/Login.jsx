import { useState } from 'react'
import { api, setTokens } from '../api.js'

export default function Login({ onAuthenticated }) {
  const [mode, setMode] = useState('login')
  const [email, setEmail] = useState('customer@dpp.local')
  const [password, setPassword] = useState('CustomerPass123!')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function submit(event) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      if (mode === 'register') {
        await api.register(email, password)
      }
      const tokens = await api.login(email, password)
      setTokens(tokens)
      const user = await api.me()
      onAuthenticated(user)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="center">
      <div className="card">
        <div className="tabs">
          <button
            className={`tab ${mode === 'login' ? 'active' : ''}`}
            onClick={() => setMode('login')}
            type="button"
          >
            Sign in
          </button>
          <button
            className={`tab ${mode === 'register' ? 'active' : ''}`}
            onClick={() => setMode('register')}
            type="button"
          >
            Register
          </button>
        </div>

        {error && <div className="notice error">{error}</div>}

        <form onSubmit={submit}>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={8}
              required
            />
          </div>
          <button className="primary" type="submit" disabled={busy} style={{ width: '100%' }}>
            {busy ? 'Working…' : mode === 'register' ? 'Create account' : 'Sign in'}
          </button>
        </form>

        <p className="hint" style={{ marginTop: '1rem' }}>
          Demo admin: admin@dpp.local / AdminPass123!
        </p>
      </div>
    </div>
  )
}
