import { Link } from 'react-router-dom'
import { useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import Icon from '@/components/Icon'
import {
  highestRole,
  ROLE_BADGE_CLASSES,
  ROLE_LABELS,
  ROLES,
} from '@/features/profile/roles'
import ProfileHeader from '@/features/profile/components/ProfileHeader'
import PersonalInfoCard from '@/features/profile/components/PersonalInfoCard'
import ManagerProfile from '@/features/profile/components/manager/ManagerProfile'
import StaffProfile from '@/features/profile/components/staff/StaffProfile'
import { useState } from 'react'

const EDIT_PROFILE_BUTTON = (setShowProfileModal: any) => (
  <button
    onClick={setShowProfileModal}
    type="button"
    className="rounded-ds-lg border border-ds-outline px-6 py-3 font-ds-label text-ds-label-md text-ds-primary transition-colors hover:bg-ds-surface-container"
  >
    Edit Profile
  </button>
)

// Callout shown to admins linking to the dedicated, gated admin console.
function AdminConsoleCallout() {
  return (
    <Link
      to="/admin"
      className="group flex items-center justify-between rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-6 shadow-sm transition-colors hover:border-ds-primary"
    >
      <div className="flex items-center gap-4">
        <div className="rounded-ds-lg bg-ds-primary p-3 text-ds-on-primary">
          <Icon name="admin_panel_settings" />
        </div>
        <div>
          <p className="font-ds-body text-ds-body-md text-ds-on-surface">
            Admin Console
          </p>
          <p className="font-ds-label text-ds-label-md text-ds-secondary">
            Manage users, security, and system configuration
          </p>
        </div>
      </div>
      <Icon name="chevron_right" filled={false} className="text-ds-outline" />
    </Link>
  )
}


interface ProfilePage {
    updateUser: () => void
}

// Base profile page for every authenticated user. Content adapts to the user's
// highest role, each with a dedicated layout: staff sees a personal workspace,
// managers a department-scoped view, admins a profile with a shortcut to the
// gated admin console.
export default function ProfilePage({updateUser} : ProfilePage) {
  const user = useAppSelector(selectCurrentUser)

  if (!user) return null

  const role = highestRole(user.roles)
  const [showProfileModal, setShowProfileModal] = useState(false)

  const toggleShowProfileModal = () => {
    setShowProfileModal(current => !current)
  }

  if (role === ROLES.manager) {
    return <ManagerProfile user={user} updateUser={updateUser} toggleShowProfileModal={toggleShowProfileModal} showProfileModal={showProfileModal} />
  }

  if (role === ROLES.staff) {
    return <StaffProfile user={user} updateUser={updateUser} toggleShowProfileModal={toggleShowProfileModal} showProfileModal={showProfileModal} />
  }

  // Admin: profile summary plus a shortcut into the admin console.
  return (
    <div className="mx-auto max-w-[1160px] space-y-8">
      <ProfileHeader
        user={user}
        showProfileModal={showProfileModal}
        toggleShowProfileModal={toggleShowProfileModal}
        updateUser={updateUser}
        roleLabel={ROLE_LABELS[role]}
        roleBadgeClassName={ROLE_BADGE_CLASSES[role]}
        department={user.department}
        organization={user.organization}
        actions={EDIT_PROFILE_BUTTON(setShowProfileModal)}
      />

      <PersonalInfoCard user={user} />

      <AdminConsoleCallout />
    </div>
  )
}
