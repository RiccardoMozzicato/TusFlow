import Icon from '@/components/Icon'
import type { StorageSummary } from '@/features/users/storageSummary'

interface WorkspaceCardProps {
  /** Owned-document count; null while loading or on error. */
  documentCount: number | null
  /** Storage usage view model; null while loading or on error. */
  storage: StorageSummary | null
  /** Set when the underlying fetch failed. */
  error?: string | null
}

const PLACEHOLDER = '—'

function TileHeader({ label, icon }: { label: string; icon: string }) {
  return (
    <div className="flex items-center justify-between">
      <span className="font-ds-label text-ds-label-md uppercase text-ds-secondary">
        {label}
      </span>
      <Icon name={icon} filled={false} className="text-xl text-ds-primary" />
    </div>
  )
}

// Staff "My Workspace" card: owned-document count + storage usage tiles, fed by
// live data (see useWorkspaceStats). Renders placeholders while loading and an
// error caption when the fetch failed.
export default function WorkspaceCard({ documentCount, storage, error }: WorkspaceCardProps) {
  const countLabel = documentCount === null ? PLACEHOLDER : documentCount.toLocaleString()
  const storageUsed = storage ? storage.usedLabel : PLACEHOLDER
  const storageSuffix = storage ? (storage.quotaLabel ? `/ ${storage.quotaLabel}` : 'used') : ''

  return (
    <div className="rounded-ds-xl border border-ds-outline-variant bg-white p-8 shadow-sm transition-shadow hover:shadow-md">
      <div className="mb-8 flex items-start justify-between">
        <div>
          <h3 className="mb-1 font-ds-heading text-ds-headline-md text-ds-primary">
            My Workspace
          </h3>
          <p className="font-ds-body text-ds-body-sm text-ds-on-surface-variant">
            Personal repository overview
          </p>
        </div>
        <Icon name="work" filled={false} className="text-ds-primary-container" />
      </div>

      {error ? (
        <p className="mb-6 font-ds-body text-ds-body-sm text-ds-error">{error}</p>
      ) : null}

      <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
        <div className="flex flex-col gap-4 rounded-ds-xl bg-ds-surface-container-low p-6">
          <TileHeader label="Document Count" icon="description" />
          <span className="text-4xl font-bold text-ds-primary">{countLabel}</span>
        </div>

        <div className="flex flex-col gap-4 rounded-ds-xl bg-ds-surface-container-low p-6">
          <TileHeader label="Storage Used" icon="cloud_queue" />
          <div className="flex flex-col gap-1.5">
            <div className="flex items-baseline gap-1.5">
              <span className="text-4xl font-bold text-ds-primary">{storageUsed}</span>
              <span className="font-ds-label text-ds-label-md text-ds-secondary">
                {storageSuffix}
              </span>
            </div>
            <div className="mt-2 h-2 w-full overflow-hidden rounded-ds-full bg-ds-outline-variant">
              <div
                className="h-full bg-ds-primary"
                style={{ width: `${Math.round((storage?.fraction ?? 0) * 100)}%` }}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}