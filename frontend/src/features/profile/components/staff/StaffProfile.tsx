import type { UserInfo } from '@/features/auth/types'
import { ROLE_LABELS, ROLES } from '../../roles'
import StaffProfileHeader from './StaffProfileHeader'
import WorkspaceCard from './WorkspaceCard'
import QuickLinks from './QuickLinks'
import RecentActivity from './RecentActivity'
import {
  STAFF_ACTIVITY,
  STAFF_DOCUMENT_COUNT,
  STAFF_QUICK_LINKS,
  STAFF_STORAGE,
} from '../../mockData'

interface StaffProfileProps {
  user: UserInfo
}

// Personal-scope staff profile: header, "My Workspace" overview, quick links,
// and a recent-activity feed. Rendered by ProfilePage for the staff role.
// Workspace/activity data is mocked pending API wiring.
export default function StaffProfile({ user }: StaffProfileProps) {
  const displayName = user.fullName || user.username || 'Unknown user'

  return (
    <div className="mx-auto max-w-[1100px]">
      <StaffProfileHeader
        name={displayName}
        roleLabel={ROLE_LABELS[ROLES.staff]}
        department={user.department}
      />

      <div className="grid grid-cols-12 gap-6">
        <div className="col-span-12 md:col-span-8">
          <WorkspaceCard counter={STAFF_DOCUMENT_COUNT} storage={STAFF_STORAGE} />
        </div>
        <div className="col-span-12 md:col-span-4">
          <QuickLinks links={STAFF_QUICK_LINKS} />
        </div>
        <div className="col-span-12">
          <RecentActivity items={STAFF_ACTIVITY} />
        </div>
      </div>
    </div>
  )
}
