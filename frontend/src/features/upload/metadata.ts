import type { MetadataField } from './types'

// The tus `Upload-Metadata` header is a comma-separated list of `key value`
// pairs, so a metadata key may not contain spaces or commas. Strip them as the
// user types to keep every key transmittable.
export function sanitizeMetadataKey(key: string): string {
  return key.replace(/[\s,]/g, '')
}

/**
 * Collapses the editor's field list into the record sent to the backend,
 * dropping incomplete rows (empty key or value) and trimming whitespace. On a
 * duplicate key the last value wins.
 */
export function toMetadataRecord(
  fields: MetadataField[],
): Record<string, string> {
  const entries = fields
    .map((field) => [sanitizeMetadataKey(field.key), field.value.trim()] as const)
    .filter(([key, value]) => key.length > 0 && value.length > 0)
  return Object.fromEntries(entries)
}
