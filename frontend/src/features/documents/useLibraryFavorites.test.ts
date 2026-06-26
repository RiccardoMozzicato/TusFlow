import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useLibraryFavorites } from './useLibraryFavorites'
import * as detailsApi from './detailsApi'
import * as favoritesApi from '@/features/favorites/favoritesApi'
import type { FileVersionFavoriteDTO } from '@/features/favorites/favoritesApi'
import type { PageResponse } from './documentsApi'

vi.mock('./detailsApi', () => ({
  addFavorite: vi.fn(),
  removeFavorite: vi.fn(),
}))

vi.mock('@/features/favorites/favoritesApi', () => ({
  getMyFavorites: vi.fn(),
}))

const getMyFavoritesMock = vi.mocked(favoritesApi.getMyFavorites)
const addFavoriteMock = vi.mocked(detailsApi.addFavorite)
const removeFavoriteMock = vi.mocked(detailsApi.removeFavorite)

function favorite(fileId: string, version: number): FileVersionFavoriteDTO {
  return {
    id: 1,
    fileId,
    fileName: 'doc.pdf',
    version,
    size: 10,
    contentType: 'application/pdf',
    status: 'AVAILABLE',
    ownerId: 'owner-1',
    favoritedAt: 'Mon, 20 Oct 2023 09:00:00 GMT',
  }
}

function page(
  content: FileVersionFavoriteDTO[],
  overrides: Partial<PageResponse<FileVersionFavoriteDTO>> = {},
): PageResponse<FileVersionFavoriteDTO> {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    size: content.length,
    number: 0,
    first: true,
    last: true,
    empty: content.length === 0,
    ...overrides,
  }
}

describe('useLibraryFavorites', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getMyFavoritesMock.mockResolvedValue(page([favorite('file-1', 2)]))
    addFavoriteMock.mockResolvedValue(undefined)
    removeFavoriteMock.mockResolvedValue(undefined)
  })

  it('loads the favorited versions on mount', async () => {
    const { result } = renderHook(() => useLibraryFavorites())
    await waitFor(() => expect(result.current.isFavorited('file-1', 2)).toBe(true))
    expect(result.current.isFavorited('file-1', 1)).toBe(false)
    expect(result.current.isFavorited('file-2', 2)).toBe(false)
  })

  it('walks every page of favorites', async () => {
    getMyFavoritesMock
      .mockResolvedValueOnce(
        page([favorite('file-1', 2)], { last: false, totalPages: 2 }),
      )
      .mockResolvedValueOnce(
        page([favorite('file-2', 1)], { number: 1, first: false, totalPages: 2 }),
      )

    const { result } = renderHook(() => useLibraryFavorites())
    await waitFor(() => expect(result.current.isFavorited('file-2', 1)).toBe(true))
    expect(result.current.isFavorited('file-1', 2)).toBe(true)
    expect(getMyFavoritesMock).toHaveBeenCalledTimes(2)
  })

  it('optimistically adds a favorite and calls the API', async () => {
    const { result } = renderHook(() => useLibraryFavorites())
    await waitFor(() => expect(result.current.isFavorited('file-1', 2)).toBe(true))

    act(() => result.current.toggle('file-3', 5))

    expect(result.current.isFavorited('file-3', 5)).toBe(true)
    expect(addFavoriteMock).toHaveBeenCalledWith('file-3', 5)
  })

  it('optimistically removes an existing favorite', async () => {
    const { result } = renderHook(() => useLibraryFavorites())
    await waitFor(() => expect(result.current.isFavorited('file-1', 2)).toBe(true))

    act(() => result.current.toggle('file-1', 2))

    expect(result.current.isFavorited('file-1', 2)).toBe(false)
    expect(removeFavoriteMock).toHaveBeenCalledWith('file-1', 2)
  })

  it('reverts the optimistic update and surfaces an error when the API fails', async () => {
    addFavoriteMock.mockRejectedValue(new Error('boom'))
    const { result } = renderHook(() => useLibraryFavorites())
    await waitFor(() => expect(result.current.isFavorited('file-1', 2)).toBe(true))

    act(() => result.current.toggle('file-3', 5))
    expect(result.current.isFavorited('file-3', 5)).toBe(true)

    await waitFor(() => expect(result.current.isFavorited('file-3', 5)).toBe(false))
    expect(result.current.error).toBe('boom')
  })

  it('keeps an empty set when the initial load fails', async () => {
    getMyFavoritesMock.mockRejectedValue(new Error('nope'))
    const { result } = renderHook(() => useLibraryFavorites())
    await waitFor(() => expect(getMyFavoritesMock).toHaveBeenCalled())
    expect(result.current.isFavorited('file-1', 2)).toBe(false)
    expect(result.current.error).toBeNull()
  })
})
