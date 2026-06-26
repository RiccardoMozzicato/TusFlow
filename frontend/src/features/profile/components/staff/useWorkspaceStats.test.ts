import { renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useWorkspaceStats } from './useWorkspaceStats'
import * as usersApi from '@/features/users/usersApi'
import * as documentsApi from '@/features/documents/documentsApi'
import type { UserDTO } from '@/features/users/usersApi'
import type { FileDTO, PageResponse } from '@/features/documents/documentsApi'

vi.mock('@/features/users/usersApi', () => ({
  getCurrentUser: vi.fn(),
}))

vi.mock('@/features/documents/documentsApi', async () => {
  const actual = await vi.importActual<typeof documentsApi>(
    '@/features/documents/documentsApi',
  )
  return { ...actual, getFiles: vi.fn() }
})

const getCurrentUserMock = vi.mocked(usersApi.getCurrentUser)
const getFilesMock = vi.mocked(documentsApi.getFiles)

const GIGABYTE = 1024 ** 3

function user(overrides: Partial<UserDTO> = {}): UserDTO {
  return {
    id: 'user-1',
    fullName: 'Ada Lovelace',
    email: 'ada@example.com',
    organization: 'Acme',
    role: 'ROLE_staff',
    storageUsed: 2 * GIGABYTE,
    storageQuota: 10 * GIGABYTE,
    ...overrides,
  }
}

function filesPage(total: number): PageResponse<FileDTO> {
  return {
    content: [],
    totalElements: total,
    totalPages: 1,
    size: 1,
    number: 0,
    first: true,
    last: true,
    empty: total === 0,
  }
}

describe('useWorkspaceStats', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getCurrentUserMock.mockResolvedValue(user())
    getFilesMock.mockResolvedValue(filesPage(42))
  })

  it('requests only the owned-files count (size 1, OWNED)', async () => {
    renderHook(() => useWorkspaceStats())

    await waitFor(() => expect(getFilesMock).toHaveBeenCalledTimes(1))
    expect(getFilesMock).toHaveBeenCalledWith({
      size: 1,
      ownership: documentsApi.Ownership.OWNED,
    })
  })

  it('exposes the document total and storage usage from live data', async () => {
    const { result } = renderHook(() => useWorkspaceStats())

    await waitFor(() => expect(result.current.status).toBe('succeeded'))
    expect(result.current.stats?.documentCount).toBe(42)
    expect(result.current.stats?.storage.usedLabel).toBe('2.0 GB')
    expect(result.current.stats?.storage.quotaLabel).toBe('10.0 GB')
    expect(result.current.stats?.storage.fraction).toBeCloseTo(0.2)
    expect(result.current.error).toBeNull()
  })

  it('treats a null quota as unlimited', async () => {
    getCurrentUserMock.mockResolvedValue(user({ storageQuota: null }))

    const { result } = renderHook(() => useWorkspaceStats())

    await waitFor(() => expect(result.current.status).toBe('succeeded'))
    expect(result.current.stats?.storage.quotaLabel).toBeNull()
    expect(result.current.stats?.storage.fraction).toBe(0)
  })

  it('surfaces an error when a request fails', async () => {
    getFilesMock.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useWorkspaceStats())

    await waitFor(() => expect(result.current.status).toBe('failed'))
    expect(result.current.error).toBe('boom')
    expect(result.current.stats).toBeNull()
  })
})
