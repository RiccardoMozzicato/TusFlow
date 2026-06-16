import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useVersionFavorites } from './useVersionFavorites'
import * as detailsApi from './detailsApi'

vi.mock('./detailsApi', () => ({
  getFavoritedVersions: vi.fn(),
  addFavorite: vi.fn(),
  removeFavorite: vi.fn(),
}))

const getFavoritedVersionsMock = vi.mocked(detailsApi.getFavoritedVersions)
const addFavoriteMock = vi.mocked(detailsApi.addFavorite)
const removeFavoriteMock = vi.mocked(detailsApi.removeFavorite)

describe('useVersionFavorites', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getFavoritedVersionsMock.mockResolvedValue([2])
    addFavoriteMock.mockResolvedValue(undefined)
    removeFavoriteMock.mockResolvedValue(undefined)
  })

  it('loads the favorited versions on mount', async () => {
    const { result } = renderHook(() => useVersionFavorites('file-1'))
    await waitFor(() => expect(result.current.favoritedVersions.has(2)).toBe(true))
    expect(getFavoritedVersionsMock).toHaveBeenCalledWith('file-1')
  })

  it('optimistically adds a favorite and calls the API', async () => {
    const { result } = renderHook(() => useVersionFavorites('file-1'))
    await waitFor(() => expect(result.current.favoritedVersions.has(2)).toBe(true))

    act(() => result.current.toggle(5))

    expect(result.current.favoritedVersions.has(5)).toBe(true)
    expect(addFavoriteMock).toHaveBeenCalledWith('file-1', 5)
  })

  it('optimistically removes an existing favorite', async () => {
    const { result } = renderHook(() => useVersionFavorites('file-1'))
    await waitFor(() => expect(result.current.favoritedVersions.has(2)).toBe(true))

    act(() => result.current.toggle(2))

    expect(result.current.favoritedVersions.has(2)).toBe(false)
    expect(removeFavoriteMock).toHaveBeenCalledWith('file-1', 2)
  })

  it('reverts the optimistic update and surfaces an error when the API fails', async () => {
    addFavoriteMock.mockRejectedValue(new Error('boom'))
    const { result } = renderHook(() => useVersionFavorites('file-1'))
    await waitFor(() => expect(result.current.favoritedVersions.has(2)).toBe(true))

    act(() => result.current.toggle(5))
    expect(result.current.favoritedVersions.has(5)).toBe(true)

    await waitFor(() => expect(result.current.favoritedVersions.has(5)).toBe(false))
    expect(result.current.error).toBe('boom')
  })

  it('keeps an empty set when the initial load fails', async () => {
    getFavoritedVersionsMock.mockRejectedValue(new Error('nope'))
    const { result } = renderHook(() => useVersionFavorites('file-1'))
    await waitFor(() => expect(getFavoritedVersionsMock).toHaveBeenCalled())
    expect(result.current.favoritedVersions.size).toBe(0)
    expect(result.current.error).toBeNull()
  })
})
