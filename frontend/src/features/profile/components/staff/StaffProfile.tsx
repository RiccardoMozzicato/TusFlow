import type { UserInfo } from '@/features/auth/types'
import { ROLE_LABELS, ROLES } from '../../roles'
import StaffProfileHeader from './StaffProfileHeader'
import WorkspaceCard from './WorkspaceCard'
import QuickLinks from './QuickLinks'
import { useWorkspaceStats } from './useWorkspaceStats'
import { STAFF_QUICK_LINKS } from '../../mockData'
import UserProfileModal from '@/features/users/components/UserUpdateProfileModal.tsx'

interface StaffProfileProps {
  user: UserInfo
  toggleShowProfileModal: () => void
  updateUser: () => void
  showProfileModal: boolean
}

// Personal-scope staff profile: header, live "My Workspace" overview, and quick
// links. Rendered by ProfilePage for the staff role.
export default function StaffProfile({
  user,
  toggleShowProfileModal,
  updateUser,
  showProfileModal,
}: StaffProfileProps) {
  const displayName = user.fullName || user.username || 'Unknown user'
  const { stats, error } = useWorkspaceStats()

  return (
    <>
      <div className="mx-auto max-w-[1100px]">
        <StaffProfileHeader
          name={displayName}
          roleLabel={ROLE_LABELS[ROLES.staff]}
          department={user.department}
          toggleShowProfileModal={toggleShowProfileModal}
          photoProfileUrl={user.photoProfileUrl}
        />

        <div className="grid grid-cols-12 gap-6">
          <div className="col-span-12 md:col-span-8">
            <WorkspaceCard
              documentCount={stats?.documentCount ?? null}
              storage={stats?.storage ?? null}
              error={error}
            />
          </div>
          <div className="col-span-12 md:col-span-4">
            <QuickLinks links={STAFF_QUICK_LINKS} />
          </div>
        </div>
      </div>

      {showProfileModal && (
        <UserProfileModal user={user} updateUser={updateUser} onClose={toggleShowProfileModal} />
      )}
    </>
  )
}
