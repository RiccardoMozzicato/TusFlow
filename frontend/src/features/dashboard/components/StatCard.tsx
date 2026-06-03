import Icon from '@/components/Icon'
import type { StatCard as StatCardData } from '../types'

export default function StatCard({
  label,
  value,
  note,
  noteTone = 'muted',
  icon,
  iconClassName,
  progress,
}: StatCardData) {
  return (
    <div className="relative flex flex-col gap-4 overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-5 shadow-[0px_4px_6px_-1px_rgba(0,0,0,0.02)]">
      {progress !== undefined ? (
        <div className="absolute bottom-0 left-0 h-1 w-full bg-ds-surface-variant">
          <div
            className="h-full bg-ds-primary"
            style={{ width: `${Math.round(progress * 100)}%` }}
          />
        </div>
      ) : null}

      <div className="flex items-start justify-between">
        <span className="font-ds-label text-ds-label-md uppercase tracking-wider text-ds-secondary">
          {label}
        </span>
        <div
          className={`flex h-8 w-8 items-center justify-center rounded-ds-full ${iconClassName}`}
        >
          <Icon name={icon} className="text-[20px]" />
        </div>
      </div>

      <div className="flex items-baseline gap-2">
        <span className="font-ds-heading text-ds-display-lg text-ds-on-surface">
          {value}
        </span>
        {note ? (
          <span
            className={
              noteTone === 'error'
                ? 'font-ds-label text-ds-label-sm text-ds-error'
                : 'font-ds-label text-ds-label-sm text-ds-secondary'
            }
          >
            {note}
          </span>
        ) : null}
      </div>
    </div>
  )
}
