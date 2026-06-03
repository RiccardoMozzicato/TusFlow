import Icon from '@/components/Icon'
import type { UploadItem, UploadStatus } from '../types'

// Shared grid template so the header and every row stay column-aligned.
export const ROW_GRID = 'grid grid-cols-[auto_1fr_auto_auto_auto] gap-4 px-6'

interface RowAction {
  icon: string
  title: string
  className: string
}

const CANCEL_ACTION: RowAction = {
  icon: 'close',
  title: 'Cancel',
  className: 'text-ds-secondary hover:bg-ds-error-container hover:text-ds-error',
}

const ACTIONS: Record<UploadStatus, RowAction[]> = {
  uploading: [
    {
      icon: 'pause',
      title: 'Pause',
      className:
        'text-ds-secondary hover:bg-ds-surface-variant hover:text-ds-on-surface',
    },
    CANCEL_ACTION,
  ],
  paused: [
    {
      icon: 'play_arrow',
      title: 'Resume',
      className: 'text-ds-primary hover:bg-ds-primary-container',
    },
    CANCEL_ACTION,
  ],
  processing: [],
  completed: [
    {
      icon: 'folder_open',
      title: 'Open file location',
      className: 'text-ds-outline hover:bg-ds-surface-variant hover:text-ds-primary',
    },
  ],
  failed: [
    {
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

function ProgressBar({ item }: { item: UploadItem }) {
  if (item.status === 'processing') {
    return (
      <div className="relative h-1.5 w-full overflow-hidden rounded-ds-full bg-ds-surface-variant">
        <div className="absolute inset-0 w-1/3 rounded-ds-full bg-ds-tertiary-container shadow-[0_0_8px_rgba(0,181,175,0.4)] animate-[ds-slide_1.5s_ease-in-out_infinite]" />
      </div>
    )
  }

  const FILL: Record<Exclude<UploadStatus, 'processing'>, string> = {
    uploading: 'bg-ds-primary',
    paused: 'bg-ds-outline',
    completed: 'bg-ds-tertiary',
    failed: 'bg-ds-error',
  }

  return (
    <div className="h-1.5 w-full overflow-hidden rounded-ds-full bg-ds-surface-variant">
      <div
        className={`h-full rounded-ds-full ${FILL[item.status]}`}
        style={{ width: `${Math.round(item.progress * 100)}%` }}
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

export default function TransferQueueRow({
  item,
  last,
}: {
  item: UploadItem
  last: boolean
}) {
  const actions = ACTIONS[item.status]
  const detailTone =
    item.status === 'failed' ? 'text-ds-error' : 'text-ds-on-surface-variant'

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
            key={action.icon}
            type="button"
            title={action.title}
            aria-label={action.title}
            className={`rounded-ds p-1 transition-colors ${action.className}`}
          >
            <Icon name={action.icon} filled={false} className="text-[20px]" />
          </button>
        ))}
      </div>
    </div>
  )
}
