import { describe, expect, it } from 'vitest'
import { OTHER_CONTENT_TYPE_EXCLUSIONS, SORT_OLDEST } from '@/features/documents/filterOptions'
import { DEFAULT_FILTERS, type SharedFilters } from './useSharedFiles'
import { buildSharedParams, parseSharedParams } from './sharedFilterParams'

// Helper: round-trip a selection through the URL and back.
function roundTrip(filters: SharedFilters, page: number) {
  const params = buildSharedParams(filters, page)
  return parseSharedParams(new URLSearchParams(params.toString()))
}

describe('buildSharedParams', () => {
  it('produces an empty query string for the default selection on the first page', () => {
    expect(buildSharedParams(DEFAULT_FILTERS, 0).toString()).toBe('')
  })

  it('omits the page param on the first page and writes it 1-based otherwise', () => {
    expect(buildSharedParams(DEFAULT_FILTERS, 0).has('page')).toBe(false)
    expect(buildSharedParams(DEFAULT_FILTERS, 1).get('page')).toBe('2')
    expect(buildSharedParams(DEFAULT_FILTERS, 4).get('page')).toBe('5')
  })

  it('writes the search query', () => {
    expect(buildSharedParams({ ...DEFAULT_FILTERS, search: 'report' }, 0).get('search')).toBe(
      'report',
    )
  })

  it('serializes a known file type as its bucket key', () => {
    const params = buildSharedParams({ ...DEFAULT_FILTERS, contentType: 'application/pdf' }, 0)
    expect(params.get('type')).toBe('pdf')
  })

  it('serializes the "Other" bucket as type=other', () => {
    const params = buildSharedParams(
      { ...DEFAULT_FILTERS, excludeContentTypes: OTHER_CONTENT_TYPE_EXCLUSIONS },
      0,
    )
    expect(params.get('type')).toBe('other')
  })

  it('omits the default sort and the default both-permissions selection', () => {
    const params = buildSharedParams(DEFAULT_FILTERS, 0)
    expect(params.has('sort')).toBe(false)
    expect(params.has('perm')).toBe(false)
  })

  it('writes a single permission bucket', () => {
    expect(buildSharedParams({ ...DEFAULT_FILTERS, permissions: ['edit'] }, 0).get('perm')).toBe(
      'edit',
    )
  })

  it('writes an empty perm value for the "show nothing" set', () => {
    const params = buildSharedParams({ ...DEFAULT_FILTERS, permissions: [] }, 0)
    expect(params.has('perm')).toBe(true)
    expect(params.get('perm')).toBe('')
  })

  it('writes the owner id', () => {
    expect(buildSharedParams({ ...DEFAULT_FILTERS, ownerId: 'owner-1' }, 0).get('owner')).toBe(
      'owner-1',
    )
  })

  it('writes the date bounds as YYYY-MM-DD', () => {
    const params = buildSharedParams(
      { ...DEFAULT_FILTERS, dateFrom: '2024-01-01T00:00:00Z', dateTo: '2024-02-01T23:59:59Z' },
      0,
    )
    expect(params.get('from')).toBe('2024-01-01')
    expect(params.get('to')).toBe('2024-02-01')
  })
})

describe('parseSharedParams', () => {
  it('returns the defaults for an empty query string', () => {
    const { filters, page } = parseSharedParams(new URLSearchParams(''))
    expect(page).toBe(0)
    expect(filters.sort).toBe(DEFAULT_FILTERS.sort)
    expect(filters.permissions).toEqual(['edit', 'view'])
    expect(filters.search).toBeUndefined()
    expect(filters.ownerId).toBeUndefined()
  })

  it('parses the 1-based page into a 0-based index', () => {
    expect(parseSharedParams(new URLSearchParams('page=3')).page).toBe(2)
  })

  it('falls back to page 0 for an invalid or first page', () => {
    expect(parseSharedParams(new URLSearchParams('page=1')).page).toBe(0)
    expect(parseSharedParams(new URLSearchParams('page=abc')).page).toBe(0)
  })

  it('reads an empty perm value as the "show nothing" set', () => {
    expect(parseSharedParams(new URLSearchParams('perm=')).filters.permissions).toEqual([])
  })

  it('trims and drops a blank search', () => {
    expect(parseSharedParams(new URLSearchParams('search=%20%20')).filters.search).toBeUndefined()
  })
})

describe('round-trip', () => {
  it('preserves a fully-populated selection', () => {
    const filters: SharedFilters = {
      sort: SORT_OLDEST,
      permissions: ['edit'],
      search: 'q3',
      ownerId: 'owner-7',
      contentType: 'application/pdf',
      dateFrom: '2024-01-01T00:00:00Z',
      dateTo: '2024-02-01T23:59:59Z',
    }
    const { filters: parsed, page } = roundTrip(filters, 2)
    expect(page).toBe(2)
    expect(parsed).toEqual(filters)
  })

  it('preserves the "Other" bucket and the empty-permission set', () => {
    const filters: SharedFilters = {
      sort: DEFAULT_FILTERS.sort,
      permissions: [],
      excludeContentTypes: OTHER_CONTENT_TYPE_EXCLUSIONS,
    }
    const { filters: parsed } = roundTrip(filters, 0)
    expect(parsed.permissions).toEqual([])
    expect(parsed.excludeContentTypes).toEqual(OTHER_CONTENT_TYPE_EXCLUSIONS)
    expect(parsed.contentType).toBeUndefined()
  })
})
