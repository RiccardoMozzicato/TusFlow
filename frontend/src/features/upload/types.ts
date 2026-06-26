import type { SharingScope } from '@/features/documents/documentsApi'

export type UploadStatus =
  | 'uploading'
  | 'paused'
  | 'processing'
  | 'completed'
  | 'failed'

export interface UploadItem {
  id: string
  /** Client-generated file UUID used as the tus `fileId` and SSE key. */
  fileId: string
  /** File name shown in the queue row. */
  name: string
  /** Material Symbols name for the file-type chip. */
  icon: string
  /** Tailwind classes tinting the file-type icon. */
  iconClassName: string
  status: UploadStatus
  /** Bytes flushed to the socket (0–1) — the leading, in-flight edge of the bar. */
  progress: number
  /** Bytes the server has confirmed (0–1) — the solid, persisted portion of the bar. */
  confirmedProgress: number
  /** Top detail line, e.g. "4.2 MB/s • 12s left". */
  detailPrimary: string
  /** Bottom detail line, e.g. "18MB / 27MB". */
  detailSecondary: string
  /** Failure reason, rendered as a callout under the file name when failed. */
  errorMessage?: string
}

export interface UploadSummary {
  completed: number
  total: number
  /** Overall progress (0–1). */
  progress: number
  /** Aggregate throughput label, e.g. "8.5 MB/s". */
  speed: string
}

/** One free-form metadata entry the user attaches before uploading. */
export interface MetadataField {
  key: string
  value: string
}

/**
 * A file the user has selected but not yet uploaded. It waits in a staging area
 * where per-file metadata is filled in; uploading starts only on confirmation.
 */
export interface StagedFile {
  id: string
  /** File name shown on the staging card. */
  name: string
  /** Size in bytes, for the size label. */
  size: number
  /** Material Symbols name for the file-type chip. */
  icon: string
  /** Tailwind classes tinting the file-type icon. */
  iconClassName: string
  /** User-entered key/value metadata sent as tus `Upload-Metadata`. */
  metadata: MetadataField[]
  /** Per-file scope override; when undefined the file inherits the general scope. */
  scopeOverride?: SharingScope
}
