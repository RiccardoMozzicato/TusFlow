import { describe, expect, it, vi } from 'vitest'
import { http } from '@/api/axios'
import { API } from '@/config'
import { createUser, deleteUser, getUsers, updateUser, type UserDTO } from './usersApi'

vi.mock('@/api/axios', () => ({
  http: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

const mockedGet = vi.mocked(http.get)
const mockedPost = vi.mocked(http.post)
const mockedPut = vi.mocked(http.put)
const mockedDelete = vi.mocked(http.delete)

const SAMPLE_USER: UserDTO = {
  id: 'u-1',
  fullName: 'Ada Lovelace',
  email: 'ada@example.com',
  organization: 'Acme',
  department: 'R&D',
  role: 'ROLE_staff',
  storageQuota: 1024,
  storageUsed: 0,
}

describe('getUsers', () => {
  it('requests a page of users', async () => {
    mockedGet.mockResolvedValue({ data: { content: [] } })
    await getUsers(2, 25)
    expect(mockedGet).toHaveBeenCalledWith(API.users, { params: { page: 2, size: 25 } })
  })
})

describe('createUser', () => {
  it('posts the creation payload to the users endpoint', async () => {
    mockedPost.mockResolvedValue({ data: undefined })
    const payload = {
      username: 'grace',
      email: 'grace@example.com',
      organization: 'Acme',
      role: 'ROLE_manager' as const,
      department: null,
      storageQuota: 2048,
    }
    await createUser(payload)
    expect(mockedPost).toHaveBeenCalledWith(API.users, payload)
  })
})

describe('updateUser', () => {
  it('puts the full user to its id-scoped endpoint', async () => {
    mockedPut.mockResolvedValue({ data: undefined })
    await updateUser(SAMPLE_USER)
    expect(mockedPut).toHaveBeenCalledWith(`${API.users}/u-1`, SAMPLE_USER)
  })
})

describe('deleteUser', () => {
  it('deletes by id', async () => {
    mockedDelete.mockResolvedValue({ data: undefined })
    await deleteUser('u-1')
    expect(mockedDelete).toHaveBeenCalledWith(`${API.users}/u-1`)
  })
})
