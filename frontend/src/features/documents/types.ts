// Domain types for the Document Library. Mirrors the dashboard feature's shape:
// presentation-friendly view models that the mock layer fills today and the
// file/job services will fill once wired.

export type DocumentStatus = 'processing' | 'ready' | 'draft' | 'scan_failed'

export type FileType = 'pdf' | 'docx' | 'image' | 'video'

export interface LibraryDocument {
  id: string
  title: string
  /** Semantic file type, drives the icon chip. */
  fileType: FileType
  status: DocumentStatus
  owner: string
  /** Pre-formatted, human-readable date (e.g. "Oct 24, 2023"). */
  modified: string
  /** Version label shown in the corner badge (e.g. "v2.1"). */
  version: string
}

export type ViewMode = 'grid' | 'list'

export interface CategoryFilter {
  id: string
  label: string
  /** Whether the box starts checked in the mock. */
  defaultChecked?: boolean
}

export interface TagFilter {
  id: string
  label: string
  /** Whether the tag starts selected (filled) in the mock. */
  defaultSelected?: boolean
}

export interface FileTypeOption {
  type: FileType
  label: string
  icon: string
  /** Tailwind text-color class for the option icon. */
  iconClassName: string
}

// --- Document Details view ---------------------------------------------------

/** A single hop in the location breadcrumb (e.g. Documents > Finance > file). */
export interface Breadcrumb {
  label: string
  /** Route target; omit for the current (non-link) trailing crumb. */
  to?: string
}

/** One step in the processing timeline. The last `done` step reads as current. */
export interface ProcessingStep {
  label: string
  /** Pre-formatted timestamp (e.g. "Oct 24, 09:12 AM"); omit while pending. */
  timestamp?: string
  done: boolean
}

/** A row in the version history list. */
export interface DocumentVersion {
  id: string
  /** Version label (e.g. "v2.1"). */
  label: string
  /** Author or origin of the version (e.g. "Alex Mercer", "System Migration"). */
  author: string
  /** Pre-formatted date (e.g. "Oct 20, 2023" or "Today, 09:12 AM"). */
  date: string
  isCurrent?: boolean
}

/** Full detail view model for a single document. */
export interface DocumentDetail {
  id: string
  title: string
  fileType: FileType
  /** Location trail shown above the header. */
  breadcrumbs: Breadcrumb[]
  /** Generated one-line summary, shown in the AI summary card. */
  aiSummary: string
  category: string
  tags: string[]
  owner: string
  /** Pre-formatted creation/modification date (e.g. "Oct 24, 2023"). */
  date: string
  /** Total page count for the preview toolbar. */
  pageCount: number
  processing: ProcessingStep[]
  versions: DocumentVersion[]
}
