import { http } from '@/api/axios'
import { API } from '@/config'

// Mirrors the backend PendingUploadDTO. Deliberately carries NO offset — the tus
// HEAD on /uploads/{fileId} is the authority on how many bytes the server has.
export interface PendingUpload {
  fileId: string
  filename?: string
  contentType?: string
  size?: number
  /** 'PENDING' | 'UPLOADING' (the resumable statuses the backend returns). */
  status: string
  /** RFC-9110 timestamp of when the upload was created. */
  createdAt: string
}

/**
 * Fetches the current user's resumable in-flight uploads. Restore intersects this
 * server-authoritative set with the locally-persisted blobs, so a user only ever
 * resumes their own uploads. Errors propagate; the axios interceptor handles 401.
 */
export async function getMyPendingUploads(): Promise<PendingUpload[]> {
  const { data } = await http.get<PendingUpload[]>(`${API.files}/my-uploads`)
  return data
}
