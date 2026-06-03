import { useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import { ROLE_BADGE_CLASSES, ROLE_LABELS, ROLES } from '@/features/profile/roles'
import ProfileHeader from '@/features/profile/components/ProfileHeader'
import SystemOverview from '@/features/profile/components/SystemOverview'
import ManagementLinks from '@/features/profile/components/ManagementLinks'
import AdminActionsTable from '@/features/profile/components/AdminActionsTable'
import {
  ADMIN_ACTIONS,
  GLOBAL_OVERVIEW,
  GLOBAL_STORAGE,
  MANAGEMENT_LINKS,
} from '@/features/profile/mockData'

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
          <SystemOverview stats={GLOBAL_OVERVIEW} storage={GLOBAL_STORAGE} />
        </div>
        <ManagementLinks links={MANAGEMENT_LINKS} />
      </div>

      <AdminActionsTable actions={ADMIN_ACTIONS} />
    </div>
  )
}
