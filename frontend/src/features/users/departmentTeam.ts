import { isUserRole, type UserRole } from './userRoles'
import type { UserDTO } from './usersApi'

// A manager's own department, derived from the org user list.
export interface DepartmentTeam {
  department: string
  /** Members of the department, sorted by display name. */
  members: UserDTO[]
  memberCount: number
  roleCounts: Record<UserRole, number>
  /** Sum of bytes used across members. */
  storageUsed: number
  /** Sum of finite quotas (unlimited members excluded). */
  storageQuota: number
  unlimitedCount: number
}

function displayName(user: UserDTO): string {
  return user.fullName || user.email
}

/** Filters the org users to a single department and aggregates its team stats. */
export function computeDepartmentTeam(users: UserDTO[], department: string): DepartmentTeam {
  const target = department.trim().toLowerCase()

  const members = users
    .filter((u) => (u.department?.trim().toLowerCase() ?? '') === target)
    .sort((a, b) => displayName(a).localeCompare(displayName(b)))

  const roleCounts: Record<UserRole, number> = {
    ROLE_admin: 0,
    ROLE_manager: 0,
    ROLE_staff: 0,
  }
  let storageUsed = 0
  let storageQuota = 0
  let unlimitedCount = 0

  for (const member of members) {
    if (isUserRole(member.role)) roleCounts[member.role] += 1
    storageUsed += member.storageUsed ?? 0
    if (member.storageQuota == null) {
      unlimitedCount += 1
    } else {
      storageQuota += member.storageQuota
    }
  }

  return {
    department,
    members,
    memberCount: members.length,
    roleCounts,
    storageUsed,
    storageQuota,
    unlimitedCount,
  }
}
