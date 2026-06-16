import { useState } from 'react'
import { useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import { ROLE_BADGE_CLASSES, ROLE_LABELS, ROLES } from '@/features/profile/roles'
import ProfileHeader from '@/features/profile/components/ProfileHeader'
import SystemOverview from '@/features/profile/components/SystemOverview'
import ManagementLinks from '@/features/profile/components/ManagementLinks'
import AdminActionsTable from '@/features/profile/components/AdminActionsTable'
import OverviewModal from '@/features/profile/components/OverviewModal'
import UsersOverviewList from '@/features/profile/components/UsersOverviewList'
import DepartmentsOverviewList from '@/features/profile/components/DepartmentsOverviewList'
import { buildOverviewStats, buildStorageInfo } from '@/features/profile/adminOverview'
import { ADMIN_ACTIONS, MANAGEMENT_LINKS } from '@/features/profile/mockData'
import { useOrgStats } from '@/features/users/useOrgStats'

type OverviewDetail = 'users' | 'departments' | null

// Renders the organization overview, swapping between loading / error / data.
// The Total Users and Departments tiles open a detail list modal.
function OverviewSection() {
  const { stats, users, status, error, reload } = useOrgStats()
  const [detail, setDetail] = useState<OverviewDetail>(null)

  if (status === 'loading') {
    return (
      <div className="flex items-center justify-center rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-12 font-ds-body text-ds-body-sm text-ds-secondary">
        Loading organization statistics…
      </div>
    )
  }

  if (status === 'failed' || !stats) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-12 text-center">
        <p className="font-ds-body text-ds-body-sm text-ds-error">
          {error ?? 'Failed to load organization statistics.'}
        </p>
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

  const overviewStats = buildOverviewStats(stats, {
    onTotalUsers: () => setDetail('users'),
    onDepartments: () => setDetail('departments'),
  })

  return (
    <>
      <SystemOverview stats={overviewStats} storage={buildStorageInfo(stats)} />

      {detail === 'users' ? (
        <OverviewModal
          title="Users"
          subtitle={`${stats.totalUsers} in your organization`}
          onClose={() => setDetail(null)}
        >
          <UsersOverviewList users={users} />
        </OverviewModal>
      ) : null}

      {detail === 'departments' ? (
        <OverviewModal
          title="Departments"
          subtitle={`${stats.departmentCount} department${stats.departmentCount === 1 ? '' : 's'}`}
          onClose={() => setDetail(null)}
        >
          <DepartmentsOverviewList departments={stats.departments} />
        </OverviewModal>
      ) : null}
    </>
  )
}

// Full admin console. Reachable only via the role-gated /admin route, so the
// current user is guaranteed to hold the admin role.
export default function AdminPage() {
  const user = useAppSelector(selectCurrentUser)
  const displayName = user?.fullName || user?.username || 'Administrator'

  return (
    <div className="mx-auto max-w-[1160px] space-y-8">
      <ProfileHeader
        name={displayName}
        roleLabel={ROLE_LABELS[ROLES.admin]}
        roleBadgeClassName={ROLE_BADGE_CLASSES[ROLES.admin]}
        department={user?.department ?? ''}
        email={user?.email ?? ''}
        organization={user?.organization}
        actions={
          <button
            type="button"
            className="rounded-ds-lg bg-ds-primary px-6 py-3 font-ds-label text-ds-label-md text-ds-on-primary transition-opacity hover:opacity-90"
          >
            System Settings
          </button>
        }
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <OverviewSection />
        </div>
        <ManagementLinks links={MANAGEMENT_LINKS} />
      </div>

      <AdminActionsTable actions={ADMIN_ACTIONS} />
    </div>
  )
}
