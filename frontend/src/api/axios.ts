import axios, { AxiosError } from 'axios'
import { LOGIN_URL, XSRF_COOKIE_NAME, XSRF_HEADER_NAME } from '@/config'

// Single Axios instance for the whole app.
//
// - withCredentials: send the gateway session cookie on every request.
// - xsrf*: Axios reads the non-HttpOnly XSRF-TOKEN cookie set by the gateway
//   and echoes it as the X-XSRF-TOKEN header on mutating requests, satisfying
//   the gateway's SpaCsrfTokenRequestHandler.
// - baseURL is intentionally relative: the app is same-origin with the gateway.
export const http = axios.create({
  baseURL: '/',
  withCredentials: true,
  xsrfCookieName: XSRF_COOKIE_NAME,
  xsrfHeaderName: XSRF_HEADER_NAME,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Set to false in places where you want to handle a 401 yourself (e.g. the
// initial userinfo probe) instead of being bounced to the login page.
export interface RequestMeta {
  skipAuthRedirect?: boolean
}

/**
 * Redirects the browser to the gateway-driven OIDC login. This is a full-page
 * navigation, not an XHR — the gateway needs to perform redirects to Keycloak.
 */
export function redirectToLogin(): void {
  window.location.href = LOGIN_URL
}

// Global 401 handler: any authenticated call that comes back 401 means the
// session expired — send the user through login again.
http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const config = error.config as (typeof error.config & RequestMeta) | undefined
    const isUnauthorized = error.response?.status === 401
    if (isUnauthorized && !config?.skipAuthRedirect) {
      redirectToLogin()
    }
    return Promise.reject(error)
  },
)
