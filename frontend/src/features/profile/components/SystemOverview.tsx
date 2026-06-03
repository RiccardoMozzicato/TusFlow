import Icon from '@/components/Icon'
import type { OverviewStat, StorageInfo } from '../types'

interface SystemOverviewProps {
  title?: string
  stats: OverviewStat[]
  storage: StorageInfo
}

function OverviewTile({ label, value, icon, iconClassName, note, progress }: OverviewStat) {
  return (
    <div className="flex items-center justify-between rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-6 transition-colors hover:border-ds-primary">
      <div>
        <p className="font-ds-label text-ds-label-md text-ds-secondary">{label}</p>
        <p className="mt-1 font-ds-heading text-ds-headline-md text-ds-primary">{value}</p>
        {note ? <div className="mt-1 text-ds-label-sm">{note}</div> : null}
        {progress !== undefined ? (
          <div className="mt-3 h-1 w-full overflow-hidden rounded-ds-full bg-ds-surface-container">
            <div
              className="h-full bg-ds-tertiary-container"
              style={{ width: `${Math.round(progress * 100)}%` }}
            />
          </div>
        ) : null}
      </div>
      <div className={`rounded-ds-xl p-4 ${iconClassName}`}>
        <Icon name={icon} className="text-[32px]" />
      </div>
    </div>
  )
}

function StorageTile({ label, usage, fraction, consumedLabel, availableLabel }: StorageInfo) {
  return (
    <div className="rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-6 md:col-span-2">
      <div className="mb-4 flex items-start justify-between">
        <div>
          <p className="font-ds-label text-ds-label-md text-ds-secondary">{label}</p>
          <p className="mt-1 font-ds-heading text-ds-headline-sm text-ds-primary">{usage}</p>
        </div>
        <Icon name="storage" filled={false} className="text-ds-secondary" />
      </div>
      <div className="mb-1 h-3 w-full overflow-hidden rounded-ds-full bg-ds-surface-container">
        <div
          className="h-full rounded-ds-full bg-ds-primary"
          style={{ width: `${Math.round(fraction * 100)}%` }}
        />
      </div>
      <div className="flex justify-between font-ds-label text-ds-label-sm text-ds-on-surface-variant">
        <span>{consumedLabel}</span>
        <span>{availableLabel}</span>
      </div>
    </div>
  )
}

// Bento-style overview grid: two stat tiles plus a wide storage tile. Scope
// (global vs department) is decided by the caller via the data passed in.
export default function SystemOverview({
  title = 'System Overview',
  stats,
  storage,
}: SystemOverviewProps) {
  return (
    <div className="space-y-6">
      <h3 className="font-ds-heading text-ds-headline-sm text-ds-on-surface">{title}</h3>
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        {stats.map((stat) => (
          <OverviewTile key={stat.label} {...stat} />
        ))}
        <StorageTile {...storage} />
      </div>
    </div>
  )
}
