import { useAppSelector } from '@/app/hooks'
import { selectAuthStatus } from '@/features/auth/authSlice'
import { selectConnectivity } from '@/features/health/healthSlice'

interface ServiceStatusIndicatorProps {
  /** Sidebar collapsed: render the dot only (label moves into the tooltip). */
  collapsed: boolean
}

// Combines reachability (health slice) with session validity (auth slice) into one
// coarse signal. Offline takes precedence: if we can't reach the gateway we don't
// actually know whether the session is still good.
function statusOf(connectivity: 'online' | 'offline', authenticated: boolean) {
  if (connectivity === 'offline') {
    return { label: 'Reconnecting…', dot: 'bg-ds-error animate-pulse', text: 'text-ds-error' }
  }
  if (authenticated) {
    return { label: 'Connected', dot: 'bg-ds-tertiary-fixed-dim', text: 'text-ds-secondary' }
  }
  // Online but not authenticated is a transient state (ProtectedRoute is redirecting).
  return { label: 'Signing in…', dot: 'bg-ds-secondary', text: 'text-ds-secondary' }
}

/**
 * Small backend service-status badge for the sidebar footer. Driven by the session
 * heartbeat (useSessionHeartbeat): a teal dot when connected, a pulsing red dot with
 * "Reconnecting…" when the gateway is unreachable.
 */
export default function ServiceStatusIndicator({
  collapsed,
}: ServiceStatusIndicatorProps) {
  const connectivity = useAppSelector(selectConnectivity)
  const authenticated = useAppSelector(selectAuthStatus) === 'authenticated'
  const status = statusOf(connectivity, authenticated)

  return (
    <div
      role="status"
      aria-label={`Service status: ${status.label}`}
      title={collapsed ? status.label : undefined}
      className={`flex items-center gap-2 ${collapsed ? 'justify-center' : 'px-2'}`}
    >
      <span className={`h-2 w-2 shrink-0 rounded-ds-full ${status.dot}`} />
      {!collapsed && (
        <span className={`font-ds-label text-ds-label-sm ${status.text}`}>
          {status.label}
        </span>
      )}
    </div>
  )
}
