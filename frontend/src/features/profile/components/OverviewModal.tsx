import { useEffect, useId, type ReactNode } from 'react'
import Icon from '@/components/Icon'

interface OverviewModalProps {
  title: string
  subtitle?: string
  onClose: () => void
  children: ReactNode
}

// Scrollable modal shell for the admin overview detail lists (users,
// departments). Shares the overlay treatment used elsewhere in the app.
export default function OverviewModal({ title, subtitle, onClose, children }: OverviewModalProps) {
  const titleId = useId()

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
        className="flex max-h-[80vh] w-full max-w-lg flex-col overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-xl"
      >
        <div className="flex items-start justify-between border-b border-ds-outline-variant px-6 py-4">
          <div>
            <h2 id={titleId} className="font-ds-heading text-ds-headline-sm text-ds-on-surface">
              {title}
            </h2>
            {subtitle ? (
              <p className="font-ds-label text-ds-label-sm text-ds-secondary">{subtitle}</p>
            ) : null}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-ds text-ds-secondary transition-colors hover:bg-ds-surface-container-high"
          >
            <Icon name="close" filled={false} />
          </button>
        </div>
        <div className="overflow-y-auto px-6 py-4">{children}</div>
      </div>
    </div>
  )
}
