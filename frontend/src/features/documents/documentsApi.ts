import { http } from '@/api/axios'
import { API } from '@/config'

// File visibility scope, mirroring the backend SharingScope enum. Declared as a
// const object (not a TS enum) so it erases cleanly under `verbatimModuleSyntax`.
export const SharingScope = {
  PERSONAL: 'PERSONAL',
  DEPARTMENT: 'DEPARTMENT',
  ORGANIZATION: 'ORGANIZATION',
} as const

export type SharingScope = (typeof SharingScope)[keyof typeof SharingScope]

// Shape returned by GET /api/v1/files. Mirrors the backend FileDTO; most fields
// are optional because they are populated at different points in a file's life.
export interface FileDTO {
  id: string
  fileName?: string
  createdAt: string
  completedAt?: string
  version?: number
  size?: number
  contentType?: string
  /** Owner UUID. */
  owner?: string
  /** Human-readable owner name. */
  ownerName?: string
  sharingScope?: SharingScope
  /** Current version status: 'AVAILABLE' (ready) or 'MERGE_PENDING' (still processing). */
  status?: string
  /** Caller's permission; only set by the shared-with-me listing. */
  permission?: string
  /** RFC-9110 timestamp of when the file was shared with the caller; shared listing only. */
  sharedAt?: string
}

// Spring Data `Page<T>` envelope.
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}

export interface GetFilesParams {
  contentType?: string
  dateFrom?: string
  dateTo?: string
  sort?: string
  page?: number
  size?: number
  /** One or more scopes; serialized as repeated `sharingScope=` query params. */
  sharingScope?: SharingScope[]
}

/**
 * Fetches a page of file metadata from the gateway-routed file service.
 *
 * The backend returns only AVAILABLE files. Errors propagate to the caller; the
 * shared axios interceptor handles 401 by redirecting to login.
 *
 * `indexes: null` makes axios serialize array params as repeated keys
 * (`sharingScope=PERSONAL&sharingScope=DEPARTMENT`) rather than the bracketed
 * `sharingScope[0]=…` form, matching Spring's `Set<SharingScope>` binding.
 */
export async function getFiles(
  params?: GetFilesParams,
): Promise<PageResponse<FileDTO>> {
  const { data } = await http.get<PageResponse<FileDTO>>(API.files, {
    params,
    paramsSerializer: { indexes: null },
  })
  return data
}
