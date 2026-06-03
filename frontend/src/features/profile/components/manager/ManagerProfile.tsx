import type { UserInfo } from '@/features/auth/types'
import ProfileBanner from './ProfileBanner'
import DepartmentStats from './DepartmentStats'
import RecentManagedDocuments from './RecentManagedDocuments'
import QuickActions from './QuickActions'
import TeamOverviewCard from './TeamOverviewCard'
import {
  DEPARTMENT_STATS,
  MANAGED_DOCUMENTS,
  MANAGER_QUICK_ACTIONS,
  TEAM_OVERVIEW,
} from '../../mockData'

interface ManagerProfileProps {
  user: UserInfo
}

// Department-scoped manager profile: banner, KPI stats, managed documents, and
// a sidebar of quick actions + team overview. Rendered by ProfilePage for the
// manager role. Stats/docs/team are mocked pending API wiring.
export default function ManagerProfile({ user }: ManagerProfileProps) {
  const displayName = user.fullName || user.username || 'Manager'

  return (
    <div className="mx-auto max-w-[1160px] space-y-8">
      <ProfileBanner
        name={displayName}
        title="Department Manager"
        subtitle={user.department || undefined}
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
        <div className="space-y-6 lg:col-span-8">
          <DepartmentStats stats={DEPARTMENT_STATS} />
          <RecentManagedDocuments documents={MANAGED_DOCUMENTS} />
        </div>
        <div className="space-y-6 lg:col-span-4">
          <QuickActions actions={MANAGER_QUICK_ACTIONS} />
          <TeamOverviewCard team={TEAM_OVERVIEW} />
        </div>
      </div>
    </div>
  )
}
