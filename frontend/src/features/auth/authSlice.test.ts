import { configureStore } from '@reduxjs/toolkit'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import authReducer, { loadCurrentUser } from './authSlice'
import { fetchUserInfo } from './authApi'
import { getCurrentUser } from '@/features/users/usersApi'
import type { UserInfo } from './types'

vi.mock('./authApi', () => ({ fetchUserInfo: vi.fn() }))
vi.mock('@/features/users/usersApi', () => ({ getCurrentUser: vi.fn() }))

const mockedFetchUserInfo = vi.mocked(fetchUserInfo)
const mockedGetCurrentUser = vi.mocked(getCurrentUser)

const USER_INFO: UserInfo = {
  uuid: 'u-1',
  username: 'ada',
  email: 'ada@example.com',
  fullName: 'Ada Lovelace',
  department: 'R&D',
  organization: 'Acme',
  roles: ['staff'],
  photoProfileUrl: null,
}

function makeStore() {
  return configureStore({ reducer: { auth: authReducer } })
}

describe('loadCurrentUser', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('merges photoProfileUrl from /me into the gateway user info', async () => {
    // Arrange
    mockedFetchUserInfo.mockResolvedValue(USER_INFO)
    mockedGetCurrentUser.mockResolvedValue({
      id: 'u-1',
      fullName: 'Ada Lovelace',
      email: 'ada@example.com',
      organization: 'Acme',
      role: 'ROLE_staff',
      photoProfileUrl: '/api/v1/users/u-1/avatar',
    })
    const store = makeStore()

    // Act
    await store.dispatch(loadCurrentUser())

    // Assert
    const user = store.getState().auth.user
    expect(user?.photoProfileUrl).toBe('/api/v1/users/u-1/avatar')
    expect(store.getState().auth.status).toBe('authenticated')
  })

  it('stays authenticated with null photo when /me fails', async () => {
    // Arrange
    mockedFetchUserInfo.mockResolvedValue(USER_INFO)
    mockedGetCurrentUser.mockRejectedValue(new Error('service down'))
    const store = makeStore()

    // Act
    await store.dispatch(loadCurrentUser())

    // Assert
    const user = store.getState().auth.user
    expect(user?.photoProfileUrl).toBeNull()
    expect(store.getState().auth.status).toBe('authenticated')
  })

  it('is anonymous when the gateway reports no session', async () => {
    // Arrange
    mockedFetchUserInfo.mockResolvedValue(null)
    const store = makeStore()

    // Act
    await store.dispatch(loadCurrentUser())

    // Assert
    expect(store.getState().auth.user).toBeNull()
    expect(store.getState().auth.status).toBe('anonymous')
    expect(mockedGetCurrentUser).not.toHaveBeenCalled()
  })
})
