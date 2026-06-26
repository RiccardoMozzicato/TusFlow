import {type ReactNode, useEffect, useState} from 'react'
import Icon from '@/components/Icon'
import { initialsOf } from '@/lib/initials'
import UserProfileModal from "@/features/users/components/UserUpdateProfileModal.tsx";
import type {UserInfo} from "@/features/auth/types.ts";
import {getCurrentUser} from "@/features/users/usersApi.ts";

interface ProfileHeaderProps {
  user: UserInfo,
  showProfileModal: boolean,
  toggleShowProfileModal: () => void,
  updateUser: () => void,
  roleLabel: string
  /** DS token classes for the role badge background + foreground. */
  roleBadgeClassName: string
  department: string
  organization?: string
  /** Right-aligned action buttons (vary by role). */
  actions?: ReactNode
}

// Profile banner: initials avatar, name, role badge, and contact details.
// Shared by the base /profile page and the gated /admin page.
export default function ProfileHeader({
  user,
  showProfileModal,
  toggleShowProfileModal,
  updateUser,
  roleLabel,
  roleBadgeClassName,
  department,
  organization,
  actions,
}: ProfileHeaderProps) {
  //const displayName = user.fullName || user.username || 'Unknown user'
  const [displayName, setDisplayName] = useState<string>(user.fullName)
  const [email, setEmail] = useState<string>(user.email)

  useEffect(() => {
    async function currentUser() {
        return await getCurrentUser()
    }

    currentUser().then(thisUser => {
        setDisplayName(thisUser.fullName)
        setEmail(thisUser.email || user.email)
    })
  }, []);


  return (
      <>
          <section className="flex flex-col items-center gap-8 rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest p-8 shadow-sm md:flex-row">
              <div className="relative shrink-0">
                  <div className="flex h-32 w-32 items-center justify-center overflow-hidden rounded-2xl border-4 border-white bg-ds-primary font-ds-heading text-ds-display-lg text-ds-on-primary shadow-xl">
                      { user?.photoProfileUrl
                          ? <img src={user.photoProfileUrl} alt={displayName} className="h-full w-full object-cover" />
                          : initialsOf(displayName) }

                  </div>
                  <div className="absolute -bottom-2 -right-2 flex items-center justify-center rounded-ds-lg bg-ds-primary p-2 text-ds-on-primary">
                      <Icon name="verified_user" className="text-[20px]" />
                  </div>
              </div>

              <div className="flex-grow text-center md:text-left">
                  <div className="flex flex-col gap-2 md:flex-row md:items-center md:gap-4">
                      <h2 className="font-ds-heading text-ds-headline-md text-ds-on-surface">
                          {displayName}
                      </h2>
                      <span
                          className={`self-center rounded-ds-full px-3 py-1 font-ds-label text-ds-label-md md:self-auto ${roleBadgeClassName}`}
                      >
            {roleLabel}
          </span>
                  </div>
                  <p className="mt-1 font-ds-body text-ds-body-lg text-ds-on-surface-variant">
                      Department: {department || '—'}
                  </p>
                  <div className="mt-4 flex flex-wrap justify-center gap-4 md:justify-start">
          <span className="flex items-center gap-1.5 font-ds-label text-ds-label-md text-ds-secondary">
            <Icon name="mail" filled={false} className="text-[18px]" />
              {email || '—'}
          </span>
                      {organization ? (
                          <span className="flex items-center gap-1.5 font-ds-label text-ds-label-md text-ds-secondary">
              <Icon name="business" filled={false} className="text-[18px]" />
                              {organization}
            </span>
                      ) : null}
                  </div>
              </div>

              {actions ? <div className="flex gap-4">{actions}</div> : null}
          </section>

          { showProfileModal && <UserProfileModal user={{...user, email, fullName: displayName}}  updateUser={updateUser} onClose={toggleShowProfileModal} /> }
      </>

  )
}
