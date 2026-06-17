import { createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { RootState } from '@/app/store'

// The tours the app knows about. `main` is the cross-page welcome tour that
// auto-starts on first access; the rest are contextual tours replayable from Help.
export type TourId = 'main' | 'library' | 'upload' | 'details'

// Bump the version suffix to re-show the welcome tour to everyone after a major
// UI change (the old key is simply ignored).
const STORAGE_KEY = 'onboarding.v1'

type CompletedMap = Partial<Record<TourId, boolean>>

interface OnboardingState {
  /** Whether a tour is currently running. */
  run: boolean
  /** Which tour is active, or null when idle. */
  tourId: TourId | null
  /** Controlled step index, driven by the tour callback (cross-page support). */
  stepIndex: number
  /** Which tours the user has finished or skipped; hydrated from localStorage. */
  completed: CompletedMap
}

// Read the persisted completion map. Guard against unavailable storage (private
// mode) and corrupt JSON, mirroring uiSlice's defensive loader.
function loadCompleted(): CompletedMap {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw) as { completed?: CompletedMap }
    return parsed.completed ?? {}
  } catch {
    return {}
  }
}

function persistCompleted(completed: CompletedMap): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ completed }))
  } catch {
    // Persistence is best-effort; ignore storage failures.
  }
}

const initialState: OnboardingState = {
  run: false,
  tourId: null,
  stepIndex: 0,
  completed: loadCompleted(),
}

// Marks the active tour done (finished or skipped), persists it, and resets the
// running state. Shared by skipTour and completeTour.
function finishActiveTour(state: OnboardingState): void {
  if (state.tourId) {
    state.completed[state.tourId] = true
    persistCompleted(state.completed)
  }
  state.run = false
  state.tourId = null
  state.stepIndex = 0
}

const onboardingSlice = createSlice({
  name: 'onboarding',
  initialState,
  reducers: {
    startTour(state, action: PayloadAction<TourId>) {
      state.tourId = action.payload
      state.stepIndex = 0
      state.run = true
    },
    // Soft stop used while changing route between steps: keeps tourId/stepIndex
    // so the driver can resume once the next target mounts.
    pauseTour(state) {
      state.run = false
    },
    resumeTour(state) {
      if (state.tourId) state.run = true
    },
    setStepIndex(state, action: PayloadAction<number>) {
      state.stepIndex = action.payload
    },
    nextStep(state) {
      state.stepIndex += 1
    },
    prevStep(state) {
      state.stepIndex = Math.max(0, state.stepIndex - 1)
    },
    skipTour(state) {
      finishActiveTour(state)
    },
    completeTour(state) {
      finishActiveTour(state)
    },
  },
})

export const {
  startTour,
  pauseTour,
  resumeTour,
  setStepIndex,
  nextStep,
  prevStep,
  skipTour,
  completeTour,
} = onboardingSlice.actions

export const selectTourRun = (state: RootState) => state.onboarding.run
export const selectTourId = (state: RootState) => state.onboarding.tourId
export const selectStepIndex = (state: RootState) => state.onboarding.stepIndex
export const selectCompleted = (state: RootState) => state.onboarding.completed

export default onboardingSlice.reducer
