import { UNASSIGNED_DEPARTMENT, type OrgStats } from '@/features/users/orgStats'
import { formatQuota } from '@/features/users/userQuota'
import type { OverviewStat, StorageInfo } from './types'

function plural(n: number, word: string): string {
  return `${n} ${word}${n === 1 ? '' : 's'}`
}

export interface OverviewHandlers {
  onTotalUsers?: () => void
  onDepartments?: () => void
}

// Maps the organization stats onto the System Overview's two stat tiles. Passing
// handlers turns the respective tile into a button that opens a detail list.
export function buildOverviewStats(stats: OrgStats, handlers: OverviewHandlers = {}): OverviewStat[] {
  const roles = stats.roleCounts
  const roleNote = `${plural(roles.ROLE_admin, 'admin')} · ${plural(roles.ROLE_manager, 'manager')} · ${roles.ROLE_staff} staff`

  const topDept = stats.departments.find((d) => d.name !== UNASSIGNED_DEPARTMENT)
  const deptNote = topDept ? `Largest: ${topDept.name} (${topDept.count})` : 'No departments set'

  return [
    {
      label: 'Total Users',
      value: String(stats.totalUsers),
      icon: 'group',
      iconClassName: 'bg-ds-surface-container text-ds-primary',
      note: <span className="text-ds-secondary">{roleNote}</span>,
      onClick: handlers.onTotalUsers,
    },
    {
      label: 'Departments',
      value: String(stats.departmentCount),
      icon: 'apartment',
      iconClassName: 'bg-ds-tertiary-container/10 text-ds-on-tertiary-container',
      note: <span className="text-ds-secondary">{deptNote}</span>,
      onClick: handlers.onDepartments,
    },
  ]
}

// Maps the organization stats onto the wide storage tile. Unlimited users are
// excluded from the quota sum, so the meter reflects only finite quotas.
export function buildStorageInfo(stats: OrgStats): StorageInfo {
  const hasQuota = stats.storageQuota > 0
  const fraction = hasQuota ? Math.min(stats.storageUsed / stats.storageQuota, 1) : 0
  const near = stats.nearLimitCount

  return {
    label: 'Organization Storage',
    usage: hasQuota
      ? `${formatQuota(stats.storageUsed)} / ${formatQuota(stats.storageQuota)}`
      : `${formatQuota(stats.storageUsed)} used`,
    fraction,
    consumedLabel: hasQuota
      ? `${Math.round(fraction * 100)}% Consumed${near > 0 ? ` · ${plural(near, 'user')} near limit` : ''}`
      : 'No quotas set',
    availableLabel: hasQuota
      ? `${formatQuota(Math.max(stats.storageQuota - stats.storageUsed, 0))} available`
      : `${plural(stats.unlimitedCount, 'user')} unlimited`,
  }
}
