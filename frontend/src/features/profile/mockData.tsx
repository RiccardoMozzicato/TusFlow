import Icon from '@/components/Icon'
import type {
  AdminAction,
  DepartmentStat,
  ManagedDocument,
  ManagementLink,
  OverviewStat,
  QuickAction,
  StaffActivity,
  StorageInfo,
  TeamOverview,
  WorkspaceCounter,
  WorkspaceStorage,
} from './types'

// Placeholder data mirroring the design mock. Replace with API-backed data
// (user/file/job services) once the admin endpoints are wired.

// --- Global scope (admin) ---------------------------------------------------

export const GLOBAL_OVERVIEW: OverviewStat[] = [
  {
    label: 'Total Users',
    value: '12,842',
    icon: 'group',
    iconClassName: 'bg-ds-surface-container text-ds-primary',
    note: (
      <span className="flex items-center gap-1 text-ds-on-tertiary-container">
        <Icon name="trending_up" className="text-[14px]" />
        +4.2% from last month
      </span>
    ),
  },
  {
    label: 'System Health',
    value: '99.98%',
    icon: 'speed',
    iconClassName: 'bg-ds-tertiary-container/10 text-ds-on-tertiary-container',
    progress: 0.99,
  },
]

export const GLOBAL_STORAGE: StorageInfo = {
  label: 'Storage Usage',
  usage: '14.2 TB / 20 TB',
  fraction: 0.71,
  consumedLabel: '71% Consumed',
  availableLabel: '5.8 TB Available',
}

// --- Department scope (manager) ---------------------------------------------

export const DEPARTMENT_STATS: DepartmentStat[] = [
  {
    label: 'Managed Documents',
    value: '2,482',
    icon: 'folder_managed',
    iconClassName: 'bg-ds-primary-fixed text-ds-primary',
    badge: '+12%',
    badgeTone: 'positive',
  },
  {
    label: 'Team Activity',
    value: '94',
    unit: '%',
    icon: 'groups',
    iconClassName: 'bg-ds-tertiary-fixed text-ds-tertiary-container',
    badge: 'Active',
    badgeTone: 'neutral',
  },
  {
    label: 'Pending Approvals',
    value: '18',
    icon: 'notification_important',
    iconClassName: 'bg-ds-error-container text-ds-on-error-container',
    badge: 'Urgent',
    badgeTone: 'urgent',
  },
]

export const MANAGED_DOCUMENTS: ManagedDocument[] = [
  {
    name: 'Q3 Financial Audit Report.pdf',
    meta: 'Modified 2 hours ago by David Chen',
    icon: 'description',
    iconClassName: 'text-ds-primary',
    tag: 'Finance',
  },
  {
    name: 'FY2024_Budget_Allocation.xlsx',
    meta: 'Modified Yesterday by Sarah Jenkins',
    icon: 'table_chart',
    iconClassName: 'text-ds-on-tertiary-container',
    tag: 'Planning',
  },
  {
    name: 'Team_Access_Policy_v2.docx',
    meta: 'Modified 3 days ago by System Admin',
    icon: 'description',
    iconClassName: 'text-ds-primary',
    tag: 'Policy',
  },
]

export const MANAGER_QUICK_ACTIONS: QuickAction[] = [
  {
    label: 'Department Settings',
    description: 'Manage division defaults',
    icon: 'settings_suggest',
    iconClassName: 'bg-ds-primary-fixed text-ds-primary',
    hoverClassName: 'group-hover:bg-ds-primary group-hover:text-ds-on-primary',
  },
  {
    label: 'Manage Team Access',
    description: 'Permissions & user roles',
    icon: 'key',
    iconClassName: 'bg-ds-surface-container-highest text-ds-primary',
    hoverClassName: 'group-hover:bg-ds-primary group-hover:text-ds-on-primary',
  },
  {
    label: 'Department Reports',
    description: 'Audit logs & performance',
    icon: 'analytics',
    iconClassName: 'bg-ds-tertiary-fixed text-ds-tertiary-container',
    hoverClassName: 'group-hover:bg-ds-tertiary group-hover:text-ds-on-tertiary',
  },
]

export const TEAM_OVERVIEW: TeamOverview = {
  name: 'Finance Team',
  memberNames: ['David Chen', 'Maria Lopez', 'James Park'],
  extraCount: 12,
  summary:
    'Your team has uploaded 45 documents this week. Productivity is up by 8% compared to last period.',
}

// --- Personal scope (staff) -------------------------------------------------

export const STAFF_DOCUMENT_COUNT: WorkspaceCounter = {
  label: 'Document Count',
  value: '1,284',
  note: '+12 this week',
  icon: 'description',
}

export const STAFF_STORAGE: WorkspaceStorage = {
  label: 'Storage Used',
  value: '12.4',
  unit: 'GB / 50 GB',
  fraction: 0.248,
  icon: 'cloud_queue',
}

export const STAFF_QUICK_LINKS: ManagementLink[] = [
  { label: 'Personal Settings', icon: 'settings_account_box' },
  { label: 'My Documents', icon: 'folder_shared', to: '/documents' },
  { label: 'Shared with Me', icon: 'group_add' },
]

export const STAFF_ACTIVITY: StaffActivity[] = [
  {
    icon: 'upload_file',
    iconClassName: 'bg-ds-tertiary-container text-ds-tertiary-fixed',
    message: (
      <>
        You uploaded{' '}
        <span className="font-bold">Q3_Marketing_Strategy_Draft.pdf</span>
      </>
    ),
    meta: '2 hours ago • Marketing / Strategy',
  },
  {
    icon: 'edit_document',
    iconClassName: 'bg-ds-primary-container text-ds-on-primary-container',
    message: (
      <>
        Edited <span className="font-bold">Campaign_Assets_List_Final.xlsx</span>
      </>
    ),
    meta: 'Yesterday, 4:32 PM • Content Management',
  },
  {
    icon: 'share',
    iconClassName: 'bg-ds-surface-variant text-ds-primary',
    message: (
      <>
        Shared <span className="font-bold">Budget_Forecast_v2</span> with{' '}
        <span className="font-bold text-ds-primary">Marketing Team</span>
      </>
    ),
    meta: '2 days ago • Finance & Ops',
  },
]

// --- Management quick links (admin) -----------------------------------------

export const MANAGEMENT_LINKS: ManagementLink[] = [
  { label: 'User Management', icon: 'person' },
  { label: 'Security Settings', icon: 'security' },
  { label: 'Audit Logs', icon: 'history_edu' },
  { label: 'Global Config', icon: 'settings_suggest' },
]

// --- Recent administrative actions (admin) ----------------------------------

export const ADMIN_ACTIONS: AdminAction[] = [
  {
    action: 'Onboarded new department',
    icon: 'person_add',
    iconClassName: 'text-ds-primary',
    target: 'Finance Division',
    status: 'success',
    timestamp: 'Oct 24, 2023 • 14:32',
  },
  {
    action: 'Modified firewall policy',
    icon: 'security_update',
    iconClassName: 'text-ds-primary',
    target: 'Global Edge 01',
    status: 'success',
    timestamp: 'Oct 24, 2023 • 11:15',
  },
  {
    action: 'Bulk permission reset',
    icon: 'folder_shared',
    iconClassName: 'text-ds-primary',
    target: 'Legal Archive Root',
    status: 'in_progress',
    timestamp: 'Oct 23, 2023 • 09:45',
  },
  {
    action: 'Access revocation (Security Alert)',
    icon: 'report',
    iconClassName: 'text-ds-error',
    target: 'User ID: #7829',
    status: 'critical',
    timestamp: 'Oct 22, 2023 • 18:20',
  },
]
