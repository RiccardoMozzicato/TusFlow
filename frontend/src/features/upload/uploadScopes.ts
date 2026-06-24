import { SharingScope } from '@/features/documents/documentsApi'
import { isAdmin, isManager } from '@/features/profile/roles'

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
 * Sharing scopes a user may upload into, mirroring the backend authorization
 * rules: Organization-wide visibility is reserved for admins, Department for
 * managers and admins, and staff can only share Personal.
 */
export function allowedUploadScopes(roles: readonly string[] | undefined): ScopeOption[] {
  const canShareOrg = isAdmin(roles)
  const canShareDepartment = isAdmin(roles) || isManager(roles)
  return ALL_SCOPE_OPTIONS.filter((option) => {
    if (option.value === SharingScope.ORGANIZATION) return canShareOrg
    if (option.value === SharingScope.DEPARTMENT) return canShareDepartment
    return true
  })
}
