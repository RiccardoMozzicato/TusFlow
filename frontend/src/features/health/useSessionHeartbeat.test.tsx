import type { ReactNode } from 'react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import authReducer from '@/features/auth/authSlice'
import healthReducer from '@/features/health/healthSlice'
import * as authApi from '@/features/auth/authApi'
import type { UserInfo } from '@/features/auth/types'
import { HEARTBEAT_INTERVAL_MS, useSessionHeartbeat } from './useSessionHeartbeat'

// Only fetchUserInfo is replaced; the hook owns the 200/401/throw branching.
vi.mock('@/features/auth/authApi', () => ({
  fetchUserInfo: vi.fn(),
}))

const fetchUserInfoMock = vi.mocked(authApi.fetchUserInfo)

const USER: UserInfo = {
  uuid: 'u-1',
  username: 'ada',
  email: 'ada@example.io',
  fullName: 'Ada Lovelace',
  department: 'R&D',
  organization: 'Acme',
  roles: ['staff'],
}

// Authenticated store so the heartbeat effect is active; connectivity is seeded so a
// probe outcome produces an observable flip.
function makeStore(connectivity: 'online' | 'offline') {
  return configureStore({
    reducer: { auth: authReducer, health: healthReducer },
    preloadedState: {
      auth: { status: 'authenticated' as const, user: USER, error: null },
      health: { connectivity, lastCheckAt: null },
    },
  })
}

type TestStore = ReturnType<typeof makeStore>

function renderHeartbeat(store: TestStore) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <Provider store={store}>{children}</Provider>
  )
  return renderHook(() => useSessionHeartbeat(), { wrapper })
}

let hidden = false

describe('useSessionHeartbeat', () => {
  beforeEach(() => {
    hidden = false
    // document.hidden is read-only; override its getter so tests can toggle visibility.
    Object.defineProperty(document, 'hidden', { configurable: true, get: () => hidden })
    // shouldAdvanceTime lets the awaited fetchUserInfo promise resolve while we still
    // control the interval timer.
    vi.useFakeTimers({ shouldAdvanceTime: true })
    fetchUserInfoMock.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('refreshes the session and marks online on a 200 probe', async () => {
    fetchUserInfoMock.mockResolvedValue(USER)
    const store = makeStore('offline')
    renderHeartbeat(store)

    await act(async () => {
      vi.advanceTimersByTime(HEARTBEAT_INTERVAL_MS)
    })

    await waitFor(() => expect(store.getState().health.connectivity).toBe('online'))
    expect(fetchUserInfoMock).toHaveBeenCalledTimes(1)
    expect(store.getState().auth.status).toBe('authenticated')
    expect(store.getState().auth.user).toEqual(USER)
  })

  it('marks online and expires the session on a 401 probe', async () => {
    fetchUserInfoMock.mockResolvedValue(null)
    const store = makeStore('online')
    renderHeartbeat(store)

    await act(async () => {
      vi.advanceTimersByTime(HEARTBEAT_INTERVAL_MS)
    })

    await waitFor(() => expect(store.getState().auth.status).toBe('anonymous'))
    expect(store.getState().auth.user).toBeNull()
    // The gateway answered (401), so connectivity is online, not offline.
    expect(store.getState().health.connectivity).toBe('online')
  })

  it('marks offline without touching auth on a network error', async () => {
    fetchUserInfoMock.mockRejectedValue(new Error('network down'))
    const store = makeStore('online')
    renderHeartbeat(store)

    await act(async () => {
      vi.advanceTimersByTime(HEARTBEAT_INTERVAL_MS)
    })

    await waitFor(() => expect(store.getState().health.connectivity).toBe('offline'))
    // A transient blip must not log the user out.
    expect(store.getState().auth.status).toBe('authenticated')
    expect(store.getState().auth.user).toEqual(USER)
  })

  it('does not probe while the tab is hidden, then probes on visibility regain', async () => {
    fetchUserInfoMock.mockResolvedValue(USER)
    hidden = true
    const store = makeStore('online')
    renderHeartbeat(store)

    await act(async () => {
      vi.advanceTimersByTime(HEARTBEAT_INTERVAL_MS * 2)
    })
    expect(fetchUserInfoMock).not.toHaveBeenCalled()

    hidden = false
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'))
    })

    await waitFor(() => expect(fetchUserInfoMock).toHaveBeenCalledTimes(1))
  })

  it('stops probing after unmount', async () => {
    fetchUserInfoMock.mockResolvedValue(USER)
    const store = makeStore('online')
    const { unmount } = renderHeartbeat(store)

    unmount()
    await act(async () => {
      vi.advanceTimersByTime(HEARTBEAT_INTERVAL_MS * 2)
    })

    expect(fetchUserInfoMock).not.toHaveBeenCalled()
  })
})
