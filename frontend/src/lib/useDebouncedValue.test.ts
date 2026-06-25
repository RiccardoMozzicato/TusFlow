import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useDebouncedValue } from './useDebouncedValue'

describe('useDebouncedValue', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('returns the initial value immediately', () => {
    const { result } = renderHook(() => useDebouncedValue('a', 300))
    expect(result.current).toBe('a')
  })

  it('updates only after the delay elapses', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: 'a' },
    })

    rerender({ value: 'ab' })
    // Still the old value before the delay.
    expect(result.current).toBe('a')

    act(() => vi.advanceTimersByTime(300))
    expect(result.current).toBe('ab')
  })

  it('collapses a burst of changes into a single trailing update', () => {
    const { result, rerender } = renderHook(({ value }) => useDebouncedValue(value, 300), {
      initialProps: { value: '' },
    })

    // Type quickly: each change happens within the debounce window.
    rerender({ value: 'b' })
    act(() => vi.advanceTimersByTime(100))
    rerender({ value: 'bu' })
    act(() => vi.advanceTimersByTime(100))
    rerender({ value: 'bud' })
    act(() => vi.advanceTimersByTime(100))

    // No intermediate value committed yet (the timer kept resetting).
    expect(result.current).toBe('')

    act(() => vi.advanceTimersByTime(300))
    expect(result.current).toBe('bud')
  })
})
