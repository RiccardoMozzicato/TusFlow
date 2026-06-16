import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { RootState } from '@/app/store'

const STORAGE_KEY = 'ui.sidebarCollapsed'

interface UiState {
  sidebarCollapsed: boolean
}

// Read the persisted preference so the sidebar opens in the user's last state.
// Guard against unavailable storage (SSR, private mode) and bad JSON.
function loadCollapsed(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'true'
  } catch {
    return false
  }
}

function persistCollapsed(collapsed: boolean): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(collapsed))
  } catch {
    // Persistence is best-effort; ignore storage failures.
  }
}

const initialState: UiState = {
  sidebarCollapsed: loadCollapsed(),
}

const uiSlice = createSlice({
  name: 'ui',
  initialState,
  reducers: {
    toggleSidebar(state) {
      state.sidebarCollapsed = !state.sidebarCollapsed
      persistCollapsed(state.sidebarCollapsed)
    },
    setSidebarCollapsed(state, action: PayloadAction<boolean>) {
      state.sidebarCollapsed = action.payload
      persistCollapsed(state.sidebarCollapsed)
    },
  },
})

export const { toggleSidebar, setSidebarCollapsed } = uiSlice.actions

export const selectSidebarCollapsed = (state: RootState) =>
  state.ui.sidebarCollapsed

export default uiSlice.reducer
