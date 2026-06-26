import type { AxiosRequestConfig } from 'axios'
import { http, type RequestMeta } from '@/api/axios'
import { USERINFO_URL } from '@/config'
import type { UserInfo } from './types'

/**
 * Fetches the current user from the gateway.
 *
 * Returns the UserInfo when a session exists, or null on 401 (anonymous).
 * We pass skipAuthRedirect so the bootstrap probe does not bounce the browser
 * to login — that decision belongs to the routing layer (ProtectedRoute).
 */
export async function fetchUserInfo(): Promise<UserInfo | null> {
  try {
    // skipAuthRedirect is custom request meta consumed by the axios interceptor.
    const config: AxiosRequestConfig & RequestMeta = { skipAuthRedirect: true }
    const { data } = await http.get<UserInfo>(USERINFO_URL, config)
    return data
  } catch (error) {
    if (isUnauthorized(error)) {
      return null
    }
    throw error
  }
}

export async function fetchCurrentUserFromService(): Promise<UserInfo | null> {
    try {
        // skipAuthRedirect is custom request meta consumed by the axios interceptor.
        const config: AxiosRequestConfig & RequestMeta = { skipAuthRedirect: true }
        const { data } = await http.get<UserInfo>("/api/v1/users/me", config)
        return data
    } catch (error) {
        if (isUnauthorized(error)) {
            return null
        }
        throw error
    }
}

function isUnauthorized(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    (error as { response?: { status?: number } }).response?.status === 401
  )
}
