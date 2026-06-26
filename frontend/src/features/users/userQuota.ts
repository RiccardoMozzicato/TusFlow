// Storage quotas are entered/displayed in a chosen unit but stored as raw bytes
// by the backend, so all unit conversion lives here.
export const STORAGE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB'] as const

export type StorageUnit = (typeof STORAGE_UNITS)[number]

// Byte multiplier per unit (binary: 1 KB = 1024 B), matching formatBytes().
export const UNIT_BYTES: Record<StorageUnit, number> = {
  B: 1,
  KB: 1024,
  MB: 1024 ** 2,
  GB: 1024 ** 3,
  TB: 1024 ** 4,
}

// Unit the quota input defaults to when creating a user.
export const DEFAULT_STORAGE_UNIT: StorageUnit = 'GB'

// Back-compat alias for callers/tests that only deal in GB.
export const BYTES_PER_GB = UNIT_BYTES.GB

export interface ParsedQuota {
  /** False when the input is non-numeric or negative. */
  ok: boolean
  /** Byte quota; null means "unlimited" (blank input). Only meaningful when ok. */
  bytes: number | null
}

/**
 * Parses a quota figure in the given unit into bytes. A blank input is treated
 * as "unlimited" (null), matching the backend's nullable storageQuota.
 */
export function parseQuota(input: string, unit: StorageUnit): ParsedQuota {
  const trimmed = input.trim()
  if (trimmed === '') return { ok: true, bytes: null }

  const value = Number(trimmed)
  if (!Number.isFinite(value) || value < 0) return { ok: false, bytes: null }

  return { ok: true, bytes: Math.round(value * UNIT_BYTES[unit]) }
}

/**
 * Renders a byte quota as a plain number for an input field, expressed in the
 * given unit; null/undefined (unlimited) becomes an empty string. Trailing
 * zeros are trimmed.
 */
export function quotaToInput(bytes: number | null | undefined, unit: StorageUnit): string {
  if (bytes == null) return ''
  return String(Number((bytes / UNIT_BYTES[unit]).toFixed(2)))
}

/**
 * Picks the largest unit that keeps the value at or above 1, so an existing
 * quota opens in a sensible unit (e.g. 5 GB shows as "5 GB", not "5120 MB").
 */
export function bestUnitFor(bytes: number | null | undefined): StorageUnit {
  if (bytes == null || bytes <= 0) return DEFAULT_STORAGE_UNIT
  for (let i = STORAGE_UNITS.length - 1; i >= 0; i -= 1) {
    const unit = STORAGE_UNITS[i]
    if (bytes >= UNIT_BYTES[unit]) return unit
  }
  return 'B'
}

/** Human-readable quota for display; null/undefined renders as "Unlimited". */
export function formatQuota(bytes: number | null | undefined): string {
  if (bytes == null) return 'Unlimited'
  if (bytes === 0) return '0 B'
  const unit = bestUnitFor(bytes)
  return `${Number((bytes / UNIT_BYTES[unit]).toFixed(1))} ${unit}`
}
