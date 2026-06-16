import { http } from '@/api/axios'
import { API } from '@/config'
import type { PageResponse } from '@/features/documents/documentsApi'
import type { UserRole } from './userRoles'

// Shape returned by GET /api/v1/users/me (UserDetailService UserDTO).
export interface UserDTO {
  id: string
  fullName: string
  email: string
  organization: string
  department?: string | null
  /** Prefixed role name, e.g. "ROLE_admin" (UserDetailService UserRole enum). */
  role: string
  /** Storage quota in bytes; null/absent means no quota (unlimited). */
  storageQuota?: number | null
  /** Bytes currently used. */
  storageUsed?: number
}

// Body for POST /api/v1/users (UserDetailService UserCreationDTO). The full name
// is sourced from Keycloak, so creation takes a username rather than fullName.
export interface UserCreationPayload {
  username: string
  email: string
  organization: string
  role: UserRole
  department?: string | null
  storageQuota?: number | null
}

/**
 * Fetches the authenticated user's profile, including live storage usage.
 * Errors propagate to the caller; the shared axios interceptor handles 401.
 */
export async function getCurrentUser(): Promise<UserDTO> {
  const { data } = await http.get<UserDTO>(`${API.users}/me`)
  return data
}

/**
 * Fetches a page of users in the caller's organization (the backend scopes the
 * list by the authenticated user's org). Used to power share suggestions.
 * Defaults to a generous page so typical orgs fit in one request.
 */
export async function getUsers(page = 0, size = 200): Promise<PageResponse<UserDTO>> {
  const { data } = await http.get<PageResponse<UserDTO>>(API.users, {
    params: { page, size },
  })
  return data
}

/**
 * Loads every user in the caller's organization by walking the paginated list.
 * The backend has no aggregate endpoint, so callers that need the whole set
 * (stats, department rosters) page through it here. Capped by `maxPages`.
 */
export async function fetchAllUsers(pageSize = 200, maxPages = 50): Promise<UserDTO[]> {
  const all: UserDTO[] = []
  for (let page = 0; page < maxPages; page += 1) {
    const response = await getUsers(page, pageSize)
    all.push(...response.content)
    if (response.last || response.content.length === 0) break
  }
  return all
}

/** Creates a new user (admin only). Resolves on 201, rejects otherwise. */
export async function createUser(payload: UserCreationPayload): Promise<void> {
  await http.post(API.users, payload)
}

/**
 * Updates an existing user (admin only). The backend validates a full UserDTO,
 * so callers pass the whole record with the changed fields (role / quota)
 * already applied.
 */
export async function updateUser(user: UserDTO): Promise<void> {
  await http.put(`${API.users}/${user.id}`, user)
}

/** Deletes a user by id (admin only). */
export async function deleteUser(id: string): Promise<void> {
  await http.delete(`${API.users}/${id}`)
}
