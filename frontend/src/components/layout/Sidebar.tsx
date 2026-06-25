import { NavLink, Link } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import { selectSidebarCollapsed, toggleSidebar } from '@/features/ui/uiSlice'
import { isAdmin, highestRole, ROLE_LABELS } from '@/features/profile/roles'
import { LOGOUT_URL } from '@/config'
import Icon from '@/components/Icon'
import ServiceStatusIndicator from '@/components/ServiceStatusIndicator'
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
  { label: 'Shared with me', icon: 'group', to: '/shared' },
  { label: 'Favorites', icon: 'star', to: '/favorites' },
]

// Only admins get the Admin entry; the route itself is role-gated too.
const ADMIN_NAV_ITEM: NavItem = {
  label: 'Admin',
  icon: 'admin_panel_settings',
  to: '/admin',
}

const BASE_LINK_CLASS =
  'flex items-center rounded-ds-lg py-3 transition-all duration-200 ease-in-out'
const ACTIVE_CLASS = 'bg-ds-surface font-semibold text-ds-primary'
const INACTIVE_CLASS = 'text-ds-secondary hover:bg-ds-surface-variant'

// Collapsed: center the icon with no label; expanded: icon + label with padding.
function navLinkClass(isActive: boolean, collapsed: boolean): string {
  const layout = collapsed ? 'justify-center px-0' : 'gap-3 px-4'
  return `${BASE_LINK_CLASS} ${layout} ${isActive ? ACTIVE_CLASS : INACTIVE_CLASS}`
}

// Renders one nav entry: a real NavLink (with active styling) when it has a route,
// or a non-navigating placeholder otherwise. Shared by the main list and the
// bottom-pinned Admin entry so they look and behave identically.
function renderNavItem(item: NavItem, collapsed: boolean) {
  return item.to ? (
    <NavLink
      key={item.label}
      to={item.to}
      end={item.to === '/'}
      title={item.label}
      className={({ isActive }) => navLinkClass(isActive, collapsed)}
    >
      {({ isActive }) => (
        <>
          <Icon name={item.icon} filled={isActive} />
          {!collapsed && <span>{item.label}</span>}
        </>
      )}
    </NavLink>
  ) : (
    <a key={item.label} href="#" title={item.label} className={navLinkClass(false, collapsed)}>
      <Icon name={item.icon} filled={false} />
      {!collapsed && <span>{item.label}</span>}
    </a>
  )
}

export default function Sidebar() {
  const dispatch = useAppDispatch()
  const collapsed = useAppSelector(selectSidebarCollapsed)
  const user = useAppSelector(selectCurrentUser)
  const displayName = user?.fullName || user?.username || 'Unknown user'
  // Show the most-privileged role the user holds, not whatever Keycloak happens
  // to list first (which could read "manager" for an admin).
  const role = ROLE_LABELS[highestRole(user?.roles)]

  function handleLogout() {
    window.location.href = LOGOUT_URL
  }

  return (
    <nav
      className={`fixed left-0 top-0 z-20 flex h-full flex-col border-r border-ds-outline-variant bg-ds-secondary-fixed transition-all duration-200 ${
        collapsed ? 'w-20 p-3' : 'w-72 p-6'
      }`}
    >
      <div
        className={`mb-8 flex ${
          collapsed ? 'flex-col items-center gap-3' : 'items-start justify-between'
        }`}
      >
        <div className="flex min-w-0 flex-col gap-1">
          <div className="flex items-center gap-3">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-ds-lg bg-ds-primary text-ds-on-primary">
              <Icon name="folder_managed" className="text-xl" />
            </div>
            {!collapsed && (
              <h1 className="font-ds-heading text-ds-headline-sm font-bold text-ds-primary">
                DocPortal
              </h1>
            )}
          </div>
          {!collapsed && (
            <p className="ml-11 font-ds-label text-ds-label-md text-ds-secondary">
              Enterprise Management
            </p>
          )}
        </div>
        <button
          type="button"
          onClick={() => dispatch(toggleSidebar())}
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          className="rounded-ds-full p-1.5 text-ds-secondary transition-colors hover:bg-ds-surface-variant hover:text-ds-primary"
        >
          <Icon name={collapsed ? 'chevron_right' : 'chevron_left'} />
        </button>
      </div>

      <div className="flex flex-col gap-2">
        {NAV_ITEMS.map((item) => renderNavItem(item, collapsed))}
      </div>

      <div className="mt-auto flex flex-col gap-3">
        {isAdmin(user?.roles) && renderNavItem(ADMIN_NAV_ITEM, collapsed)}
        <ServiceStatusIndicator collapsed={collapsed} />
        {collapsed ? (
          <div className="flex flex-col items-center gap-3">
            <Link
              to="/profile"
              title={`${displayName} — view profile`}
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-ds-full bg-ds-primary font-ds-label text-ds-label-md font-bold text-ds-on-primary transition-opacity hover:opacity-80"
            >
              {initialsOf(displayName)}
            </Link>
            <button
              type="button"
              onClick={handleLogout}
              title="Sign out"
              aria-label="Sign out"
              className="rounded-ds-full p-1.5 text-ds-secondary transition-colors hover:bg-ds-surface-variant hover:text-ds-primary"
            >
              <Icon name="logout" className="text-[20px]" />
            </button>
          </div>
        ) : (
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
                <span className="font-ds-label text-ds-label-sm text-ds-secondary">
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
        )}
      </div>
    </nav>
  )
}
