import { http } from '@/api/axios'
import { API } from '@/config'
import type { FileDTO, PageResponse } from '@/features/documents/documentsApi'

/**
 * Fetches a page of files shared *with* the current user. The backend keys off
 * the caller's JWT, and each FileDTO carries the caller's `permission` and the
 * `sharedAt` timestamp. Errors propagate; the axios interceptor handles 401.
 */
export async function getSharedWithMe(
  page = 0,
  size = 10,
): Promise<PageResponse<FileDTO>> {
  const { data } = await http.get<PageResponse<FileDTO>>(`${API.files}/my-shared`, {
    params: { page, size },
  })
  return data
}
