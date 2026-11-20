import { useEffect, useState } from 'react'
import { api } from '../api.js'

const STATUS_CLASS = {
  SENT: 'ok',
  DELIVERED: 'ok',
  PENDING: 'warn',
  FAILED: 'err',
}

export default function Notifications({ refreshKey }) {
  const [notifications, setNotifications] = useState([])
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    api
      .listNotifications()
      .then((page) => {
        if (active) setNotifications(page.content ?? [])
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

  if (loading) return <p className="muted">Loading notifications…</p>
  if (error) return <div className="notice error">{error}</div>
  if (notifications.length === 0) {
    return (
      <div className="notice info">
        No notifications yet. They appear here after the order/payment events flow through Kafka.
      </div>
    )
  }

  return (
    <div className="stack">
      {notifications.map((note) => (
        <div className="card" key={note.id}>
          <div className="row">
            <div>
              <h3>{note.subject}</h3>
              <div className="hint">
                {note.eventType} · {note.channel} · {note.recipient}
              </div>
            </div>
            <span className={`badge ${STATUS_CLASS[note.status] ?? 'neutral'}`}>{note.status}</span>
          </div>
          <p className="muted" style={{ marginBottom: 0, fontSize: '0.85rem' }}>
            {note.body}
          </p>
        </div>
      ))}
    </div>
  )
}
