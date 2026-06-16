import { describe, expect, it, vi } from 'vitest'
import { http } from '@/api/axios'
import { API } from '@/config'
import { getFiles, SharingScope } from './documentsApi'

vi.mock('@/api/axios', () => ({ http: { get: vi.fn() } }))
const mockedGet = vi.mocked(http.get)

describe('getFiles', () => {
  it('requests the files endpoint with repeated-key array serialization', async () => {
    mockedGet.mockResolvedValue({ data: { content: [] } })

    await getFiles({
      page: 0,
      size: 12,
      sharingScope: [SharingScope.PERSONAL, SharingScope.DEPARTMENT, SharingScope.ORGANIZATION],
    })

    expect(mockedGet).toHaveBeenCalledWith(API.files, {
      params: {
        page: 0,
        size: 12,
        sharingScope: ['PERSONAL', 'DEPARTMENT', 'ORGANIZATION'],
      },
      // indexes: null → `sharingScope=PERSONAL&sharingScope=DEPARTMENT&…`,
      // which Spring binds to a Set<SharingScope>.
      paramsSerializer: { indexes: null },
    })
  })

  it('returns the response payload', async () => {
    const page = { content: [], totalElements: 0 }
    mockedGet.mockResolvedValue({ data: page })
    await expect(getFiles()).resolves.toBe(page)
  })
})
