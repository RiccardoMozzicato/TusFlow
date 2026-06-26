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
  /** When set, the card becomes a link to this route. */
  to?: string
}

export type DocStatus = 'processed' | 'in_review' | 'failed'

export interface DocumentRow {
  /** File UUID, used to link the row to its details page. */
  id: string
  name: string
  type: string
  status: DocStatus
  modified: string
  icon: string
  /** Tailwind classes for the file-type icon chip. */
  iconClassName: string
}
