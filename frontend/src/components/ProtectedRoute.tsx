import type { ReactNode } from 'react'
import { useAppSelector } from '@/app/hooks'
import { selectAuthStatus } from '@/features/auth/authSlice'
import { redirectToLogin } from '@/api/axios'
import FullPageSpinner from './FullPageSpinner'

interface ProtectedRouteProps {
  children: ReactNode
}

/**
 * Gate for authenticated routes.
 *
 * - While the userinfo probe is in flight: show a spinner.
 * - Anonymous: trigger the gateway OIDC login (full-page redirect).
 * - Authenticated: render the protected content.
 */
export default function ProtectedRoute({ children }: ProtectedRouteProps) {
  const status = useAppSelector(selectAuthStatus)

  if (status === 'idle' || status === 'loading') {
    return <FullPageSpinner />
  }

  if (status === 'anonymous') {
    redirectToLogin()
    return <FullPageSpinner label="Redirecting to sign in…" />
  }

  return <>{children}</>
}
