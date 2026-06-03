export type UploadStatus =
  | 'uploading'
  | 'paused'
  | 'processing'
  | 'completed'
  | 'failed'

export interface UploadItem {
  id: string
  /** File name shown in the queue row. */
  name: string
  /** Material Symbols name for the file-type chip. */
  icon: string
  /** Tailwind classes tinting the file-type icon. */
  iconClassName: string
  status: UploadStatus
  /** Progress bar fill (0–1). Ignored for `processing` (indeterminate). */
  progress: number
  /** Top detail line, e.g. "4.2 MB/s • 12s left" or an error reason. */
  detailPrimary: string
  /** Bottom detail line, e.g. "18MB / 27MB". */
  detailSecondary: string
}

export interface UploadSummary {
  completed: number
  total: number
  /** Overall progress (0–1). */
  progress: number
  /** Aggregate throughput label, e.g. "8.5 MB/s". */
  speed: string
}
