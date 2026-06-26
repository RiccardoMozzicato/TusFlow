import type { UploadItem } from '../types'
import TransferQueueRow, { ROW_GRID, type RowActionKind } from './TransferQueueRow'

const HEADERS = [
  { label: 'Type', className: 'w-8' },
  { label: 'File Name & Progress', className: '' },
  { label: 'Details', className: 'w-48 text-right' },
  { label: 'Status', className: 'w-24 text-center' },
  { label: 'Actions', className: 'w-20 text-center' },
]

export default function TransferQueue({
  items,
  onAction,
}: {
  items: UploadItem[]
  onAction: (id: string, kind: RowActionKind) => void
}) {
  return (
    <div className="overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface shadow-[0px_4px_6px_-1px_rgba(0,0,0,0.02)]">
      <div
        className={`${ROW_GRID} border-b border-ds-outline-variant bg-ds-surface-container-low py-3 font-ds-label text-ds-label-sm uppercase tracking-wider text-ds-on-surface-variant`}
      >
        {HEADERS.map((header) => (
          <div key={header.label} className={header.className}>
            {header.label}
          </div>
        ))}
      </div>

      {items.length === 0 ? (
        <p className="px-6 py-10 text-center text-ds-body-sm text-ds-on-surface-variant">
          No transfers yet. Drop files above to start uploading.
        </p>
      ) : (
        items.map((item, index) => (
          <TransferQueueRow
            key={item.id}
            item={item}
            last={index === items.length - 1}
            onAction={onAction}
          />
        ))
      )}
    </div>
  )
}
