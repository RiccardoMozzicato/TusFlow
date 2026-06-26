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
