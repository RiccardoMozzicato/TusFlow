import Icon from '@/components/Icon'
import type { ActivityItem } from '../types'
import { ACTIVITY } from '../mockData'

function ActivityRow({ item }: { item: ActivityItem }) {
  return (
    <div className="relative mb-6 flex gap-4 last:mb-0">
      <div
        className={`relative z-10 flex h-8 w-8 items-center justify-center rounded-ds-full border border-ds-outline-variant bg-ds-surface ${
          item.tone === 'error' ? 'text-ds-error' : 'text-ds-secondary'
        }`}
      >
        <Icon name={item.icon} className="text-[16px]" />
      </div>
      <div className="flex flex-1 flex-col pt-1">
        <p className="text-ds-body-sm text-ds-on-surface">{item.message}</p>
        <span className="mt-1 font-ds-label text-ds-label-sm text-ds-secondary">
          {item.time}
        </span>
      </div>
    </div>
  )
}

export default function DepartmentActivity() {
  return (
    <div className="col-span-12 flex flex-col gap-6 xl:col-span-4">
      <div className="flex h-full flex-col rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-[0px_4px_6px_-1px_rgba(0,0,0,0.02)]">
        <div className="border-b border-ds-outline-variant bg-ds-surface-bright px-6 py-5">
          <h3 className="font-ds-heading text-ds-headline-sm text-ds-on-surface">
            Department Activity
          </h3>
        </div>
        <div className="flex-1 overflow-y-auto p-6">
          {/* Vertical timeline rail behind the icons */}
          <div className="relative before:absolute before:inset-y-0 before:left-4 before:w-px before:bg-ds-outline-variant/50">
            {ACTIVITY.map((item, index) => (
              <ActivityRow key={index} item={item} />
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
