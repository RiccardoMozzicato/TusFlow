import { NavLink, Link } from 'react-router-dom'
import { useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import { isAdmin } from '@/features/profile/roles'
import { LOGOUT_URL } from '@/config'
import Icon from '@/components/Icon'
import { initialsOf } from '@/lib/initials'

interface NavItem {
  label: string
  icon: string
  /** Real route target. Omit (placeholder) until the page exists. */
  to?: string
}

// TODO: point `to` at real routes as those pages are built. Items without a
// `to` are placeholders.
const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', icon: 'dashboard', to: '/' },
  { label: 'Documents', icon: 'description', to: '/documents' },
  { label: 'Upload', icon: 'upload_file', to: '/upload' },
  { label: 'Shared with me', icon: 'group' },
]

// Only admins get the Admin entry; the route itself is role-gated too.
const ADMIN_NAV_ITEM: NavItem = {
  label: 'Admin',
  icon: 'admin_panel_settings',
  to: '/admin',
}

const ACTIVE_CLASS =
  'flex items-center gap-3 rounded-ds-lg bg-ds-surface px-4 py-3 font-semibold text-ds-primary transition-all duration-200 ease-in-out'
const INACTIVE_CLASS =
  'flex items-center gap-3 rounded-ds-lg px-4 py-3 text-ds-secondary transition-all duration-200 ease-in-out hover:bg-ds-surface-variant'

export default function Sidebar() {
  const user = useAppSelector(selectCurrentUser)
  const displayName = user?.fullName || user?.username || 'Unknown user'
  const role = user?.roles[0] ?? 'Member'
  const navItems = isAdmin(user?.roles) ? [...NAV_ITEMS, ADMIN_NAV_ITEM] : NAV_ITEMS

  function handleLogout() {
    window.location.href = LOGOUT_URL
  }

  return (
    <nav className="fixed left-0 top-0 z-20 flex h-full w-72 flex-col border-r border-ds-outline-variant bg-ds-secondary-fixed p-6">
      <div className="mb-8 flex flex-col gap-1">
        <div className="flex items-center gap-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-ds-lg bg-ds-primary text-ds-on-primary">
            <Icon name="folder_managed" className="text-xl" />
          </div>
          <h1 className="font-ds-heading text-ds-headline-sm font-bold text-ds-primary">
            DocPortal
          </h1>
        </div>
        <p className="ml-11 font-ds-label text-ds-label-md text-ds-secondary">
          Enterprise Management
        </p>
      </div>

      <div className="flex flex-col gap-2">
        {navItems.map((item) =>
          item.to ? (
            <NavLink
              key={item.label}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) => (isActive ? ACTIVE_CLASS : INACTIVE_CLASS)}
            >
              {({ isActive }) => (
                <>
                  <Icon name={item.icon} filled={isActive} />
                  <span>{item.label}</span>
                </>
              )}
            </NavLink>
          ) : (
            <a key={item.label} href="#" className={INACTIVE_CLASS}>
              <Icon name={item.icon} filled={false} />
              <span>{item.label}</span>
            </a>
          ),
        )}
      </div>

      <div className="mt-auto">
        <div className="flex items-center gap-3 rounded-ds-xl border border-ds-outline-variant/30 bg-ds-surface-container-low p-4">
          <Link
            to="/profile"
            className="flex min-w-0 flex-grow items-center gap-3 rounded-ds-lg transition-colors hover:opacity-80"
            title="View profile"
          >
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-ds-full bg-ds-primary font-ds-label text-ds-label-md font-bold text-ds-on-primary">
              {initialsOf(displayName)}
            </div>
            <div className="flex min-w-0 flex-col">
              <span className="truncate font-ds-label text-ds-label-md font-bold text-ds-on-surface">
                {displayName}
              </span>
              <span className="font-ds-label text-ds-label-sm capitalize text-ds-secondary">
                {role}
              </span>
            </div>
          </Link>
          <button
            type="button"
            onClick={handleLogout}
            title="Sign out"
            aria-label="Sign out"
            className="ml-auto rounded-ds-full p-1.5 text-ds-secondary transition-colors hover:bg-ds-surface-variant hover:text-ds-primary"
          >
            <Icon name="logout" className="text-[20px]" />
          </button>
        </div>
      </div>
    </nav>
  )
}
