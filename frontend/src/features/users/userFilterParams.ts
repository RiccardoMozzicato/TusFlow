// Bidirectional mapping between the user-management query (search + sort + page)
// and the URL's query string, so the current selection is bookmarkable,
// shareable, and survives a reload or browser Back/Forward. Mirrors the
// document-library serializer (features/documents/filterParams.ts).
//
// Param names (omitted when at their default, to keep clean URLs):
//   search — free-text query (matched server-side against name + email)
//   sort   — sort spec (field,direction); omitted when the default name-ascending
//   page   — 1-based page number; omitted on the first page

// Default sort, matching the UserDetailService default (fullName ascending).
export const DEFAULT_USER_SORT = 'fullName,asc'

export interface UserFilters {
  /** Free-text query; undefined/blank means no filter. */
  search?: string
  /** Sort spec, e.g. 'email,asc'. */
  sort: string
}

// Parses the 1-based `page` param into a 0-based index. Missing/invalid → 0.
function parsePage(raw: string | null): number {
  if (!raw) return 0
  const parsed = Number.parseInt(raw, 10)
  return Number.isInteger(parsed) && parsed > 1 ? parsed - 1 : 0
}

/** Reads the current user-management selection out of a URL's query string. */
export function parseUserParams(params: URLSearchParams): {
  filters: UserFilters
  page: number
} {
  const filters: UserFilters = {
    search: params.get('search')?.trim() || undefined,
    sort: params.get('sort') || DEFAULT_USER_SORT,
  }
  return { filters, page: parsePage(params.get('page')) }
}

/**
 * Serializes a user-management selection into URL query params, omitting every
 * field that is at its default so the URL stays minimal. Fields are written in a
 * fixed order so the resulting string is stable for the same selection.
 */
export function buildUserParams(filters: UserFilters, page: number): URLSearchParams {
  const params = new URLSearchParams()

  if (filters.search) params.set('search', filters.search)
  if (filters.sort && filters.sort !== DEFAULT_USER_SORT) params.set('sort', filters.sort)
  if (page > 0) params.set('page', String(page + 1))

  return params
}
