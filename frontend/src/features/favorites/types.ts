import type { FileType } from '@/features/documents/types'

/** Presentation model for one row in the Favorites table. */
export interface FavoriteDocument {
  /** Unique row key (fileId + version, since the same file can appear twice). */
  id: string
  /** Document UUID, used for navigation and downloads. */
  fileId: string
  title: string
  /** Numeric backend version, sent as the `version` query param when downloading. */
  version: number
  /** Version label (e.g. "v3"). */
  versionLabel: string
  /** Drives the file-type icon. */
  fileType: FileType
  /** Human file-type label (e.g. "PDF Document"). */
  typeLabel: string
  /** Pre-formatted size (e.g. "2.4 MB"); "—" when unknown. */
  size: string
  /** Owner display name (or UUID fallback). */
  owner: string
  /** Pre-formatted favorited date (e.g. "Oct 12, 2023"). */
  favoritedDate: string
}
