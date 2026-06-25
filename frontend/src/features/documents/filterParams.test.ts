import { describe, expect, it } from 'vitest'
import { Ownership, SharingScope } from './documentsApi'
import { ALL_SHARING_SCOPES, DEFAULT_FILTERS, type DocumentFilters } from './documentsSlice'
import { OTHER_CONTENT_TYPE_EXCLUSIONS } from './filterOptions'
import { buildDocumentParams, parseDocumentParams } from './filterParams'

// Helper: round-trip a selection through the URL and back.
function roundTrip(filters: DocumentFilters, page: number) {
  const params = buildDocumentParams(filters, page)
  return parseDocumentParams(new URLSearchParams(params.toString()))
}

describe('buildDocumentParams', () => {
  it('produces an empty query string for the default selection on the first page', () => {
    expect(buildDocumentParams(DEFAULT_FILTERS, 0).toString()).toBe('')
  })

  it('omits the page param on the first page and writes it 1-based otherwise', () => {
    expect(buildDocumentParams(DEFAULT_FILTERS, 0).has('page')).toBe(false)
    expect(buildDocumentParams(DEFAULT_FILTERS, 1).get('page')).toBe('2')
    expect(buildDocumentParams(DEFAULT_FILTERS, 4).get('page')).toBe('5')
  })

  it('serializes a known file type as its bucket key', () => {
    const params = buildDocumentParams({ ...DEFAULT_FILTERS, contentType: 'application/pdf' }, 0)
    expect(params.get('type')).toBe('pdf')
  })

  it('serializes the "Other" bucket as type=other', () => {
    const params = buildDocumentParams(
      { ...DEFAULT_FILTERS, excludeContentTypes: OTHER_CONTENT_TYPE_EXCLUSIONS },
      0,
    )
    expect(params.get('type')).toBe('other')
  })

  it('omits the default sort and all-scopes selection', () => {
    const params = buildDocumentParams(DEFAULT_FILTERS, 0)
    expect(params.has('sort')).toBe(false)
    expect(params.has('scope')).toBe(false)
  })

  it('writes a non-default sort, scope subset, ownership, and dates', () => {
    const params = buildDocumentParams(
      {
        sort: 'size,asc',
        sharingScope: [SharingScope.PERSONAL, SharingScope.DEPARTMENT],
        ownership: Ownership.OWNED,
        dateFrom: '2024-01-01T00:00:00Z',
        dateTo: '2024-03-31T23:59:59Z',
        search: 'budget',
      },
      0,
    )
    expect(params.get('sort')).toBe('size,asc')
    expect(params.get('scope')).toBe('PERSONAL,DEPARTMENT')
    expect(params.get('ownership')).toBe('OWNED')
    expect(params.get('from')).toBe('2024-01-01')
    expect(params.get('to')).toBe('2024-03-31')
    expect(params.get('search')).toBe('budget')
  })

  it('writes an empty scope param for the show-nothing selection', () => {
    const params = buildDocumentParams({ ...DEFAULT_FILTERS, sharingScope: [] }, 0)
    expect(params.has('scope')).toBe(true)
    expect(params.get('scope')).toBe('')
  })
})

describe('parseDocumentParams', () => {
  it('returns the defaults for an empty query string', () => {
    const { filters, page } = parseDocumentParams(new URLSearchParams(''))
    expect(page).toBe(0)
    expect(filters.sort).toBe(DEFAULT_FILTERS.sort)
    expect(filters.sharingScope).toEqual(ALL_SHARING_SCOPES)
    expect(filters.search).toBeUndefined()
    expect(filters.ownership).toBeUndefined()
  })

  it('reads a 1-based page back as a 0-based index', () => {
    expect(parseDocumentParams(new URLSearchParams('page=2')).page).toBe(1)
    expect(parseDocumentParams(new URLSearchParams('page=5')).page).toBe(4)
  })

  it('falls back to the first page for invalid or non-positive page values', () => {
    expect(parseDocumentParams(new URLSearchParams('page=0')).page).toBe(0)
    expect(parseDocumentParams(new URLSearchParams('page=-3')).page).toBe(0)
    expect(parseDocumentParams(new URLSearchParams('page=abc')).page).toBe(0)
  })

  it('maps a known type bucket back to its content-type token', () => {
    const { filters } = parseDocumentParams(new URLSearchParams('type=pdf'))
    expect(filters.contentType).toBe('application/pdf')
    expect(filters.excludeContentTypes).toBeUndefined()
  })

  it('maps type=other back to the exclusion set', () => {
    const { filters } = parseDocumentParams(new URLSearchParams('type=other'))
    expect(filters.excludeContentTypes).toEqual(OTHER_CONTENT_TYPE_EXCLUSIONS)
    expect(filters.contentType).toBeUndefined()
  })

  it('ignores an unknown type token', () => {
    const { filters } = parseDocumentParams(new URLSearchParams('type=bogus'))
    expect(filters.contentType).toBeUndefined()
    expect(filters.excludeContentTypes).toBeUndefined()
  })

  it('reconstructs the inclusive day bounds for from/to', () => {
    const { filters } = parseDocumentParams(new URLSearchParams('from=2024-01-01&to=2024-03-31'))
    expect(filters.dateFrom).toBe('2024-01-01T00:00:00Z')
    expect(filters.dateTo).toBe('2024-03-31T23:59:59Z')
  })

  it('parses an empty scope param as the show-nothing selection', () => {
    const { filters } = parseDocumentParams(new URLSearchParams('scope='))
    expect(filters.sharingScope).toEqual([])
  })

  it('drops unknown scope and ownership tokens', () => {
    const { filters } = parseDocumentParams(
      new URLSearchParams('scope=PERSONAL,BOGUS&ownership=NOPE'),
    )
    expect(filters.sharingScope).toEqual([SharingScope.PERSONAL])
    expect(filters.ownership).toBeUndefined()
  })
})

describe('round-trip', () => {
  it('preserves a fully-specified selection', () => {
    const filters: DocumentFilters = {
      sort: 'size,desc',
      sharingScope: [SharingScope.DEPARTMENT, SharingScope.ORGANIZATION],
      ownership: Ownership.SHARED,
      contentType: 'application/pdf',
      dateFrom: '2024-02-10T00:00:00Z',
      dateTo: '2024-02-20T23:59:59Z',
      search: 'q4 report',
    }
    expect(roundTrip(filters, 3)).toEqual({ filters, page: 3 })
  })

  it('preserves the "Other" bucket selection', () => {
    const filters: DocumentFilters = {
      ...DEFAULT_FILTERS,
      excludeContentTypes: OTHER_CONTENT_TYPE_EXCLUSIONS,
    }
    expect(roundTrip(filters, 0)).toEqual({ filters, page: 0 })
  })
})
