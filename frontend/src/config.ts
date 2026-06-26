// Central runtime config for the BFF (Backend-for-Frontend) integration.
//
// Two kinds of URL live here, and the distinction matters:
//
//  - LOGIN/LOGOUT are ABSOLUTE to the gateway origin. They must work even when
//    the app is opened directly at http://localhost:5173 (before any session
//    exists): hitting them bounces the browser to Keycloak, and after login the
//    gateway lands the user at http://localhost:8080/ui. A relative path would
//    resolve to :5173 and 404, breaking that flow.
//  - USERINFO and the /api/v1 roots stay RELATIVE. Once the app runs at
//    :8080/ui they hit the gateway same-origin with the session cookie; at
//    :5173 they simply fail, which is what trips the redirect to login.
//
// Override the origin for other environments with VITE_GATEWAY_ORIGIN.
export const GATEWAY_ORIGIN =
  import.meta.env.VITE_GATEWAY_ORIGIN ?? 'http://localhost:8080'

// Spring's authorization-request endpoint for the confidential "gateway-client"
// registration. Hitting this triggers the full OIDC login at Keycloak.
export const LOGIN_URL = `${GATEWAY_ORIGIN}/oauth2/authorization/gateway-client`

// Spring Security logout endpoint. The gateway performs OIDC RP-initiated
// logout and redirects back to /ui?logout.
export const LOGOUT_URL = `${GATEWAY_ORIGIN}/logout`

// Returns the current user (200) or 401 when anonymous.
export const USERINFO_URL = '/api/auth/userinfo'

// Spring's CSRF cookie/header names used by the gateway's SpaCsrfTokenRequestHandler.
export const XSRF_COOKIE_NAME = 'XSRF-TOKEN'
export const XSRF_HEADER_NAME = 'X-XSRF-TOKEN'

// Backend API roots (routed by the gateway to the downstream services).
export const API = {
  files: '/api/v1/files',
  uploads: '/api/v1/uploads',
  users: '/api/v1/users',
  jobs: '/api/v1/jobs',
  rag: '/api/rag',
} as const
