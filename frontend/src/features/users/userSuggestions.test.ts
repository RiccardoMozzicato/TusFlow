import { describe, expect, it } from 'vitest'
import { filterUserSuggestions, isUuid } from './userSuggestions'
import type { UserDTO } from './usersApi'

function user(id: string, fullName: string, email: string): UserDTO {
  return { id, fullName, email, organization: 'Org', role: 'staff' }
}

const USERS: UserDTO[] = [
  user('1', 'Alice Johnson', 'alice@acme.io'),
  user('2', 'Bob Martin', 'bob@acme.io'),
  user('3', 'Carla Jones', 'carla@acme.io'),
]

describe('isUuid', () => {
  it('accepts a canonical UUID (trimmed, case-insensitive)', () => {
    expect(isUuid('123e4567-e89b-12d3-a456-426614174000')).toBe(true)
    expect(isUuid('  123E4567-E89B-12D3-A456-426614174000 ')).toBe(true)
  })

  it('rejects non-UUID text', () => {
    expect(isUuid('alice')).toBe(false)
    expect(isUuid('123')).toBe(false)
    expect(isUuid('')).toBe(false)
  })
})

describe('filterUserSuggestions', () => {
  const noExclusions = new Set<string>()

  it('matches on name (case-insensitive)', () => {
    const result = filterUserSuggestions(USERS, 'jo', noExclusions)
    expect(result.map((u) => u.id)).toEqual(['1', '3']) // Johnson, Jones
  })

  it('matches on email', () => {
    const result = filterUserSuggestions(USERS, 'bob@', noExclusions)
    expect(result.map((u) => u.id)).toEqual(['2'])
  })

  it('excludes already-shared / owner ids', () => {
    const result = filterUserSuggestions(USERS, 'jo', new Set(['1']))
    expect(result.map((u) => u.id)).toEqual(['3'])
  })

  it('returns a browse list (no query) capped at the limit', () => {
    const result = filterUserSuggestions(USERS, '', noExclusions, 2)
    expect(result.map((u) => u.id)).toEqual(['1', '2'])
  })

  it('caps matched results at the limit', () => {
    const result = filterUserSuggestions(USERS, 'a', noExclusions, 1)
    expect(result).toHaveLength(1)
  })
})
