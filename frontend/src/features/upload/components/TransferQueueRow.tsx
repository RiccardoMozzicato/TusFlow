import Icon from '@/components/Icon'
import type { UploadItem, UploadStatus } from '../types'

// Shared grid template so the header and every row stay column-aligned.
export const ROW_GRID = 'grid grid-cols-[auto_1fr_auto_auto_auto] gap-4 px-6'

// The imperative actions a row button can trigger, handled by the queue hook.
export type RowActionKind = 'pause' | 'resume' | 'cancel' | 'dismiss' | 'retry' | 'open'

interface RowAction {
  kind: RowActionKind
  icon: string
  title: string
  className: string
}

const CANCEL_ACTION: RowAction = {
  kind: 'cancel',
  icon: 'close',
  title: 'Cancel',
  className: 'text-ds-secondary hover:bg-ds-error-container hover:text-ds-error',
}

const ACTIONS: Record<UploadStatus, RowAction[]> = {
  uploading: [
    {
      kind: 'pause',
      icon: 'pause',
      title: 'Pause',
      className:
        'text-ds-secondary hover:bg-ds-surface-variant hover:text-ds-on-surface',
    },
    CANCEL_ACTION,
  ],
  paused: [
    {
      kind: 'resume',
      icon: 'play_arrow',
      title: 'Resume',
      className: 'text-ds-primary hover:bg-ds-primary-container',
    },
    CANCEL_ACTION,
  ],
  processing: [],
  completed: [
    {
      kind: 'open',
      icon: 'open_in_new',
      title: 'View details',
      className: 'text-ds-primary hover:bg-ds-primary-container',
    },
    {
      kind: 'dismiss',
      icon: 'close',
      title: 'Remove from list',
      className: 'text-ds-outline hover:bg-ds-surface-variant hover:text-ds-on-surface',
    },
  ],
  failed: [
    {
      kind: 'retry',
      icon: 'refresh',
      title: 'Retry',
      className: 'text-ds-primary hover:bg-ds-primary-container',
    },
    CANCEL_ACTION,
  ],
}

// Background treatment of the whole row, by status.
const ROW_TONE: Record<UploadStatus, string> = {
  uploading: 'hover:bg-ds-surface-container-lowest',
  paused:
    'bg-ds-surface-container-lowest opacity-80 transition-opacity hover:opacity-100',
  processing: 'hover:bg-ds-surface-container-lowest',
  completed: 'bg-ds-surface-container-lowest',
  failed: 'bg-ds-error-container/10 hover:bg-ds-surface-container-lowest',
}

// Tone pairs per status: [solid confirmed fill, lighter in-flight fill].
const FILL: Record<Exclude<UploadStatus, 'processing'>, { solid: string; buffered: string }> = {
  uploading: { solid: 'bg-ds-primary', buffered: 'bg-ds-primary/30' },
  paused: { solid: 'bg-ds-outline', buffered: 'bg-ds-outline/30' },
  completed: { solid: 'bg-ds-tertiary', buffered: 'bg-ds-tertiary/30' },
  failed: { solid: 'bg-ds-error', buffered: 'bg-ds-error/30' },
}

function ProgressBar({ item }: { item: UploadItem }) {
  if (item.status === 'processing') {
    return (
      <div className="relative h-1.5 w-full overflow-hidden rounded-ds-full bg-ds-surface-variant">
        <div className="absolute inset-0 w-1/3 rounded-ds-full bg-ds-tertiary-container shadow-[0_0_8px_rgba(0,181,175,0.4)] animate-[ds-slide_1.5s_ease-in-out_infinite]" />
      </div>
    )
  }

  const { solid, buffered } = FILL[item.status]
  // The lighter fill is bytes flushed to the socket (in flight); the solid fill
  // is bytes the server has actually confirmed via each chunk's PATCH response.
  return (
    <div className="relative h-1.5 w-full overflow-hidden rounded-ds-full bg-ds-surface-variant">
      <div
        className={`absolute inset-y-0 left-0 rounded-ds-full transition-[width] duration-200 ${buffered}`}
        style={{ width: `${Math.round(item.progress * 100)}%` }}
      />
      <div
        className={`absolute inset-y-0 left-0 rounded-ds-full transition-[width] duration-200 ${solid}`}
        style={{ width: `${Math.round(item.confirmedProgress * 100)}%` }}
      />
    </div>
  )
}

function StatusBadge({ status }: { status: UploadStatus }) {
  const base =
    'inline-flex items-center gap-1 rounded-ds-full px-2 py-1 font-ds-label text-ds-label-sm'

  switch (status) {
    case 'uploading':
      return (
        <span className={`${base} bg-ds-primary-container text-ds-on-primary-container`}>
          <span className="h-1.5 w-1.5 animate-pulse rounded-ds-full bg-ds-primary" />
          Uploading
        </span>
      )
    case 'paused':
      return (
        <div className="flex flex-col items-center gap-1">
          <span className={`${base} bg-ds-surface-variant text-ds-on-surface-variant`}>
            Paused
          </span>
          <span className="text-[10px] leading-none text-ds-outline">Resumable</span>
        </div>
      )
    case 'processing':
      return (
        <span className={`${base} bg-ds-tertiary-container text-ds-on-tertiary-container`}>
          <Icon name="sync" className="animate-spin text-[14px]" />
          Processing
        </span>
      )
    case 'completed':
      return (
        <span
          className={`${base} border border-ds-tertiary/20 bg-ds-surface-container-high text-ds-tertiary`}
        >
          <Icon name="check_circle" className="text-[14px]" />
          Completed
        </span>
      )
    case 'failed':
      return (
        <span className={`${base} bg-ds-error-container text-ds-on-error-container`}>
          <Icon name="error" className="text-[14px]" />
          Failed
        </span>
      )
  }
}

// Inline callout under a failed row's file name. Gives the failure reason the
// full name-column width instead of squeezing it into the details column.
function ErrorCallout({ message }: { message: string }) {
  return (
    <div className="mt-1 flex items-start gap-2 rounded-ds border border-ds-error/20 bg-ds-error-container/40 px-2.5 py-1.5">
      <Icon name="error" className="mt-px shrink-0 text-[16px] text-ds-error" />
      <p className="text-ds-body-sm leading-snug text-ds-on-error-container">{message}</p>
    </div>
  )
}

export default function TransferQueueRow({
  item,
  last,
  onAction,
}: {
  item: UploadItem
  last: boolean
  onAction: (id: string, kind: RowActionKind) => void
}) {
  const actions = ACTIONS[item.status]
  const isFailed = item.status === 'failed'
  const detailTone = isFailed ? 'text-ds-error' : 'text-ds-on-surface-variant'

  return (
    <div
      className={`${ROW_GRID} items-center py-4 transition-colors ${ROW_TONE[item.status]} ${
        last ? '' : 'border-b border-ds-outline-variant'
      }`}
    >
      <div className={`flex w-8 justify-center ${item.iconClassName}`}>
        <Icon name={item.icon} />
      </div>

      <div className="flex flex-col gap-1">
        <span className="truncate font-medium text-ds-body-sm text-ds-on-surface">
          {item.name}
        </span>
        <ProgressBar item={item} />
        {isFailed && item.errorMessage ? <ErrorCallout message={item.errorMessage} /> : null}
      </div>

      <div
        className={`flex w-48 flex-col items-end font-ds-label text-ds-label-sm ${detailTone}`}
      >
        <span>{item.detailPrimary}</span>
        <span>{item.detailSecondary}</span>
      </div>

      <div className="flex w-24 justify-center">
        <StatusBadge status={item.status} />
      </div>

      <div className="flex w-20 justify-center gap-1">
        {actions.map((action) => (
          <button
            key={action.kind}
            type="button"
            title={action.title}
            aria-label={action.title}
            onClick={() => onAction(item.id, action.kind)}
            className={`rounded-ds p-1 transition-colors ${action.className}`}
          >
            <Icon name={action.icon} filled={false} className="text-[20px]" />
          </button>
        ))}
      </div>
    </div>
  )
}
