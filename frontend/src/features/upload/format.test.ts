import { describe, expect, it } from 'vitest'
import { formatBytes, formatEta, formatSpeed } from './format'

describe('formatBytes', () => {
  it('formats across unit boundaries', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(2048)).toBe('2.0 KB')
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB')
    expect(formatBytes(3 * 1024 * 1024 * 1024)).toBe('3.0 GB')
  })

  it('guards against invalid input', () => {
    expect(formatBytes(-1)).toBe('0 B')
    expect(formatBytes(Number.NaN)).toBe('0 B')
  })
})

describe('formatSpeed', () => {
  it('formats MB/s and KB/s', () => {
    expect(formatSpeed(4.2 * 1024 * 1024)).toBe('4.2 MB/s')
    expect(formatSpeed(50 * 1024)).toBe('50 KB/s')
  })

  it('returns a dash for non-positive rates', () => {
    expect(formatSpeed(0)).toBe('—')
    expect(formatSpeed(Number.POSITIVE_INFINITY)).toBe('—')
  })
})

describe('formatEta', () => {
  it('formats seconds and minutes', () => {
    expect(formatEta(12)).toBe('12s left')
    expect(formatEta(150)).toBe('3m left')
  })

  it('returns blank when unknown', () => {
    expect(formatEta(Number.POSITIVE_INFINITY)).toBe('')
    expect(formatEta(0)).toBe('')
  })
})
