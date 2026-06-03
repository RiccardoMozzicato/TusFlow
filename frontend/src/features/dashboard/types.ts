import type { ReactNode } from 'react'

export interface StatCard {
  label: string
  value: string
  /** Small text after the value, e.g. "files" or "Action required". */
  note?: string
  /** Tone of the note text. */
  noteTone?: 'muted' | 'error'
  icon: string
  /** Tailwind classes for the icon chip background + foreground. */
  iconClassName: string
  /** Optional progress bar fill (0–1) shown along the bottom of the card. */
  progress?: number
}

export type DocStatus = 'processed' | 'in_review' | 'failed'

export interface DocumentRow {
  name: string
  type: string
  status: DocStatus
  modified: string
  icon: string
  /** Tailwind classes for the file-type icon chip. */
  iconClassName: string
}

export interface ActivityItem {
  icon: string
  /** Rich message (may contain bold/linked spans). */
  message: ReactNode
  time: string
  tone?: 'default' | 'error'
}
