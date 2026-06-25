import { Ownership, SharingScope } from './documentsApi'
import {
  ALL_SHARING_SCOPES,
  DEFAULT_FILTERS,
  type DocumentFilters,
} from './documentsSlice'
import {
  isoToDateInput,
  OTHER_CONTENT_TYPE_EXCLUSIONS,
  SELECTABLE_FILE_TYPES,
} from './filterOptions'
import { FILE_TYPE_CONTENT_TYPE } from './mappers'

// Bidirectional mapping between the document-library query (filters + page) and
// the URL's query string, so the current selection is bookmarkable, shareable,
// and survives a reload or browser Back/Forward.
//
// Param names (all omitted when at their default, to keep clean URLs):
//   page   — 1-based page number; omitted on the first page
//   search — free-text query
//   sort   — sort spec (field,direction); omitted when the default newest-first
//   type   — file-type bucket key (pdf/docx/txt) or the literal `other`
//   from   — created-date lower bound as YYYY-MM-DD
//   to     — created-date upper bound as YYYY-MM-DD
//   scope  — comma-joined sharing scopes; omitted when all three (the default)
//   ownership — OWNED | SHARED

// Sentinel `type` value for the "Other" bucket (the complement of the known
// buckets), which the filter expresses as a set of excluded content-type tokens.
const OTHER_TYPE = 'other'

function isSharingScope(value: string): value is SharingScope {
  return (Object.values(SharingScope) as string[]).includes(value)
}

function isOwnership(value: string | null): value is Ownership {
  return value !== null && (Object.values(Ownership) as string[]).includes(value)
}

// File-type slice (contentType / excludeContentTypes) → single `type` token.
function serializeType(filters: DocumentFilters): string | null {
  if (filters.excludeContentTypes?.length) return OTHER_TYPE
  if (!filters.contentType) return null
  const option = SELECTABLE_FILE_TYPES.find(
    (opt) => FILE_TYPE_CONTENT_TYPE[opt.type] === filters.contentType,
  )
  return option?.type ?? null
}

// `type` token → the file-type slice of the filters. Both keys are always
// present (undefined when inactive) so the parsed filters are a complete
// snapshot: merging them into the slice clears whichever the URL dropped.
function parseType(raw: string | null): Pick<DocumentFilters, 'contentType' | 'excludeContentTypes'> {
  if (raw === OTHER_TYPE) {
    return { contentType: undefined, excludeContentTypes: OTHER_CONTENT_TYPE_EXCLUSIONS }
  }
  const option = raw ? SELECTABLE_FILE_TYPES.find((opt) => opt.type === raw) : undefined
  const token = option ? FILE_TYPE_CONTENT_TYPE[option.type] : undefined
  return { contentType: token, excludeContentTypes: undefined }
}

function isDefaultScopes(scopes: SharingScope[]): boolean {
  return (
    scopes.length === ALL_SHARING_SCOPES.length &&
    ALL_SHARING_SCOPES.every((scope) => scopes.includes(scope))
  )
}

// Parses the 1-based `page` param into a 0-based index. Missing/invalid → 0.
function parsePage(raw: string | null): number {
  if (!raw) return 0
  const parsed = Number.parseInt(raw, 10)
  return Number.isInteger(parsed) && parsed > 1 ? parsed - 1 : 0
}

/** Reads the current document-library selection out of a URL's query string. */
export function parseDocumentParams(params: URLSearchParams): {
  filters: DocumentFilters
  page: number
} {
  const from = params.get('from')
  const to = params.get('to')
  const filters: DocumentFilters = {
    ...parseType(params.get('type')),
    sort: params.get('sort') || DEFAULT_FILTERS.sort,
    search: params.get('search')?.trim() || undefined,
    sharingScope: params.has('scope')
      ? (params.get('scope') ?? '').split(',').filter(isSharingScope)
      : [...ALL_SHARING_SCOPES],
    ownership: isOwnership(params.get('ownership')) ? (params.get('ownership') as Ownership) : undefined,
    dateFrom: from ? `${from}T00:00:00Z` : undefined,
    dateTo: to ? `${to}T23:59:59Z` : undefined,
  }
  return { filters, page: parsePage(params.get('page')) }
}

/**
 * Serializes a document-library selection into URL query params, omitting every
 * field that is at its default so the URL stays minimal. Fields are written in a
 * fixed order so the resulting string is stable for the same selection.
 */
export function buildDocumentParams(filters: DocumentFilters, page: number): URLSearchParams {
  const params = new URLSearchParams()

  if (filters.search) params.set('search', filters.search)
  if (filters.sort && filters.sort !== DEFAULT_FILTERS.sort) params.set('sort', filters.sort)

  const type = serializeType(filters)
  if (type) params.set('type', type)

  const from = isoToDateInput(filters.dateFrom)
  if (from) params.set('from', from)
  const to = isoToDateInput(filters.dateTo)
  if (to) params.set('to', to)

  // An empty scope selection ("show nothing") is meaningful, so it is written as
  // an empty value rather than omitted; only the default all-scopes set is dropped.
  if (!isDefaultScopes(filters.sharingScope)) params.set('scope', filters.sharingScope.join(','))

  if (filters.ownership) params.set('ownership', filters.ownership)
  if (page > 0) params.set('page', String(page + 1))

  return params
}
