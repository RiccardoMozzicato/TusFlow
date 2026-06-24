// Domain types for the Document Library. Mirrors the dashboard feature's shape:
// presentation-friendly view models that the mock layer fills today and the
// file/job services will fill once wired.

import type { SharingScope } from './documentsApi'
import type { JobStatus } from './jobsApi'

export type DocumentStatus = 'processing' | 'ready' | 'draft' | 'scan_failed'

/** Coarse file-type buckets; 'file' is the generic fallback for unknown MIME types. */
export type FileType = 'pdf' | 'docx' | 'txt' | 'image' | 'video' | 'file'

/** Visibility scope of a file; mirrors the backend SharingScope enum. */
export type DocumentScope = 'personal' | 'department' | 'organization'

export interface LibraryDocument {
  id: string
  title: string
  /** Semantic file type, drives the icon chip. */
  fileType: FileType
  /** Visibility scope, drives the scope indicator next to the title. */
  scope: DocumentScope
  status: DocumentStatus
  owner: string
  /** Whether the current user owns this file; drives the "You" owner label. */
  isOwnedByMe: boolean
  /** Whether the current user may modify this file; gates the row/card actions menu. */
  isWritable: boolean
  /** Raw backend sharing scope, reused when uploading a new version. */
  sharingScope: SharingScope
  /** Organization that owns the file; shown in the file information modal. */
  organization?: string
  /** Department the file belongs to; null for organization-wide or personal files. */
  department?: string
  /** Pre-formatted creation date and time (e.g. "Oct 24, 2023, 09:12 AM"); used as tooltip. */
  created: string
  /** Relative creation time (e.g. "Just now", "Yesterday", "3 days ago"). */
  createdRelative: string
  /** Version label shown in the corner badge (e.g. "v2.1"). */
  version: string
  /** Numeric current version, used when favoriting the file from the library. */
  versionNumber: number
  /** Pre-formatted file size (e.g. "2.3 MB"); "—" when unknown. */
  size: string
  /** Current AI-job status; drives the AI indicator. Undefined when no job exists. */
  aiStatus?: JobStatus
}

export type ViewMode = 'grid' | 'list'

/** Row/card actions reachable from the document actions menu. */
export type DocumentActionKind = 'info' | 'share' | 'newVersion' | 'delete'

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
  /** Numeric backend version, sent as the `version` query param when downloading. */
  version: number
  /** Version label (e.g. "v2.1"). */
  label: string
  /** Filename of this version, used as the download's save name. */
  filename: string
  /** Author or origin of the version (e.g. "Alex Mercer", "System Migration"). */
  author: string
  /** Pre-formatted date (e.g. "Oct 20, 2023" or "Today, 09:12 AM"). */
  date: string
  isCurrent?: boolean
}

/** One user-inserted (non-AI) metadata key/value pair. */
export interface MetadataEntry {
  key: string
  value: string
}

/** Outcome of the AI analysis for a file, used to drive the details-page indicator. */
export type AiAnalysisStatus = 'completed' | 'failed' | 'unsupported' | 'pending'

/** A user the file is shared with, as shown in the sharing panel. */
export interface DocumentShare {
  id: string
  /** Numeric share record id, used to remove/update the share; null when unsaved. */
  shareId: number | null
  /** Owner/recipient user id (UUID); displayed truncated when no name is known. */
  userId: string
  /** Recipient display name, resolved server-side; null when unavailable. */
  userName: string | null
  /** 'READ_ONLY' | 'READ_WRITE', shown as a permission badge. */
  permission: string
  /** Pre-formatted share timestamp. */
  sharedAt: string
}

/** Full detail view model for a single document. */
export interface DocumentDetail {
  id: string
  title: string
  fileType: FileType
  /**
   * Raw backend sharing scope (PERSONAL | DEPARTMENT | ORGANIZATION). Carried so
   * a new-version upload reuses the file's existing scope for authorization.
   */
  sharingScope: SharingScope
  /** Current version number; targets metadata edits at the right file version. */
  currentVersion: number
  /** Location trail shown above the header. */
  breadcrumbs: Breadcrumb[]
  /** Whether AI analysis has completed, failed, is unsupported, or is still pending. */
  aiAnalysisStatus: AiAnalysisStatus
  /** Generated one-line summary, shown in the AI summary card; null while absent. */
  aiSummary: string | null
  /** AI-assessed sensitivity classification (e.g. "Confidential"). */
  sensitivity: string
  tags: string[]
  /** User-inserted (non-AI) metadata for the current version; system keys excluded. */
  userMetadata: MetadataEntry[]
  owner: string
  /** Pre-formatted creation date (e.g. "Oct 24, 2023"). */
  date: string
  processing: ProcessingStep[]
  versions: DocumentVersion[]
}
