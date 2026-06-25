import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { RootState } from '@/app/store'
import { fetchUserInfo } from './authApi'
import type { AuthState, UserInfo } from './types'

const initialState: AuthState = {
  status: 'idle',
  user: null,
  error: null,
}

// Bootstraps auth on app load: asks the gateway who the user is.
export const loadCurrentUser = createAsyncThunk<UserInfo | null>(
  'auth/loadCurrentUser',
  async () => fetchUserInfo(),
)

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    // Driven by the session heartbeat (useSessionHeartbeat), not the bootstrap thunk.
    // A 401 probe means the gateway session is gone: flip to anonymous so the already-
    // mounted ProtectedRoute redirects to login. Kept separate from loadCurrentUser
    // so a transient network error (thunk rejected) can't masquerade as expiry.
    sessionExpired(state) {
      state.status = 'anonymous'
      state.user = null
      state.error = null
    },
    // A 200 probe: keep the user authenticated and refresh their fields.
    sessionRefreshed(state, action: PayloadAction<UserInfo>) {
      state.status = 'authenticated'
      state.user = action.payload
      state.error = null
    },
  },
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

export const { sessionExpired, sessionRefreshed } = authSlice.actions

export default authSlice.reducer

// Selectors
export const selectAuthStatus = (state: RootState) => state.auth.status
export const selectCurrentUser = (state: RootState) => state.auth.user
export const selectIsAuthenticated = (state: RootState) =>
  state.auth.status === 'authenticated'
export const selectHasRole = (role: string) => (state: RootState) =>
  state.auth.user?.roles.includes(role) ?? false
