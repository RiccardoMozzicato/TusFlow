import { describe, expect, it } from 'vitest'
import { computeOrgStats, UNASSIGNED_DEPARTMENT } from './orgStats'
import { UNIT_BYTES } from './userQuota'
import type { UserDTO } from './usersApi'

function user(overrides: Partial<UserDTO> = {}): UserDTO {
  return {
    id: crypto.randomUUID(),
    fullName: 'Test User',
    email: 'test@example.com',
    organization: 'Acme',
    department: 'Engineering',
    role: 'ROLE_staff',
    storageQuota: 10 * UNIT_BYTES.GB,
    storageUsed: 0,
    ...overrides,
  }
}

describe('computeOrgStats', () => {
  it('counts users by role', () => {
    const stats = computeOrgStats([
      user({ role: 'ROLE_admin' }),
      user({ role: 'ROLE_manager' }),
      user({ role: 'ROLE_staff' }),
      user({ role: 'ROLE_staff' }),
    ])
    expect(stats.totalUsers).toBe(4)
    expect(stats.roleCounts).toEqual({ ROLE_admin: 1, ROLE_manager: 1, ROLE_staff: 2 })
  })

  it('buckets departments and excludes Unassigned from the distinct count', () => {
    const stats = computeOrgStats([
      user({ department: 'Engineering' }),
      user({ department: 'Engineering' }),
      user({ department: 'Finance' }),
      user({ department: '  ' }),
      user({ department: null }),
    ])
    expect(stats.departmentCount).toBe(2)
    expect(stats.departments[0]).toEqual({ name: 'Engineering', count: 2 })
    expect(stats.departments.find((d) => d.name === UNASSIGNED_DEPARTMENT)?.count).toBe(2)
  })

  it('sums used bytes and finite quotas, tracking unlimited users', () => {
    const stats = computeOrgStats([
      user({ storageUsed: 2 * UNIT_BYTES.GB, storageQuota: 10 * UNIT_BYTES.GB }),
      user({ storageUsed: 3 * UNIT_BYTES.GB, storageQuota: null }),
    ])
    expect(stats.storageUsed).toBe(5 * UNIT_BYTES.GB)
    expect(stats.storageQuota).toBe(10 * UNIT_BYTES.GB)
    expect(stats.unlimitedCount).toBe(1)
  })

  it('flags users at or above 90% of their quota', () => {
    const stats = computeOrgStats([
      user({ storageUsed: 9 * UNIT_BYTES.GB, storageQuota: 10 * UNIT_BYTES.GB }),
      user({ storageUsed: 8 * UNIT_BYTES.GB, storageQuota: 10 * UNIT_BYTES.GB }),
      user({ storageUsed: 100, storageQuota: null }),
    ])
    expect(stats.nearLimitCount).toBe(1)
  })

  it('handles an empty organization', () => {
    const stats = computeOrgStats([])
    expect(stats.totalUsers).toBe(0)
    expect(stats.storageQuota).toBe(0)
    expect(stats.departmentCount).toBe(0)
  })
})
