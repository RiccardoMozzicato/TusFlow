import type { ReactNode } from 'react'

// A single tile in the System Overview bento grid (e.g. Total Users).
export interface OverviewStat {
  label: string
  value: string
  icon: string
  /** Tailwind classes for the icon chip background + foreground. */
  iconClassName: string
  /** Optional caption under the value (trend, etc.). */
  note?: ReactNode
  /** Optional thin progress bar (0–1) shown under the value. */
  progress?: number
  /** When set, the tile becomes a button that opens a detail view. */
  onClick?: () => void
}

// The wide storage tile spanning the bento grid.
export interface StorageInfo {
  label: string
  /** e.g. "14.2 TB / 20 TB". */
  usage: string
  /** Fill fraction 0–1. */
  fraction: number
  consumedLabel: string
  availableLabel: string
}

// A row in the Management quick-links list (admin only).
export interface ManagementLink {
  label: string
  icon: string
  /** Real route target. Omit (placeholder) until the page exists. */
  to?: string
}

export type AdminActionStatus = 'success' | 'in_progress' | 'critical'

// A row in the Recent Administrative Actions table (admin only).
export interface AdminAction {
  action: string
  icon: string
  /** Tailwind color class for the action icon. */
  iconClassName: string
  target: string
  status: AdminActionStatus
  timestamp: string
}

// --- Manager profile -------------------------------------------------------

export type TrendTone = 'positive' | 'neutral' | 'urgent'

// A bento stat tile in the department stats grid (manager only).
export interface DepartmentStat {
  label: string
  value: string
  /** Optional small unit after the value, e.g. "%". */
  unit?: string
  icon: string
  /** Tailwind classes for the icon chip background + foreground. */
  iconClassName: string
  /** Optional badge in the top-right corner (trend / status). */
  badge?: string
  badgeTone?: TrendTone
}

// A row in the Recently Managed Documents list (manager only).
export interface ManagedDocument {
  name: string
  meta: string
  icon: string
  /** Tailwind color class for the file-type icon. */
  iconClassName: string
  tag: string
}

// A button in the manager Quick Actions panel. Items without a `to` are
// placeholders until those pages exist.
export interface QuickAction {
  label: string
  description: string
  icon: string
  /** Tailwind classes for the resting icon chip background + foreground. */
  iconClassName: string
  /** Tailwind classes applied to the icon chip on row hover. */
  hoverClassName: string
  to?: string
}

// Summary card for the manager's team (manager only).
export interface TeamOverview {
  name: string
  /** Display names of a few members; rendered as initials avatars. */
  memberNames: string[]
  /** Count of members beyond those shown. */
  extraCount: number
  summary: string
}

// --- Staff profile ---------------------------------------------------------

// The "Document Count" tile in the staff workspace card.
export interface WorkspaceCounter {
  label: string
  value: string
  /** Caption after the value, e.g. "+12 this week". */
  note: string
  icon: string
}

// The "Storage Used" tile in the staff workspace card.
export interface WorkspaceStorage {
  label: string
  value: string
  /** Unit / capacity suffix, e.g. "GB / 50 GB". */
  unit: string
  /** Fill fraction 0–1. */
  fraction: number
  icon: string
}

// A row in the staff Recent Activity feed.
export interface StaffActivity {
  icon: string
  /** Tailwind classes for the icon circle background + foreground. */
  iconClassName: string
  /** Rich message (may contain bold/linked spans). */
  message: ReactNode
  /** Secondary line, e.g. "2 hours ago • Marketing / Strategy". */
  meta: string
}
