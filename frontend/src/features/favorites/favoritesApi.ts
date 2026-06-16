import { http } from '@/api/axios'
import { API } from '@/config'
import type { PageResponse } from '@/features/documents/documentsApi'

/** One favorited file version. Mirrors the backend FileVersionFavoriteDTO. */
export interface FileVersionFavoriteDTO {
  id: number | null
  fileId: string
  /** Filename of the favorited version (may differ from the file's latest name). */
  fileName: string
  version: number
  size: number | null
  contentType: string | null
  status: string
  ownerId: string
  /** Owner's display name, resolved server-side; null when unavailable. */
  ownerName?: string | null
  /** RFC 9110 date of when the user favorited this version. */
  favoritedAt: string
}

/**
 * Fetches a page of the current user's favorited file versions. The backend
 * keys off the caller's JWT. Errors propagate; the axios interceptor handles 401.
 */
export async function getMyFavorites(
  page = 0,
  size = 10,
): Promise<PageResponse<FileVersionFavoriteDTO>> {
  const { data } = await http.get<PageResponse<FileVersionFavoriteDTO>>(`${API.files}/favorites`, {
    params: { page, size },
  })
  return data
}
