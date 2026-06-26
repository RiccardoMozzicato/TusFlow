import { isUserRole, USER_ROLE_BADGE_CLASSES, userRoleLabel } from '../userRoles'

// Small pill rendering a user's role with role-specific design tokens. Unknown
// roles fall back to a neutral surface chip.
export default function RoleBadge({ role }: { role: string }) {
  const className = isUserRole(role)
    ? USER_ROLE_BADGE_CLASSES[role]
    : 'bg-ds-surface-variant text-ds-on-surface-variant'

  return (
    <span
      className={`inline-flex rounded-ds-full px-3 py-1 font-ds-label text-ds-label-sm ${className}`}
    >
      {userRoleLabel(role)}
    </span>
  )
}
