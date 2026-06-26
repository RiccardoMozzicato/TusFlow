// User roles as returned and accepted by the UserDetailService UserDTO (its
// UserRole enum). NOTE: these keep the `ROLE_` prefix, unlike the gateway
// userinfo roles consumed elsewhere (see features/profile/roles.ts), which the
// gateway strips. Admin user management talks to the user service directly, so
// it uses the prefixed form.
export const USER_ROLES = ['ROLE_admin', 'ROLE_manager', 'ROLE_staff'] as const

export type UserRole = (typeof USER_ROLES)[number]

// Human-friendly label per role.
export const USER_ROLE_LABELS: Record<UserRole, string> = {
  ROLE_admin: 'Admin',
  ROLE_manager: 'Manager',
  ROLE_staff: 'Staff',
}

// Badge color per role, expressed in design-system tokens (mirrors the profile
// role badges so the two surfaces stay visually consistent).
export const USER_ROLE_BADGE_CLASSES: Record<UserRole, string> = {
  ROLE_admin: 'bg-ds-primary-container text-ds-on-primary-container',
  ROLE_manager: 'bg-ds-tertiary-container text-ds-on-tertiary-container',
  ROLE_staff: 'bg-ds-secondary-container text-ds-on-secondary-container',
}

export function isUserRole(value: string): value is UserRole {
  return (USER_ROLES as readonly string[]).includes(value)
}

// Label for a role string, tolerating unknown values by echoing them back.
export function userRoleLabel(role: string): string {
  return isUserRole(role) ? USER_ROLE_LABELS[role] : role
}
