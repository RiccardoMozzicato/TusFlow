import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useUserManagement } from './useUserManagement'
import * as usersApi from './usersApi'
import type { PageResponse } from '@/features/documents/documentsApi'
import type { UserDTO } from './usersApi'

vi.mock('./usersApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./usersApi')>()
  return { ...actual, getUsers: vi.fn() }
})

const getUsersMock = vi.mocked(usersApi.getUsers)

const USER: UserDTO = {
  id: 'u-1',
  fullName: 'Ada Lovelace',
  email: 'ada@example.com',
  organization: 'Acme',
  department: 'R&D',
  role: 'ROLE_staff',
  storageQuota: 1024,
  storageUsed: 0,
}

function page(
  content: UserDTO[],
  overrides: Partial<PageResponse<UserDTO>> = {},
): PageResponse<UserDTO> {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    size: 10,
    number: 0,
    first: true,
    last: true,
    empty: content.length === 0,
    ...overrides,
  }
}

// Renders the hook inside a MemoryRouter so useSearchParams is available; the
// initial entry seeds the URL selection.
function renderManagement(initialEntry = '/admin/users') {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
  )
  return renderHook(() => useUserManagement(), { wrapper })
}

describe('useUserManagement', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getUsersMock.mockResolvedValue(page([USER]))
  })

  it('fetches page 0 with default sort on mount', async () => {
    const { result } = renderManagement()
    await waitFor(() => expect(result.current.status).toBe('succeeded'))
    expect(getUsersMock).toHaveBeenLastCalledWith({
      page: 0,
      size: 10,
      search: undefined,
      sort: 'fullName,asc',
    })
    expect(result.current.users).toEqual([USER])
  })

  it('initializes filters and page from the URL query string', async () => {
    renderManagement('/admin/users?page=2&search=rossi&sort=email,desc')
    await waitFor(() =>
      expect(getUsersMock).toHaveBeenLastCalledWith({
        page: 1,
        size: 10,
        search: 'rossi',
        sort: 'email,desc',
      }),
    )
  })

  it('applyFilters writes the selection to the URL and resets to page 0', async () => {
    const { result } = renderManagement('/admin/users?page=3')
    await waitFor(() => expect(result.current.status).toBe('succeeded'))

    act(() => result.current.applyFilters({ ...result.current.filters, sort: 'role,asc' }))

    await waitFor(() =>
      expect(getUsersMock).toHaveBeenLastCalledWith({
        page: 0,
        size: 10,
        search: undefined,
        sort: 'role,asc',
      }),
    )
  })

  it('goToPage keeps the active filters', async () => {
    const { result } = renderManagement('/admin/users?search=ada')
    await waitFor(() => expect(result.current.status).toBe('succeeded'))

    act(() => result.current.goToPage(2))

    await waitFor(() =>
      expect(getUsersMock).toHaveBeenLastCalledWith({
        page: 2,
        size: 10,
        search: 'ada',
        sort: 'fullName,asc',
      }),
    )
  })

  it('surfaces a friendly error when the fetch fails', async () => {
    getUsersMock.mockRejectedValueOnce(new Error('network down'))
    const { result } = renderManagement()
    await waitFor(() => expect(result.current.status).toBe('failed'))
    expect(result.current.error).toBe('network down')
  })
})
