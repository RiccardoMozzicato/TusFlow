import { describe, expect, it } from 'vitest'
import { storageSummary } from './storageSummary'

const GB = 1024 * 1024 * 1024

describe('storageSummary', () => {
  it('summarizes used vs quota with remaining space', () => {
    const summary = storageSummary(GB, 5 * GB)
    expect(summary).toEqual({
      usedLabel: '1.0 GB',
      quotaLabel: '5.0 GB',
      freeLabel: '4.0 GB',
      fraction: 0.2,
      isNearLimit: false,
    })
  })

  it('flags near-limit usage at 90% or above', () => {
    expect(storageSummary(4.5 * GB, 5 * GB).isNearLimit).toBe(true)
    expect(storageSummary(4.4 * GB, 5 * GB).isNearLimit).toBe(false)
  })

  it('clamps the fraction at 1 when usage exceeds the quota', () => {
    const summary = storageSummary(6 * GB, 5 * GB)
    expect(summary.fraction).toBe(1)
    expect(summary.freeLabel).toBe('0 B')
    expect(summary.isNearLimit).toBe(true)
  })

  it('handles an unset quota as unlimited', () => {
    const summary = storageSummary(GB, null)
    expect(summary).toEqual({
      usedLabel: '1.0 GB',
      quotaLabel: null,
      freeLabel: null,
      fraction: 0,
      isNearLimit: false,
    })
  })
})
