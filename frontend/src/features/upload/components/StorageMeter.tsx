import Icon from '@/components/Icon'
import type { StorageSummary } from '@/features/users/storageSummary'

// Compact storage gauge for the Upload Manager header: used vs quota with a
// slim bar that shifts to the error treatment near the limit, so users see at
// a glance whether the next upload will fit.

interface StorageMeterProps {
  /** Null while loading or unavailable. */
  summary: StorageSummary | null
  hasError: boolean
}

export default function StorageMeter({ summary, hasError }: StorageMeterProps) {
  if (hasError) return null

  return (
    <div
      data-tour="upload-storage"
      className="flex w-64 flex-col gap-2 rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest px-4 py-3"
    >
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1.5 font-ds-label text-ds-label-sm uppercase tracking-wider text-ds-on-surface-variant">
          <Icon name="cloud" filled={false} className="text-[16px]" />
          Storage
        </span>
        {summary?.isNearLimit ? (
          <span className="flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-error">
            <Icon name="warning" className="text-[14px]" />
            Almost full
          </span>
        ) : null}
      </div>

      {summary === null ? (
        <span className="text-ds-body-sm text-ds-on-surface-variant">Loading…</span>
      ) : (
        <>
          <div className="flex items-baseline justify-between">
            <span className="font-medium text-ds-body-sm text-ds-on-surface">
              {summary.usedLabel}
              <span className="font-normal text-ds-on-surface-variant">
                {summary.quotaLabel ? ` of ${summary.quotaLabel}` : ' used'}
              </span>
            </span>
            {summary.freeLabel ? (
              <span className="font-ds-label text-ds-label-sm text-ds-on-surface-variant">
                {summary.freeLabel} free
              </span>
            ) : null}
          </div>

          {summary.quotaLabel ? (
            <div className="h-1.5 w-full overflow-hidden rounded-ds-full bg-ds-surface-variant">
              <div
                className={`h-full rounded-ds-full transition-[width] duration-500 ${
                  summary.isNearLimit ? 'bg-ds-error' : 'bg-ds-primary'
                }`}
                style={{ width: `${Math.round(summary.fraction * 100)}%` }}
              />
            </div>
          ) : null}
        </>
      )}
    </div>
  )
}
