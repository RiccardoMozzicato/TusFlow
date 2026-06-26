import type { ManagementLink } from './types'

// Static link lists for the profile pages. The data-backed surfaces (admin
// System Overview, manager department view, staff "My Workspace") are wired to
// their APIs; only these navigation lists remain hardcoded.

// --- Personal scope (staff) -------------------------------------------------

export const STAFF_QUICK_LINKS: ManagementLink[] = [
  { label: 'My Documents', icon: 'folder_shared', to: '/documents' },
  { label: 'Shared with Me', icon: 'group_add', to: '/shared' },
]

// --- Management quick links (admin) -----------------------------------------

export const MANAGEMENT_LINKS: ManagementLink[] = [
  { label: 'User Management', icon: 'person', to: '/admin/users' },
  { label: 'Security Settings', icon: 'security' },
  { label: 'Audit Logs', icon: 'history_edu' },
  { label: 'Global Config', icon: 'settings_suggest' },
]