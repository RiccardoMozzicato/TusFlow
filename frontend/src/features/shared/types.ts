import type { FileType } from '@/features/documents/types'

/** Coarse permission bucket used by the Shared-with-me view. */
export type SharePermission = 'edit' | 'view'

/** Presentation model for one row in the Shared-with-me table. */
export interface SharedDocument {
  id: string
  title: string
  /** Drives the file-type icon. */
  fileType: FileType
  /** Human file-type label (e.g. "PDF Document"). */
  typeLabel: string
  /** Pre-formatted size (e.g. "2.4 MB"); "—" when unknown. */
  size: string
  /** Owner display name (or UUID fallback). */
  owner: string
  /** Pre-formatted share date (e.g. "Oct 12, 2023"). */
  sharedDate: string
  permission: SharePermission
}
