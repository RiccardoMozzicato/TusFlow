import { describe, expect, it } from 'vitest'
import {
  bestUnitFor,
  formatQuota,
  parseQuota,
  quotaToInput,
  UNIT_BYTES,
} from './userQuota'

describe('parseQuota', () => {
  it('converts a figure in the chosen unit to bytes', () => {
    expect(parseQuota('5', 'GB')).toEqual({ ok: true, bytes: 5 * UNIT_BYTES.GB })
    expect(parseQuota('2', 'TB')).toEqual({ ok: true, bytes: 2 * UNIT_BYTES.TB })
    expect(parseQuota('512', 'MB')).toEqual({ ok: true, bytes: 512 * UNIT_BYTES.MB })
  })

  it('treats blank input as unlimited (null) regardless of unit', () => {
    expect(parseQuota('   ', 'TB')).toEqual({ ok: true, bytes: null })
  })

  it('rounds fractional values to whole bytes', () => {
    expect(parseQuota('1.5', 'GB').bytes).toBe(Math.round(1.5 * UNIT_BYTES.GB))
  })

  it('rejects non-numeric and negative input', () => {
    expect(parseQuota('abc', 'GB').ok).toBe(false)
    expect(parseQuota('-2', 'GB').ok).toBe(false)
  })
})

describe('quotaToInput', () => {
  it('renders bytes as a number string in the chosen unit', () => {
    expect(quotaToInput(5 * UNIT_BYTES.GB, 'GB')).toBe('5')
    expect(quotaToInput(2 * UNIT_BYTES.TB, 'TB')).toBe('2')
    expect(quotaToInput(UNIT_BYTES.GB, 'MB')).toBe('1024')
  })

  it('renders unlimited (null/undefined) as an empty string', () => {
    expect(quotaToInput(null, 'GB')).toBe('')
    expect(quotaToInput(undefined, 'TB')).toBe('')
  })

  it('trims trailing zeros from fractional values', () => {
    expect(quotaToInput(1.5 * UNIT_BYTES.GB, 'GB')).toBe('1.5')
  })
})

describe('bestUnitFor', () => {
  it('picks the largest unit keeping the value >= 1', () => {
    expect(bestUnitFor(5 * UNIT_BYTES.GB)).toBe('GB')
    expect(bestUnitFor(3 * UNIT_BYTES.TB)).toBe('TB')
    expect(bestUnitFor(512 * UNIT_BYTES.MB)).toBe('MB')
    expect(bestUnitFor(900)).toBe('B')
  })

  it('defaults to GB for unlimited or zero', () => {
    expect(bestUnitFor(null)).toBe('GB')
    expect(bestUnitFor(0)).toBe('GB')
  })
})

describe('formatQuota', () => {
  it('formats a byte quota in its most natural unit', () => {
    expect(formatQuota(2 * UNIT_BYTES.GB)).toBe('2 GB')
    expect(formatQuota(1.5 * UNIT_BYTES.TB)).toBe('1.5 TB')
  })

  it('labels an unset quota as Unlimited', () => {
    expect(formatQuota(null)).toBe('Unlimited')
    expect(formatQuota(undefined)).toBe('Unlimited')
  })
})
