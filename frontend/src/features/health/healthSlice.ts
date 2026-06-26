import { createSlice } from '@reduxjs/toolkit'
import type { RootState } from '@/app/store'

// Coarse backend connectivity, driven by the session heartbeat (useSessionHeartbeat):
//  - 'online'  : the last userinfo probe reached the gateway (200 or 401)
//  - 'offline' : the last probe threw (network error / 5xx) — backend unreachable
// Session validity itself lives in the auth slice; this slice is only about reachability.
export type Connectivity = 'online' | 'offline'

interface HealthState {
  connectivity: Connectivity
  // Epoch ms of the last completed probe, or null before the first one.
  lastCheckAt: number | null
}

// Start optimistic: the bootstrap userinfo probe corrects this on the first tick.
const initialState: HealthState = {
  connectivity: 'online',
  lastCheckAt: null,
}

const healthSlice = createSlice({
  name: 'health',
  initialState,
  reducers: {
    setOnline(state) {
      state.connectivity = 'online'
      state.lastCheckAt = Date.now()
    },
    setOffline(state) {
      state.connectivity = 'offline'
      state.lastCheckAt = Date.now()
    },
  },
})

export const { setOnline, setOffline } = healthSlice.actions

export default healthSlice.reducer

// Selectors
export const selectConnectivity = (state: RootState) => state.health.connectivity
export const selectIsOffline = (state: RootState) =>
  state.health.connectivity === 'offline'
