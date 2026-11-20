export default function Pager({ page, onPageChange, disabled = false }) {
  const current = Number(page?.number ?? 0)
  const totalPages = Number(page?.totalPages ?? 0)

  if (totalPages <= 1 && current === 0) return null

  return (
    <div className="pager">
      <button
        type="button"
        onClick={() => onPageChange(current - 1)}
        disabled={disabled || current <= 0}
      >
        Previous
      </button>
      <span className="hint">
        Page {current + 1} of {Math.max(totalPages, 1)}
        {page?.totalElements != null ? ` · ${page.totalElements} total` : ''}
      </span>
      <button
        type="button"
        onClick={() => onPageChange(current + 1)}
        disabled={disabled || (totalPages > 0 && current >= totalPages - 1)}
      >
        Next
      </button>
    </div>
  )
}
