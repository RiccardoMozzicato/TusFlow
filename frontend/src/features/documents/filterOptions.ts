import { FILE_TYPE_OPTIONS } from './mockData'
import { FILE_TYPE_CONTENT_TYPE } from './mappers'

// Sort specs accepted by the backend `sort` param (field,direction).
export const SORT_NEWEST = 'createdAt,desc'
export const SORT_OLDEST = 'createdAt,asc'

// Only file types that map to a single exact MIME the backend can match on are
// offered as filters (the `contentType` param is a case-insensitive contains).
export const SELECTABLE_FILE_TYPES = FILE_TYPE_OPTIONS.filter(
  (option) => option.type in FILE_TYPE_CONTENT_TYPE,
)

// Tokens the "Other" file-type bucket excludes: the content-type tokens of every
// known bucket (PDF/DOCX/TXT). Selecting "Other" sends these as `excludeContentTypes`
// so the backend keeps only files matching none of them — the buckets' complement.
export const OTHER_CONTENT_TYPE_EXCLUSIONS: string[] = Object.values(
  FILE_TYPE_CONTENT_TYPE,
).filter((token): token is string => Boolean(token))

// Converts an ISO-8601 timestamp to the `YYYY-MM-DD` value an <input type=date>
// expects. Empty/invalid input yields '' so the bound filter reads as cleared.
export function isoToDateInput(iso?: string): string {
  if (!iso) return ''
  const parsed = new Date(iso)
  return Number.isNaN(parsed.getTime()) ? '' : iso.slice(0, 10)
}
