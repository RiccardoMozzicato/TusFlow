import type { UserInfo } from '@/features/auth/types'
import ProfileBanner from './ProfileBanner'
import ManagerDepartmentSection from './ManagerDepartmentSection'
import UserProfileModal from "@/features/users/components/UserUpdateProfileModal.tsx";

interface ManagerProfileProps {
  user: UserInfo,
  toggleShowProfileModal: () => void,
  updateUser: () => void,
  showProfileModal: Boolean
}

// Department-scoped manager profile: banner plus a real, view-only management
// surface for the manager's own department (head-count, role mix, storage, and
// the member roster). Rendered by ProfilePage for the manager role.
export default function ManagerProfile({ user, updateUser, toggleShowProfileModal, showProfileModal }: ManagerProfileProps) {
  const displayName = user.fullName || user.username || 'Manager'

  return (
    <>
        <div className="mx-auto max-w-[1160px] space-y-8">
            <ProfileBanner
                name={displayName}
                title="Department Manager"
                subtitle={user.department || undefined}
                photoProfileUrl={user.photoProfileUrl}
                toggleShowProfileModal={toggleShowProfileModal}
            />

            <ManagerDepartmentSection userId={user.uuid} />
        </div>

        { showProfileModal && <UserProfileModal user={user} updateUser={updateUser} onClose={toggleShowProfileModal} /> }
    </>

  )
}
