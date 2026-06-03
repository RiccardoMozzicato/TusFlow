import Icon from '@/components/Icon'
import type { WorkspaceCounter, WorkspaceStorage } from '../../types'

interface WorkspaceCardProps {
  counter: WorkspaceCounter
  storage: WorkspaceStorage
}

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

// Staff "My Workspace" card: document count + storage usage tiles.
export default function WorkspaceCard({ counter, storage }: WorkspaceCardProps) {
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

      <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
        <div className="flex flex-col gap-4 rounded-ds-xl bg-ds-surface-container-low p-6">
          <TileHeader label={counter.label} icon={counter.icon} />
          <div className="flex items-baseline gap-1.5">
            <span className="text-4xl font-bold text-ds-primary">{counter.value}</span>
            <span className="font-ds-label text-ds-label-md text-ds-on-tertiary-container">
              {counter.note}
            </span>
          </div>
        </div>

        <div className="flex flex-col gap-4 rounded-ds-xl bg-ds-surface-container-low p-6">
          <TileHeader label={storage.label} icon={storage.icon} />
          <div className="flex flex-col gap-1.5">
            <div className="flex items-baseline gap-1.5">
              <span className="text-4xl font-bold text-ds-primary">{storage.value}</span>
              <span className="font-ds-label text-ds-label-md text-ds-secondary">
                {storage.unit}
              </span>
            </div>
            <div className="mt-2 h-2 w-full overflow-hidden rounded-ds-full bg-ds-outline-variant">
              <div
                className="h-full bg-ds-primary"
                style={{ width: `${Math.round(storage.fraction * 100)}%` }}
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
