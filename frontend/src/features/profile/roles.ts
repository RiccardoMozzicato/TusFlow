// Realm roles exposed by the gateway (GET /api/auth/userinfo). Mirrors the
// backend g11 UserRole enum (ROLE_admin / ROLE_manager / ROLE_staff) after the
// gateway strips the ROLE_ prefix. Hierarchy: admin > manager > staff.
export const ROLES = {
  admin: 'admin',
  manager: 'manager',
  staff: 'staff',
} as const

export type Role = (typeof ROLES)[keyof typeof ROLES]

const ROLE_RANK: Record<Role, number> = {
  admin: 3,
  manager: 2,
  staff: 1,
}

// Human-friendly label for the role badge.
export const ROLE_LABELS: Record<Role, string> = {
  admin: 'Global Admin',
  manager: 'Manager',
  staff: 'Staff Member',
}

// Badge color per role, expressed in design-system tokens.
export const ROLE_BADGE_CLASSES: Record<Role, string> = {
  admin: 'bg-ds-primary-container text-ds-on-primary-container',
  manager: 'bg-ds-tertiary-container text-ds-on-tertiary-container',
  staff: 'bg-ds-secondary-container text-ds-on-secondary-container',
}

function isRole(value: string): value is Role {
  return value === ROLES.admin || value === ROLES.manager || value === ROLES.staff
}

export function hasRole(roles: readonly string[] | undefined, role: Role): boolean {
  return roles?.includes(role) ?? false
}

export function isAdmin(roles?: readonly string[]): boolean {
  return hasRole(roles, ROLES.admin)
}

export function isManager(roles?: readonly string[]): boolean {
  return hasRole(roles, ROLES.manager)
}

// The most privileged role the user holds; defaults to staff when none match.
export function highestRole(roles: readonly string[] | undefined): Role {
  const known = (roles ?? []).filter(isRole)
  if (known.length === 0) return ROLES.staff
  return known.reduce((best, r) => (ROLE_RANK[r] > ROLE_RANK[best] ? r : best))
}
