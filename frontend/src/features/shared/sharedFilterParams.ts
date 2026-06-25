import {
  isoToDateInput,
  OTHER_CONTENT_TYPE_EXCLUSIONS,
  SELECTABLE_FILE_TYPES,
} from '@/features/documents/filterOptions'
import { FILE_TYPE_CONTENT_TYPE } from '@/features/documents/mappers'
import { DEFAULT_FILTERS, type SharedFilters } from './useSharedFiles'
import type { SharePermission } from './types'

// Bidirectional mapping between the shared-with-me query (filters + page) and the
// URL's query string, so the current selection is bookmarkable, shareable, and
// survives a reload or browser Back/Forward — mirroring the Document Library's
// filterParams.
//
// Param names (all omitted when at their default, to keep clean URLs):
//   page   — 1-based page number; omitted on the first page
//   search — free-text query
//   sort   — sort spec (field,direction); omitted when the default newest-first
//   type   — file-type bucket key (pdf/docx/txt) or the literal `other`
//   from   — created-date lower bound as YYYY-MM-DD
//   to     — created-date upper bound as YYYY-MM-DD
//   perm   — comma-joined permission buckets; omitted when both (the default)
//   owner  — selected owner's user id

// Sentinel `type` value for the "Other" bucket (the complement of the known
// buckets), expressed as a set of excluded content-type tokens.
const OTHER_TYPE = 'other'
const ALL_PERMISSIONS: SharePermission[] = ['edit', 'view']

function isSharePermission(value: string): value is SharePermission {
  return value === 'edit' || value === 'view'
}

// File-type slice (contentType / excludeContentTypes) → single `type` token.
function serializeType(filters: SharedFilters): string | null {
  if (filters.excludeContentTypes?.length) return OTHER_TYPE
  if (!filters.contentType) return null
  const option = SELECTABLE_FILE_TYPES.find(
    (opt) => FILE_TYPE_CONTENT_TYPE[opt.type] === filters.contentType,
  )
  return option?.type ?? null
}

// `type` token → the file-type slice of the filters. Both keys are always present
// (undefined when inactive) so the parsed filters are a complete snapshot.
function parseType(raw: string | null): Pick<SharedFilters, 'contentType' | 'excludeContentTypes'> {
  if (raw === OTHER_TYPE) {
    return { contentType: undefined, excludeContentTypes: OTHER_CONTENT_TYPE_EXCLUSIONS }
  }
  const option = raw ? SELECTABLE_FILE_TYPES.find((opt) => opt.type === raw) : undefined
  const token = option ? FILE_TYPE_CONTENT_TYPE[option.type] : undefined
  return { contentType: token, excludeContentTypes: undefined }
}

function isDefaultPermissions(permissions: SharePermission[]): boolean {
  return (
    permissions.length === ALL_PERMISSIONS.length &&
    ALL_PERMISSIONS.every((permission) => permissions.includes(permission))
  )
}

// Parses the 1-based `page` param into a 0-based index. Missing/invalid → 0.
function parsePage(raw: string | null): number {
  if (!raw) return 0
  const parsed = Number.parseInt(raw, 10)
  return Number.isInteger(parsed) && parsed > 1 ? parsed - 1 : 0
}

/** Reads the current shared-with-me selection out of a URL's query string. */
export function parseSharedParams(params: URLSearchParams): {
  filters: SharedFilters
  page: number
} {
  const from = params.get('from')
  const to = params.get('to')
  const filters: SharedFilters = {
    ...parseType(params.get('type')),
    sort: params.get('sort') || DEFAULT_FILTERS.sort,
    search: params.get('search')?.trim() || undefined,
    // An absent `perm` means the default (both buckets); a present one — even
    // empty — is taken literally, so an empty value reads as "show nothing".
    permissions: params.has('perm')
      ? (params.get('perm') ?? '').split(',').filter(isSharePermission)
      : [...ALL_PERMISSIONS],
    ownerId: params.get('owner') || undefined,
    dateFrom: from ? `${from}T00:00:00Z` : undefined,
    dateTo: to ? `${to}T23:59:59Z` : undefined,
  }
  return { filters, page: parsePage(params.get('page')) }
}

/**
 * Serializes a shared-with-me selection into URL query params, omitting every
 * field at its default so the URL stays minimal. Fields are written in a fixed
 * order so the resulting string is stable for the same selection.
 */
export function buildSharedParams(filters: SharedFilters, page: number): URLSearchParams {
  const params = new URLSearchParams()

  if (filters.search) params.set('search', filters.search)
  if (filters.sort && filters.sort !== DEFAULT_FILTERS.sort) params.set('sort', filters.sort)

  const type = serializeType(filters)
  if (type) params.set('type', type)

  const from = isoToDateInput(filters.dateFrom)
  if (from) params.set('from', from)
  const to = isoToDateInput(filters.dateTo)
  if (to) params.set('to', to)

  // An empty permission set ("show nothing") is meaningful, so it is written as an
  // empty value rather than omitted; only the default both-buckets set is dropped.
  if (!isDefaultPermissions(filters.permissions)) params.set('perm', filters.permissions.join(','))

  if (filters.ownerId) params.set('owner', filters.ownerId)
  if (page > 0) params.set('page', String(page + 1))

  return params
}
