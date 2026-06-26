import { isUserRole, type UserRole } from './userRoles'
import type { UserDTO } from './usersApi'

// Fraction of quota at which a user is flagged as "near limit".
const NEAR_LIMIT_FRACTION = 0.9

// Label used when a user has no department set.
export const UNASSIGNED_DEPARTMENT = 'Unassigned'

export interface DepartmentCount {
  name: string
  count: number
}

// Aggregate, organization-scoped view derived purely from the user list the
// backend returns (GET /api/v1/users is scoped to the caller's organization).
export interface OrgStats {
  totalUsers: number
  roleCounts: Record<UserRole, number>
  /** Distinct real departments (excludes the "Unassigned" bucket). */
  departmentCount: number
  /** Department buckets sorted by member count, descending. */
  departments: DepartmentCount[]
  /** Sum of bytes used across all users. */
  storageUsed: number
  /** Sum of finite quotas (unlimited users excluded). */
  storageQuota: number
  /** Users with no quota set. */
  unlimitedCount: number
  /** Users at or above NEAR_LIMIT_FRACTION of their finite quota. */
  nearLimitCount: number
}

/** Aggregates a list of users into the organization overview stats. */
export function computeOrgStats(users: UserDTO[]): OrgStats {
  const roleCounts: Record<UserRole, number> = {
    ROLE_admin: 0,
    ROLE_manager: 0,
    ROLE_staff: 0,
  }
  const deptCounts = new Map<string, number>()
  let storageUsed = 0
  let storageQuota = 0
  let unlimitedCount = 0
  let nearLimitCount = 0

  for (const user of users) {
    if (isUserRole(user.role)) roleCounts[user.role] += 1

    const dept = user.department?.trim() || UNASSIGNED_DEPARTMENT
    deptCounts.set(dept, (deptCounts.get(dept) ?? 0) + 1)

    const used = user.storageUsed ?? 0
    storageUsed += used

    if (user.storageQuota == null) {
      unlimitedCount += 1
    } else {
      storageQuota += user.storageQuota
      if (user.storageQuota > 0 && used / user.storageQuota >= NEAR_LIMIT_FRACTION) {
        nearLimitCount += 1
      }
    }
  }

  const departments = [...deptCounts.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)

  return {
    totalUsers: users.length,
    roleCounts,
    departmentCount: departments.filter((d) => d.name !== UNASSIGNED_DEPARTMENT).length,
    departments,
    storageUsed,
    storageQuota,
    unlimitedCount,
    nearLimitCount,
  }
}
