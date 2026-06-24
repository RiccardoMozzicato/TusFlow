import { http } from '@/api/axios'
import { API } from '@/config'
import type { FileDTO, PageResponse } from './documentsApi'

// Permission level for a share, mirroring the backend PERMISSION_LEVEL enum.
// Declared as a const object (not a TS enum) so it erases cleanly under
// `verbatimModuleSyntax`.
export const Permission = {
  READ_ONLY: 'READ_ONLY',
  READ_WRITE: 'READ_WRITE',
} as const

export type Permission = (typeof Permission)[keyof typeof Permission]

// Sensitivity classification levels accepted by the AI-metadata endpoint.
// Mirrors the backend SensitivityLevel enum; values are sent verbatim.
export const SensitivityLevel = {
  PUBLIC: 'PUBLIC',
  INTERNAL: 'INTERNAL',
  CONFIDENTIAL: 'CONFIDENTIAL',
  HIGHLY_CONFIDENTIAL: 'HIGHLY_CONFIDENTIAL',
} as const

export type SensitivityLevel = (typeof SensitivityLevel)[keyof typeof SensitivityLevel]

// Editable subset of AI-generated metadata. Only these keys are accepted by the
// backend's generatedmetadata endpoint (summary, tags, sensitivity).
export interface GeneratedMetadataUpdate {
  summary?: string
  tags?: string
  sensitivity?: string
}

// AI-generated metadata for a file version. Mirrors the backend MetadataAIDTO.
// Most fields are nullable because they are filled in asynchronously by the AI
// processing pipeline (and may be absent while a file is still processing).
export interface MetadataAIDTO {
  /** Comma-separated tag list, e.g. "Q3,2023,Final". */
  tags?: string | null
  summary?: string | null
  filename?: string | null
  contentType?: string | null
  ownerUserId?: string | null
  sensitivity?: string | null
  fileVersionId?: string | null
  analysisStatus?: string | null
  /** File lifecycle status: MERGE_PENDING | AVAILABLE | DELETED. */
  status: string
}

// One version of a file. Mirrors the backend FileVersionDTO.
export interface FileVersionDTO {
  id: number | null
  version: number
  filename: string
  size: number
  contentType: string
  checksum?: string | null
  status: string
  metadata?: Record<string, string> | null
  /** ISO timestamps. */
  createdAt: string
  lastUpdateAt: string
}

// A single share record. Mirrors the backend FileShareDTO.
export interface FileShareDTO {
  id: number | null
  fileID: string
  userID: string
  permission: Permission
  timestamp: string
  /** Recipient's display name, resolved server-side; null when unavailable. */
  userName?: string | null
}

const fileUrl = (id: string) => `${API.files}/${id}`

/** Builds the inline-preview URL for a file (browser hits it with the session cookie). */
export function previewUrl(id: string): string {
  return `${fileUrl(id)}/preview`
}

/** Builds the download URL for a file. */
export function downloadUrl(id: string): string {
  return `${fileUrl(id)}/download`
}

/**
 * Absolute URL of a document's details PAGE (not the API), for "Copy link".
 * Honors the app's base path (Vite `base` / router basename, e.g. `/ui/`) so the
 * link resolves through the gateway — `import.meta.env.BASE_URL` is `/ui/`.
 */
export function documentPageUrl(id: string): string {
  return `${window.location.origin}${import.meta.env.BASE_URL}documents/${id}`
}

/**
 * Fetches the file's bytes for inline preview. We pull them via the authed
 * client and render from an object URL rather than pointing an iframe at the
 * preview URL directly — the gateway's default X-Frame-Options: DENY blocks
 * embedding that URL, but a same-origin blob: URL is not subject to it.
 */
export async function getPreviewBlob(id: string): Promise<Blob> {
  const { data } = await http.get<Blob>(previewUrl(id), { responseType: 'blob' })
  return data
}

/**
 * Fetches the file's bytes for download via the authed client. We pull the blob
 * (rather than navigating to the download URL) so the session cookie/CSRF are
 * applied and a 401 is handled by the global interceptor.
 *
 * Pass `version` to download a specific historical version; omit it for the
 * current one. The backend merges a still-pending version on demand.
 */
export async function getDownloadBlob(id: string, version?: number): Promise<Blob> {
  const { data } = await http.get<Blob>(downloadUrl(id), {
    responseType: 'blob',
    params: version != null ? { version } : undefined,
  })
  return data
}

/** Fetches core metadata for a single file. 404 if the file does not exist. */
export async function getFileMetadata(id: string): Promise<FileDTO> {
  const { data } = await http.get<FileDTO>(fileUrl(id))
  return data
}

/**
 * Fetches AI-generated metadata for a file's current version. Returns null when
 * none exists yet (the file is still processing), rather than throwing on 404.
 */
export async function getGeneratedMetadata(id: string): Promise<MetadataAIDTO | null> {
  try {
    const { data } = await http.get<MetadataAIDTO>(`${API.files}/generatedmetadata/${id}`)
    return data
  } catch {
    // AI metadata is optional and may legitimately be absent (still processing).
    return null
  }
}

/**
 * Updates AI-generated metadata (summary, tags, sensitivity) for a file version.
 * The current version must be passed explicitly: unlike the read path (which
 * defaults to the current version), the backend's update path falls back to
 * version 1 when omitted, so leaving it out would edit the wrong version.
 */
export async function updateGeneratedMetadata(
  id: string,
  version: number,
  patch: GeneratedMetadataUpdate,
): Promise<void> {
  await http.patch(`${API.files}/generatedmetadata/${id}`, patch, {
    params: { version },
  })
}

/**
 * Updates a file version's user (non-AI) metadata. The backend merges the given
 * map into the existing metadata (it never deletes keys), so callers blank a key
 * with an empty string to "remove" it. Keys must be ASCII; requires write access.
 */
export async function updateFileVersionMetadata(
  id: string,
  version: number,
  metadata: Record<string, string>,
): Promise<void> {
  await http.patch(`${fileUrl(id)}/versions/${version}`, metadata)
}

/**
 * Returns the subset of the given file ids the current user may modify. Used to
 * decide whether to surface edit affordances. Returns an empty list without a
 * round-trip when no candidate ids are provided.
 */
export async function getWritableFileIds(ids: string[]): Promise<string[]> {
  if (ids.length === 0) return []
  const { data } = await http.post<string[]>(`${API.files}/writable-ids`, ids)
  return data
}

/** Convenience: whether the current user has write access to a single file. */
export async function isFileWritable(id: string): Promise<boolean> {
  const writable = await getWritableFileIds([id])
  return writable.includes(id)
}

/** Fetches all versions of a file, newest-first handling left to the mapper. */
export async function getFileVersions(id: string): Promise<FileVersionDTO[]> {
  const { data } = await http.get<FileVersionDTO[]>(`${fileUrl(id)}/versions`)
  return data
}

/** Fetches a page of share records for a file. */
export async function getFileShares(
  id: string,
  page = 0,
  size = 20,
): Promise<PageResponse<FileShareDTO>> {
  const { data } = await http.get<PageResponse<FileShareDTO>>(`${fileUrl(id)}/shares`, {
    params: { page, size },
  })
  return data
}

/** Marks a specific version as favorite for the current user. Idempotent. */
export async function addFavorite(id: string, version: number): Promise<void> {
  await http.post(`${fileUrl(id)}/versions/${version}/favorite`)
}

/** Removes the current user's favorite on a specific version. Idempotent. */
export async function removeFavorite(id: string, version: number): Promise<void> {
  await http.delete(`${fileUrl(id)}/versions/${version}/favorite`)
}

/** Fetches the version numbers of this file favorited by the current user. */
export async function getFavoritedVersions(id: string): Promise<number[]> {
  const { data } = await http.get<number[]>(`${fileUrl(id)}/favorites`)
  return data
}

/** Shares a file with a user at the given permission level. */
export async function createFileShare(
  id: string,
  userId: string,
  permission: Permission,
): Promise<FileShareDTO> {
  const { data } = await http.post<FileShareDTO>(`${fileUrl(id)}/shares`, null, {
    params: { userId, permission },
  })
  return data
}

/** Updates an existing share's permission level (READ_ONLY ↔ READ_WRITE). Owner-only. */
export async function updateFileShare(
  id: string,
  shareId: number,
  permission: Permission,
): Promise<FileShareDTO> {
  const { data } = await http.patch<FileShareDTO>(`${fileUrl(id)}/shares/${shareId}`, null, {
    params: { permission },
  })
  return data
}

/** Removes a share from a file. Owner-only. Idempotent from the caller's view. */
export async function deleteFileShare(id: string, shareId: number): Promise<void> {
  await http.delete(`${fileUrl(id)}/shares/${shareId}`)
}

/** Permanently deletes a file and all its versions. Requires write access. */
export async function deleteFile(id: string): Promise<void> {
  await http.delete(fileUrl(id))
}
