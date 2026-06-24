import { http } from '@/api/axios'
import { API } from '@/config'
import type { FileDTO, PageResponse } from '@/features/documents/documentsApi'

/** Backend permission level on a share; maps to the view's edit/view buckets. */
export type PermissionLevel = 'READ_ONLY' | 'READ_WRITE'

/** Server-side filters for the shared-with-me listing. Undefined fields are omitted. */
export interface SharedFilesParams {
  page?: number
  size?: number
  /** Case-insensitive contains match on the current version's MIME type. */
  contentType?: string
  /** Content-type tokens to exclude (the "Other" file-type bucket); omitted means none. */
  excludeContentTypes?: string[]
  /** ISO-8601 lower/upper bounds (inclusive) on the file's createdAt. */
  dateFrom?: string
  dateTo?: string
  /** Sort spec, e.g. 'createdAt,desc'. */
  sort?: string
  /** Keep only shares at this permission level; omitted means any. */
  permission?: PermissionLevel
  /** Keep only files owned by this user id; omitted means any owner. */
  ownerId?: string
}

/**
 * Fetches a page of files shared *with* the current user, applying the given
 * server-side filters. The backend keys the recipient off the caller's JWT, and
 * each FileDTO carries the caller's `permission` and the `sharedAt` timestamp.
 * Errors propagate; the axios interceptor handles 401.
 */
export async function getSharedWithMe(
  params: SharedFilesParams = {},
): Promise<PageResponse<FileDTO>> {
  const { data } = await http.get<PageResponse<FileDTO>>(`${API.files}/my-shared`, {
    params,
    // `indexes: null` serializes the array param as repeated keys
    // (`excludeContentTypes=…&excludeContentTypes=…`), matching Spring's List binding.
    paramsSerializer: { indexes: null },
  })
  return data
}
