import type { DocumentVersion } from '../../types'
import DetailSection from './DetailSection'

interface VersionHistoryProps {
  versions: DocumentVersion[]
}

// Version list. The current version is pinned with an accent border; older
// versions are quietly interactive (hover) placeholders for a future restore.
export default function VersionHistory({ versions }: VersionHistoryProps) {
  return (
    <DetailSection
      icon="update"
      title="Versions"
      action={
        <button type="button" className="font-ds-label text-ds-label-sm text-ds-primary hover:underline">
          View All
        </button>
      }
    >
      <div className="flex flex-col gap-2">
        {versions.map((version) =>
          version.isCurrent ? (
            <div
              key={version.id}
              className="flex items-center justify-between rounded-ds border border-ds-primary-fixed-dim bg-ds-surface-container p-2"
            >
              <div className="flex items-center gap-2">
                <span className="rounded-ds bg-white px-1.5 py-0.5 font-ds-label text-ds-label-sm font-bold text-ds-primary shadow-sm">
                  {version.label}
                </span>
                <span className="font-ds-body text-ds-body-sm text-ds-on-surface">(Current)</span>
              </div>
              <span className="font-ds-label text-ds-label-sm text-ds-secondary">{version.date}</span>
            </div>
          ) : (
            <button
              key={version.id}
              type="button"
              className="group flex cursor-pointer items-center justify-between rounded-ds p-2 text-left transition-colors hover:bg-ds-surface"
            >
              <div className="flex items-center gap-2">
                <span className="font-ds-label text-ds-label-sm font-medium text-ds-secondary group-hover:text-ds-on-surface">
                  {version.label}
                </span>
                <span className="font-ds-body text-ds-body-sm text-ds-secondary group-hover:text-ds-on-surface">
                  {version.author}
                </span>
              </div>
              <span className="font-ds-label text-ds-label-sm text-ds-outline">{version.date}</span>
            </button>
          ),
        )}
      </div>
    </DetailSection>
  )
}
