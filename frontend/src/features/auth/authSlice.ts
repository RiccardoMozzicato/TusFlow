import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import type { RootState } from '@/app/store'
import { getCurrentUser } from '@/features/users/usersApi'
import {fetchUserInfo} from './authApi'
import type { AuthState, UserInfo } from './types'

const initialState: AuthState = {
  status: 'idle',
  user: null,
  error: null,
}

// Bootstraps auth on app load: the gateway (/userinfo, from Keycloak) is the
// source of identity and roles, but it can't know the avatar — that lives behind
// UserDetailService/MinIO. So we also read /me and merge its photoProfileUrl in.
// A /me failure must not break auth: the user stays authenticated, photo null.
export const loadCurrentUser = createAsyncThunk<UserInfo | null>(
  'auth/loadCurrentUser',
  async () => {
    const userInfo = await fetchUserInfo()
    if (!userInfo) return null

    try {
      const me = await getCurrentUser()
      return { ...userInfo, photoProfileUrl: me.photoProfileUrl ?? null }
    } catch {
      return { ...userInfo, photoProfileUrl: null }
    }
  },
)

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(loadCurrentUser.pending, (state) => {
        state.status = 'loading'
        state.error = null
      })
      .addCase(loadCurrentUser.fulfilled, (state, action) => {
        if (action.payload) {
          state.status = 'authenticated'
          state.user = action.payload
        } else {
          state.status = 'anonymous'
          state.user = null
        }
      })
      .addCase(loadCurrentUser.rejected, (state, action) => {
        state.status = 'anonymous'
        state.user = null
        state.error = action.error.message ?? 'Failed to load user'
      })
  },
})

export default authSlice.reducer

// Selectors
export const selectAuthStatus = (state: RootState) => state.auth.status
export const selectCurrentUser = (state: RootState) => state.auth.user
export const selectIsAuthenticated = (state: RootState) =>
  state.auth.status === 'authenticated'
export const selectHasRole = (role: string) => (state: RootState) =>
  state.auth.user?.roles.includes(role) ?? false
