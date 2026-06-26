import { SharingScope } from '@/features/documents/documentsApi'
import { isAdmin, isManager } from '@/features/profile/roles'

export interface ScopeOption {
  value: SharingScope
  label: string
  /** Material Symbols icon name for the scope. */
  icon: string
  /** One-line explanation of who can access files shared at this scope. */
  description: string
}

// All sharing scopes, in increasing visibility order.
export const ALL_SCOPE_OPTIONS: ScopeOption[] = [
  {
    value: SharingScope.PERSONAL,
    label: 'Personal',
    icon: 'person',
    description: 'Only you can access these files.',
  },
  {
    value: SharingScope.DEPARTMENT,
    label: 'Department',
    icon: 'groups',
    description: 'Your department can access these files.',
  },
  {
    value: SharingScope.ORGANIZATION,
    label: 'Organization',
    icon: 'corporate_fare',
    description: 'Everyone in the organization can access these files.',
  },
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
