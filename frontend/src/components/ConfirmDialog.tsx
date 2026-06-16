import { useEffect, useId } from 'react'
import Icon from '@/components/Icon'

interface ConfirmDialogProps {
  title: string
  message: string
  confirmLabel: string
  /** Styles the confirm button as a destructive action. */
  destructive?: boolean
  isBusy: boolean
  onConfirm: () => void
  onCancel: () => void
}

// Minimal confirmation modal, sharing the overlay treatment of UserFormModal.
export default function ConfirmDialog({
  title,
  message,
  confirmLabel,
  destructive = false,
  isBusy,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const titleId = useId()

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onCancel])

  const confirmClass = destructive
    ? 'bg-ds-error text-ds-on-error hover:opacity-90'
    : 'bg-ds-primary text-ds-on-primary hover:opacity-90'

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm"
      onClick={onCancel}
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-sm rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-6 shadow-xl"
      >
        <h2 id={titleId} className="font-ds-heading text-ds-headline-sm text-ds-on-surface">
          {title}
        </h2>
        <p className="mt-2 font-ds-body text-ds-body-sm text-ds-on-surface-variant">{message}</p>

        <div className="mt-6 flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={isBusy}
            className="rounded-ds-lg border border-ds-outline px-5 py-2.5 font-ds-label text-ds-label-md text-ds-on-surface transition-colors hover:bg-ds-surface-container disabled:opacity-60"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={isBusy}
            className={`flex items-center gap-2 rounded-ds-lg px-5 py-2.5 font-ds-label text-ds-label-md transition-opacity disabled:opacity-60 ${confirmClass}`}
          >
            {isBusy ? (
              <Icon name="progress_activity" filled={false} className="animate-spin text-[18px]" />
            ) : null}
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
