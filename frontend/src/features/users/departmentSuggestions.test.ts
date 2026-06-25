import { describe, expect, it } from 'vitest'
import { filterDepartmentSuggestions } from './departmentSuggestions'
import type { DepartmentDTO } from './usersApi'

const DEPARTMENTS: DepartmentDTO[] = [
  { name: 'Engineering', userCount: 5 },
  { name: 'Finance', userCount: 3 },
  { name: 'IT', userCount: 2 },
]

describe('filterDepartmentSuggestions', () => {
  it('returns a browse list (no query) capped at the limit', () => {
    const result = filterDepartmentSuggestions(DEPARTMENTS, '', 2)
    expect(result.map((d) => d.name)).toEqual(['Engineering', 'Finance'])
  })

  it('matches on name (case-insensitive substring)', () => {
    const result = filterDepartmentSuggestions(DEPARTMENTS, 'in')
    expect(result.map((d) => d.name)).toEqual(['Engineering', 'Finance'])
  })

  it('caps matched results at the limit', () => {
    const result = filterDepartmentSuggestions(DEPARTMENTS, '', 1)
    expect(result).toHaveLength(1)
  })

  it('returns no suggestion when the query exactly matches a single department', () => {
    // The user has already typed it in full — a lone redundant row is just noise.
    expect(filterDepartmentSuggestions(DEPARTMENTS, 'finance')).toEqual([])
    expect(filterDepartmentSuggestions(DEPARTMENTS, 'IT')).toEqual([])
  })

  it('still suggests when a query is a prefix of several departments', () => {
    const departments: DepartmentDTO[] = [
      { name: 'IT', userCount: 2 },
      { name: 'IT Support', userCount: 1 },
    ]
    // "it" matches both, so the exact-match suppression must not kick in.
    expect(filterDepartmentSuggestions(departments, 'it').map((d) => d.name)).toEqual([
      'IT',
      'IT Support',
    ])
  })

  it('returns an empty list when nothing matches', () => {
    expect(filterDepartmentSuggestions(DEPARTMENTS, 'zzz')).toEqual([])
  })
})
