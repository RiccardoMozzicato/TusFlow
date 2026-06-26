import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { fetchUserInfo } from '@/features/auth/authApi'
import {
  selectIsAuthenticated,
  sessionExpired,
  sessionRefreshed,
} from '@/features/auth/authSlice'
import { setOffline, setOnline } from './healthSlice'

// How often to probe the gateway for session validity + reachability.
// Long enough to stay cheap (the gateway transparently refreshes the 5-min access
// token, and Keycloak SSO idle is 30 min), short enough to reflect an outage promptly.
export const HEARTBEAT_INTERVAL_MS = 120_000

/**
 * App-level session heartbeat: while authenticated, periodically probes
 * GET /api/auth/userinfo (via fetchUserInfo, which carries skipAuthRedirect so the
 * global interceptor stays out of it) and translates the three possible outcomes:
 *
 *  - 200 → sessionRefreshed + online: session valid, user fields refreshed.
 *  - 401 (resolves null) → online + sessionExpired: the gateway is reachable but the
 *    session is gone; flipping auth to anonymous makes the already-mounted
 *    ProtectedRoute redirect to login.
 *  - throws (network/5xx) → offline only: backend unreachable; auth status is left
 *    untouched so a transient blip never logs the user out.
 *
 * Polling pauses while the tab is hidden (preserving Keycloak's idle-logout for a
 * walked-away user) and fires an immediate probe when the tab becomes visible again.
 */
export function useSessionHeartbeat(): void {
  const dispatch = useAppDispatch()
  const isAuthenticated = useAppSelector(selectIsAuthenticated)

  useEffect(() => {
    if (!isAuthenticated) return

    let cancelled = false

    async function probe() {
      try {
        const user = await fetchUserInfo()
        if (cancelled) return
        dispatch(setOnline())
        if (user) {
          dispatch(sessionRefreshed(user))
        } else {
          dispatch(sessionExpired())
        }
      } catch {
        // Network error / 5xx: reachable status unknown → offline, auth untouched.
        if (!cancelled) dispatch(setOffline())
      }
    }

    const intervalId = window.setInterval(() => {
      if (!document.hidden) void probe()
    }, HEARTBEAT_INTERVAL_MS)

    // Re-check immediately when the user returns to the tab, instead of waiting out
    // a full interval to discover the session expired or the backend came back.
    function onVisibilityChange() {
      if (!document.hidden) void probe()
    }
    document.addEventListener('visibilitychange', onVisibilityChange)

    return () => {
      cancelled = true
      window.clearInterval(intervalId)
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [isAuthenticated, dispatch])
}
