import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import { hasRole, type Role } from '@/features/profile/roles'

interface RequireRoleProps {
  role: Role
  children: ReactNode
}

/**
 * Role gate for already-authenticated routes (sits inside ProtectedRoute, so a
 * user is guaranteed present). Users missing the required role are bounced to
 * their base profile rather than shown a forbidden page.
 */
export default function RequireRole({ role, children }: RequireRoleProps) {
  const user = useAppSelector(selectCurrentUser)

  if (!hasRole(user?.roles, role)) {
    return <Navigate to="/profile" replace />
  }

  return <>{children}</>
}
