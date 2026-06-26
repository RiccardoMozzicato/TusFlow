import { describe, expect, it } from 'vitest'
import {
  buildUserParams,
  DEFAULT_USER_SORT,
  parseUserParams,
  type UserFilters,
} from './userFilterParams'

// Helper: round-trip a selection through the URL and back.
function roundTrip(filters: UserFilters, page: number) {
  const params = buildUserParams(filters, page)
  return parseUserParams(new URLSearchParams(params.toString()))
}

describe('buildUserParams', () => {
  it('produces an empty query string for the default selection on the first page', () => {
    expect(buildUserParams({ sort: DEFAULT_USER_SORT }, 0).toString()).toBe('')
  })

  it('omits the page param on the first page and writes it 1-based otherwise', () => {
    expect(buildUserParams({ sort: DEFAULT_USER_SORT }, 0).has('page')).toBe(false)
    expect(buildUserParams({ sort: DEFAULT_USER_SORT }, 1).get('page')).toBe('2')
    expect(buildUserParams({ sort: DEFAULT_USER_SORT }, 4).get('page')).toBe('5')
  })

  it('omits the default sort but writes a non-default one', () => {
    expect(buildUserParams({ sort: DEFAULT_USER_SORT }, 0).has('sort')).toBe(false)
    expect(buildUserParams({ sort: 'email,desc' }, 0).get('sort')).toBe('email,desc')
  })

  it('writes the search term when present', () => {
    expect(buildUserParams({ sort: DEFAULT_USER_SORT, search: 'rossi' }, 0).get('search')).toBe('rossi')
  })

  it('omits a blank/undefined search', () => {
    expect(buildUserParams({ sort: DEFAULT_USER_SORT, search: undefined }, 0).has('search')).toBe(false)
  })
})

describe('parseUserParams', () => {
  it('returns the defaults for an empty query string', () => {
    const { filters, page } = parseUserParams(new URLSearchParams(''))
    expect(page).toBe(0)
    expect(filters.sort).toBe(DEFAULT_USER_SORT)
    expect(filters.search).toBeUndefined()
  })

  it('reads a 1-based page back as a 0-based index', () => {
    expect(parseUserParams(new URLSearchParams('page=2')).page).toBe(1)
    expect(parseUserParams(new URLSearchParams('page=5')).page).toBe(4)
  })

  it('falls back to the first page for invalid or non-positive page values', () => {
    expect(parseUserParams(new URLSearchParams('page=0')).page).toBe(0)
    expect(parseUserParams(new URLSearchParams('page=-3')).page).toBe(0)
    expect(parseUserParams(new URLSearchParams('page=abc')).page).toBe(0)
  })

  it('trims the search term and treats a blank one as no filter', () => {
    expect(parseUserParams(new URLSearchParams('search=%20%20')).filters.search).toBeUndefined()
    expect(parseUserParams(new URLSearchParams('search=ada')).filters.search).toBe('ada')
  })

  it('reads a non-default sort spec', () => {
    expect(parseUserParams(new URLSearchParams('sort=role,desc')).filters.sort).toBe('role,desc')
  })
})

describe('round-trip', () => {
  it('preserves a fully-specified selection', () => {
    const filters: UserFilters = { search: 'mary smith', sort: 'storageUsed,desc' }
    expect(roundTrip(filters, 3)).toEqual({ filters, page: 3 })
  })

  it('preserves the default selection', () => {
    const filters: UserFilters = { sort: DEFAULT_USER_SORT }
    expect(roundTrip(filters, 0)).toEqual({ filters, page: 0 })
  })
})
