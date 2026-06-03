import Icon from '@/components/Icon'
import type { StaffActivity } from '../../types'

interface RecentActivityProps {
  items: StaffActivity[]
}

function ActivityRow({ icon, iconClassName, message, meta }: StaffActivity) {
  return (
    <div className="group flex items-center gap-8 rounded-ds-lg p-4 transition-colors hover:bg-ds-surface-container">
      <div className={`flex h-12 w-12 items-center justify-center rounded-ds-full ${iconClassName}`}>
        <Icon name={icon} filled={false} />
      </div>
      <div className="min-w-0 flex-1">
        <p className="font-ds-body text-ds-body-md text-ds-on-surface">{message}</p>
        <p className="font-ds-body text-ds-body-sm text-ds-on-surface-variant">{meta}</p>
      </div>
      <div className="flex gap-2 opacity-0 transition-opacity group-hover:opacity-100">
        <button
          type="button"
          aria-label="Preview"
          className="rounded-ds-full p-2 text-ds-secondary hover:bg-white"
        >
          <Icon name="visibility" filled={false} className="text-sm" />
        </button>
        <button
          type="button"
          aria-label="More actions"
          className="rounded-ds-full p-2 text-ds-secondary hover:bg-white"
        >
          <Icon name="more_vert" filled={false} className="text-sm" />
        </button>
      </div>
    </div>
  )
}

// Staff recent-activity feed.
export default function RecentActivity({ items }: RecentActivityProps) {
  return (
    <div className="rounded-ds-xl border border-ds-outline-variant bg-white p-8 shadow-sm">
      <div className="mb-8 flex items-center justify-between">
        <h3 className="font-ds-heading text-ds-headline-md text-ds-primary">Recent Activity</h3>
        <button
          type="button"
          className="font-ds-label text-ds-label-md text-ds-primary hover:underline"
        >
          View All
        </button>
      </div>
      <div className="space-y-4">
        {items.map((item, i) => (
          <ActivityRow key={`${item.meta}-${i}`} {...item} />
        ))}
      </div>
    </div>
  )
}
