import type { DepartmentDTO } from './usersApi'

// Default cap on how many suggestions to surface. Kept high so the full org list
// is reachable — the dropdown is a fixed-height scroll container, so the user
// scrolls rather than being silently truncated to a handful of matches.
export const MAX_DEPARTMENT_SUGGESTIONS = 50

/**
 * Filters the org's departments for the autocomplete: an empty query returns
 * the first departments as a browse list; otherwise ranks by a case-insensitive
 * substring match on the name. When the query exactly matches a single existing
 * department (case-insensitively), no suggestion is returned — the user has
 * already typed it in full, so a lone redundant row would just be noise.
 */
export function filterDepartmentSuggestions(
  departments: DepartmentDTO[],
  query: string,
  limit = MAX_DEPARTMENT_SUGGESTIONS,
): DepartmentDTO[] {
  const normalized = query.trim().toLowerCase()

  if (!normalized) return departments.slice(0, limit)

  const matches = departments.filter((dept) => dept.name.toLowerCase().includes(normalized))

  if (matches.length === 1 && matches[0].name.toLowerCase() === normalized) return []

  return matches.slice(0, limit)
}
