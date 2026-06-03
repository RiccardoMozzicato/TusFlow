import Icon from '@/components/Icon'
import type { AdminAction, AdminActionStatus } from '../types'

interface AdminActionsTableProps {
  actions: AdminAction[]
}

const STATUS_LABEL: Record<AdminActionStatus, string> = {
  success: 'Success',
  in_progress: 'In Progress',
  critical: 'Critical',
}

const STATUS_CLASS: Record<AdminActionStatus, string> = {
  success: 'bg-ds-tertiary-container/10 text-ds-on-tertiary-container',
  in_progress: 'bg-ds-primary-container text-ds-on-primary-container',
  critical: 'bg-ds-error-container text-ds-on-error-container',
}

// Admin-only audit table of recent administrative actions.
export default function AdminActionsTable({ actions }: AdminActionsTableProps) {
  return (
    <section className="space-y-6">
      <div className="flex items-center justify-between">
        <h3 className="font-ds-heading text-ds-headline-sm text-ds-on-surface">
          Recent Administrative Actions
        </h3>
        <button
          type="button"
          className="flex items-center gap-1.5 font-ds-label text-ds-label-md text-ds-primary hover:underline"
        >
          View All History
          <Icon name="open_in_new" filled={false} className="text-[16px]" />
        </button>
      </div>

      <div className="overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead className="bg-ds-surface-container font-ds-label text-ds-label-md text-ds-secondary">
              <tr>
                <th className="px-6 py-4">Action</th>
                <th className="px-6 py-4">Target Entity</th>
                <th className="px-6 py-4">Status</th>
                <th className="px-6 py-4">Timestamp</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ds-outline-variant font-ds-body text-ds-body-sm text-ds-on-surface">
              {actions.map((item) => (
                <tr
                  key={`${item.action}-${item.timestamp}`}
                  className="transition-colors hover:bg-ds-surface-container-low"
                >
                  <td className="px-6 py-4">
                    <span className="flex items-center gap-3">
                      <Icon
                        name={item.icon}
                        className={`text-[20px] ${item.iconClassName}`}
                      />
                      {item.action}
                    </span>
                  </td>
                  <td className="px-6 py-4">{item.target}</td>
                  <td className="px-6 py-4">
                    <span
                      className={`rounded-ds-full px-3 py-1 font-ds-label text-ds-label-sm ${STATUS_CLASS[item.status]}`}
                    >
                      {STATUS_LABEL[item.status]}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-ds-secondary">{item.timestamp}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}
