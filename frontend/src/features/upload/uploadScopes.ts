import { SharingScope } from '@/features/documents/documentsApi'
import { isAdmin } from '@/features/profile/roles'

export interface ScopeOption {
  value: SharingScope
  label: string
}

// All sharing scopes, in increasing visibility order.
export const ALL_SCOPE_OPTIONS: ScopeOption[] = [
  { value: SharingScope.PERSONAL, label: 'Personal' },
  { value: SharingScope.DEPARTMENT, label: 'Department' },
  { value: SharingScope.ORGANIZATION, label: 'Organization' },
]

/**
 * Sharing scopes a user may upload into. Organization-wide visibility is
 * reserved for admins; managers and staff can only share Personal or
 * Department.
 */
export function allowedUploadScopes(roles: readonly string[] | undefined): ScopeOption[] {
  const canShareOrg = isAdmin(roles)
  return ALL_SCOPE_OPTIONS.filter(
    (option) => option.value !== SharingScope.ORGANIZATION || canShareOrg,
  )
}
