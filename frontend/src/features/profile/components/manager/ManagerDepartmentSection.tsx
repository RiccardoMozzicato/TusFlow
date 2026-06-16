import { useDepartmentTeam } from '@/features/users/useDepartmentTeam'
import { formatQuota } from '@/features/users/userQuota'
import type { DepartmentTeam } from '@/features/users/departmentTeam'
import type { DepartmentStat } from '../../types'
import DepartmentStats from './DepartmentStats'
import UsersOverviewList from '../UsersOverviewList'

// Real KPI tiles for the manager's department, derived from the team aggregate.
function buildStats(team: DepartmentTeam): DepartmentStat[] {
  const { roleCounts } = team

  return [
    {
      label: 'Members',
      value: String(team.memberCount),
      icon: 'groups',
      iconClassName: 'bg-ds-primary-fixed text-ds-primary',
      badge:
        roleCounts.ROLE_manager > 0
          ? `${roleCounts.ROLE_manager} mgr · ${roleCounts.ROLE_staff} staff`
          : `${roleCounts.ROLE_staff} staff`,
      badgeTone: 'neutral',
    },
    {
      label: 'Staff',
      value: String(roleCounts.ROLE_staff),
      icon: 'badge',
      iconClassName: 'bg-ds-tertiary-fixed text-ds-tertiary-container',
    },
    {
      label: 'Storage Used',
      value: formatQuota(team.storageUsed),
      icon: 'cloud_queue',
      iconClassName: 'bg-ds-surface-container-highest text-ds-primary',
      badge:
        team.storageQuota > 0
          ? `of ${formatQuota(team.storageQuota)}`
          : team.unlimitedCount > 0
            ? 'unlimited'
            : undefined,
      badgeTone: 'neutral',
    },
  ]
}

const PANEL_CLASS =
  'rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-8 text-center font-ds-body text-ds-body-sm'

// Department-scoped, view-only management surface for the manager profile:
// real head-count, role mix, storage, and the member roster. (User mutations
// are admin-only, so this is read access — the most a manager's token allows.)
export default function ManagerDepartmentSection({
  userId,
}: {
  userId: string | null | undefined
}) {
  const { team, status, error, reload } = useDepartmentTeam(userId)

  if (status === 'loading') {
    return <div className={`${PANEL_CLASS} text-ds-secondary`}>Loading your department…</div>
  }

  if (status === 'failed') {
    return (
      <div className={`${PANEL_CLASS} flex flex-col items-center gap-3`}>
        <p className="text-ds-error">{error ?? 'Failed to load your department.'}</p>
        <button
          type="button"
          onClick={reload}
          className="inline-flex items-center rounded-ds border border-ds-outline-variant bg-ds-surface-container px-3 py-1.5 font-ds-label text-ds-label-sm text-ds-on-surface transition-colors hover:bg-ds-surface-container-high"
        >
          Retry
        </button>
      </div>
    )
  }

  if (!team) {
    return (
      <div className={`${PANEL_CLASS} text-ds-secondary`}>
        No department is assigned to your account.
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <DepartmentStats stats={buildStats(team)} />

      <section className="overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm">
        <div className="flex items-center justify-between border-b border-ds-outline-variant px-6 py-4">
          <h3 className="font-ds-heading text-ds-headline-sm text-ds-on-surface">
            {team.department}
          </h3>
          <span className="font-ds-label text-ds-label-sm text-ds-secondary">
            {team.memberCount} member{team.memberCount === 1 ? '' : 's'}
          </span>
        </div>
        <div className="px-6 py-2">
          <UsersOverviewList users={team.members} />
        </div>
      </section>
    </div>
  )
}
