import { formatBytes } from '@/features/upload/format'

// Fraction of the quota at which the meter switches to the warning treatment.
const NEAR_LIMIT_FRACTION = 0.9

export interface StorageSummary {
  usedLabel: string
  /** Null when the user has no quota (unlimited). */
  quotaLabel: string | null
  /** Remaining space; null when the quota is unlimited. */
  freeLabel: string | null
  /** Used fraction of the quota (0–1, clamped); 0 when unlimited. */
  fraction: number
  isNearLimit: boolean
}

/** Projects raw used/quota byte counts onto the storage meter's view model. */
export function storageSummary(usedBytes: number, quotaBytes: number | null): StorageSummary {
  const usedLabel = formatBytes(usedBytes)

  if (quotaBytes === null || quotaBytes <= 0) {
    return { usedLabel, quotaLabel: null, freeLabel: null, fraction: 0, isNearLimit: false }
  }

  const fraction = Math.min(usedBytes / quotaBytes, 1)
  return {
    usedLabel,
    quotaLabel: formatBytes(quotaBytes),
    freeLabel: formatBytes(Math.max(quotaBytes - usedBytes, 0)),
    fraction,
    isNearLimit: fraction >= NEAR_LIMIT_FRACTION,
  }
}
