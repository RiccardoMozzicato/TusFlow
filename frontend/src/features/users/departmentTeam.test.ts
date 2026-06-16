import { describe, expect, it } from 'vitest'
import { computeDepartmentTeam } from './departmentTeam'
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

describe('computeDepartmentTeam', () => {
  it('keeps only members of the target department, matched case-insensitively', () => {
    const team = computeDepartmentTeam(
      [
        user({ department: 'Engineering', fullName: 'Zoe' }),
        user({ department: 'engineering', fullName: 'Ada' }),
        user({ department: 'Finance', fullName: 'Bob' }),
      ],
      'Engineering',
    )
    expect(team.memberCount).toBe(2)
    // Sorted by display name.
    expect(team.members.map((m) => m.fullName)).toEqual(['Ada', 'Zoe'])
  })

  it('counts roles and sums storage, tracking unlimited members', () => {
    const team = computeDepartmentTeam(
      [
        user({ role: 'ROLE_manager', storageUsed: UNIT_BYTES.GB, storageQuota: 10 * UNIT_BYTES.GB }),
        user({ role: 'ROLE_staff', storageUsed: 2 * UNIT_BYTES.GB, storageQuota: null }),
      ],
      'Engineering',
    )
    expect(team.roleCounts).toEqual({ ROLE_admin: 0, ROLE_manager: 1, ROLE_staff: 1 })
    expect(team.storageUsed).toBe(3 * UNIT_BYTES.GB)
    expect(team.storageQuota).toBe(10 * UNIT_BYTES.GB)
    expect(team.unlimitedCount).toBe(1)
  })

  it('returns an empty team when nobody matches', () => {
    const team = computeDepartmentTeam([user({ department: 'Finance' })], 'Engineering')
    expect(team.memberCount).toBe(0)
    expect(team.members).toEqual([])
  })
})
