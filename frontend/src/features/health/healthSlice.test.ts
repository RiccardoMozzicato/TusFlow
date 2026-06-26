import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import healthReducer, {
  setOffline,
  setOnline,
  selectConnectivity,
  selectIsOffline,
} from './healthSlice'
import type { RootState } from '@/app/store'

describe('healthSlice', () => {
  beforeEach(() => {
    // Freeze the clock so lastCheckAt is assertable.
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-06-25T12:00:00Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  test('setOnline marks connectivity online and stamps lastCheckAt', () => {
    const state = { connectivity: 'offline' as const, lastCheckAt: null }

    const next = healthReducer(state, setOnline())

    expect(next.connectivity).toBe('online')
    expect(next.lastCheckAt).toBe(Date.parse('2026-06-25T12:00:00Z'))
  })

  test('setOffline marks connectivity offline and stamps lastCheckAt', () => {
    const state = { connectivity: 'online' as const, lastCheckAt: null }

    const next = healthReducer(state, setOffline())

    expect(next.connectivity).toBe('offline')
    expect(next.lastCheckAt).toBe(Date.parse('2026-06-25T12:00:00Z'))
  })

  test('selectors read connectivity off the root state', () => {
    const root = { health: { connectivity: 'offline', lastCheckAt: 1 } } as RootState

    expect(selectConnectivity(root)).toBe('offline')
    expect(selectIsOffline(root)).toBe(true)
  })
})
