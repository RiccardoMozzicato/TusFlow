import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useDepartments } from './useDepartments'
import * as usersApi from './usersApi'

const getDepartments = vi.spyOn(usersApi, 'getDepartments')

describe('useDepartments', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('does not fetch until load() is called', () => {
    renderHook(() => useDepartments())
    expect(getDepartments).not.toHaveBeenCalled()
  })

  it('loads the departments once across repeated load() calls', async () => {
    getDepartments.mockResolvedValue([{ name: 'IT', userCount: 2 }])
    const { result } = renderHook(() => useDepartments())

    act(() => {
      result.current.load()
      result.current.load()
    })

    await waitFor(() => expect(result.current.departments).toEqual([{ name: 'IT', userCount: 2 }]))
    expect(getDepartments).toHaveBeenCalledTimes(1)
    expect(result.current.error).toBeNull()
  })

  it('exposes an error and allows a retry on failure', async () => {
    getDepartments.mockRejectedValueOnce(new Error('boom'))
    const { result } = renderHook(() => useDepartments())

    act(() => {
      result.current.load()
    })
    await waitFor(() => expect(result.current.error).toBe('boom'))

    // A second load() retries because the first attempt failed.
    getDepartments.mockResolvedValueOnce([{ name: 'IT', userCount: 1 }])
    act(() => {
      result.current.load()
    })
    await waitFor(() => expect(result.current.departments).toEqual([{ name: 'IT', userCount: 1 }]))
    expect(getDepartments).toHaveBeenCalledTimes(2)
  })
})
