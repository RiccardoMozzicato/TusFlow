import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'
import Icon from '@/components/Icon'
import type { QuickAction } from '../../types'

interface QuickActionsProps {
  actions: QuickAction[]
}

function ActionBody({ label, description, icon, iconClassName, hoverClassName }: QuickAction) {
  return (
    <>
      <div
        className={`flex h-10 w-10 items-center justify-center rounded-ds-lg transition-colors ${iconClassName} ${hoverClassName}`}
      >
        <Icon name={icon} filled={false} />
      </div>
      <div className="text-left">
        <p className="font-ds-label text-ds-label-md font-bold text-ds-on-surface">{label}</p>
        <p className="font-ds-label text-ds-label-sm text-ds-on-surface-variant">{description}</p>
      </div>
    </>
  )
}

const ROW_CLASS =
  'group flex w-full items-center gap-4 rounded-ds-lg border border-ds-outline-variant p-4 transition-all hover:bg-ds-surface-container-low'

// Manager quick-actions panel. Items without a `to` are placeholders until
// those pages exist.
export default function QuickActions({ actions }: QuickActionsProps) {
  return (
    <div className="space-y-6 rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-8 shadow-sm">
      <h3 className="font-ds-heading text-ds-headline-sm text-ds-primary">Quick Actions</h3>
      <div className="space-y-4">
        {actions.map((action): ReactNode =>
          action.to ? (
            <Link key={action.label} to={action.to} className={ROW_CLASS}>
              <ActionBody {...action} />
            </Link>
          ) : (
            <button key={action.label} type="button" className={ROW_CLASS}>
              <ActionBody {...action} />
            </button>
          ),
        )}
      </div>
    </div>
  )
}
