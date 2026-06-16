import { describe, expect, it, vi, beforeEach } from 'vitest'
import { http } from '@/api/axios'
import { API } from '@/config'
import {
  deleteFile,
  deleteFileShare,
  getWritableFileIds,
  isFileWritable,
  Permission,
  updateFileShare,
  updateFileVersionMetadata,
  updateGeneratedMetadata,
} from './detailsApi'

vi.mock('@/api/axios', () => ({
  http: { patch: vi.fn(), post: vi.fn(), delete: vi.fn() },
}))
const mockedPatch = vi.mocked(http.patch)
const mockedPost = vi.mocked(http.post)
const mockedDelete = vi.mocked(http.delete)

beforeEach(() => {
  vi.clearAllMocks()
})

describe('updateGeneratedMetadata', () => {
  it('PATCHes the generatedmetadata endpoint with the version param and body', async () => {
    mockedPatch.mockResolvedValue({ data: undefined })

    await updateGeneratedMetadata('file-1', 3, {
      sensitivity: 'CONFIDENTIAL',
      tags: 'Q3,2023',
    })

    expect(mockedPatch).toHaveBeenCalledWith(
      `${API.files}/generatedmetadata/file-1`,
      { sensitivity: 'CONFIDENTIAL', tags: 'Q3,2023' },
      { params: { version: 3 } },
    )
  })
})

describe('updateFileVersionMetadata', () => {
  it('PATCHes the version endpoint with the metadata map as the body', async () => {
    mockedPatch.mockResolvedValue({ data: undefined })

    await updateFileVersionMetadata('file-1', 2, { department: 'Finance', old: '' })

    expect(mockedPatch).toHaveBeenCalledWith(`${API.files}/file-1/versions/2`, {
      department: 'Finance',
      old: '',
    })
  })
})

describe('getWritableFileIds', () => {
  it('POSTs the candidate ids and returns the writable subset', async () => {
    mockedPost.mockResolvedValue({ data: ['file-1'] })

    const writable = await getWritableFileIds(['file-1', 'file-2'])

    expect(mockedPost).toHaveBeenCalledWith(`${API.files}/writable-ids`, [
      'file-1',
      'file-2',
    ])
    expect(writable).toEqual(['file-1'])
  })

  it('short-circuits without a request for an empty candidate list', async () => {
    const writable = await getWritableFileIds([])
    expect(writable).toEqual([])
    expect(mockedPost).not.toHaveBeenCalled()
  })
})

describe('isFileWritable', () => {
  it('returns true when the id is in the writable subset', async () => {
    mockedPost.mockResolvedValue({ data: ['file-1'] })
    await expect(isFileWritable('file-1')).resolves.toBe(true)
  })

  it('returns false when the id is absent from the writable subset', async () => {
    mockedPost.mockResolvedValue({ data: [] })
    await expect(isFileWritable('file-1')).resolves.toBe(false)
  })
})

describe('updateFileShare', () => {
  it('PATCHes the share endpoint with the permission query param', async () => {
    mockedPatch.mockResolvedValue({ data: { id: 5, permission: Permission.READ_WRITE } })

    await updateFileShare('file-1', 5, Permission.READ_WRITE)

    expect(mockedPatch).toHaveBeenCalledWith(`${API.files}/file-1/shares/5`, null, {
      params: { permission: Permission.READ_WRITE },
    })
  })
})

describe('deleteFileShare', () => {
  it('DELETEs the specific share', async () => {
    mockedDelete.mockResolvedValue({ data: undefined })

    await deleteFileShare('file-1', 5)

    expect(mockedDelete).toHaveBeenCalledWith(`${API.files}/file-1/shares/5`)
  })
})

describe('deleteFile', () => {
  it('DELETEs the file', async () => {
    mockedDelete.mockResolvedValue({ data: undefined })

    await deleteFile('file-1')

    expect(mockedDelete).toHaveBeenCalledWith(`${API.files}/file-1`)
  })
})
