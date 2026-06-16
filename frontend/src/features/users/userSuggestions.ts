import type { UserDTO } from './usersApi'

// Default cap on how many suggestions to surface in the dropdown.
export const MAX_SUGGESTIONS = 6

// Matches a canonical UUID, so a pasted id can be shared directly even when the
// user isn't in the fetched suggestion page.
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function isUuid(value: string): boolean {
  return UUID_RE.test(value.trim())
}

/**
 * Filters org users for the share autocomplete: drops anyone already excluded
 * (already shared / the owner), then ranks by a case-insensitive match on name
 * or email. An empty query returns the first users as-is (a browse list).
 */
export function filterUserSuggestions(
  users: UserDTO[],
  query: string,
  excludeIds: ReadonlySet<string>,
  limit = MAX_SUGGESTIONS,
): UserDTO[] {
  const candidates = users.filter((user) => !excludeIds.has(user.id))
  const normalized = query.trim().toLowerCase()

  if (!normalized) return candidates.slice(0, limit)

  return candidates
    .filter(
      (user) =>
        user.fullName.toLowerCase().includes(normalized) ||
        user.email.toLowerCase().includes(normalized),
    )
    .slice(0, limit)
}
