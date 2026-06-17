import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import onboardingReducer, {
  completeTour,
  nextStep,
  pauseTour,
  prevStep,
  resumeTour,
  setStepIndex,
  skipTour,
  startTour,
} from './onboardingSlice'

const STORAGE_KEY = 'onboarding.v1'

const idle = {
  run: false,
  tourId: null,
  stepIndex: 0,
  completed: {},
}

describe('onboardingSlice', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })
  afterEach(() => {
    localStorage.clear()
  })

  test('startTour runs the given tour from step 0', () => {
    const next = onboardingReducer(idle, startTour('main'))
    expect(next.run).toBe(true)
    expect(next.tourId).toBe('main')
    expect(next.stepIndex).toBe(0)
  })

  test('nextStep / prevStep move the index and never go below 0', () => {
    let state = onboardingReducer(idle, startTour('library'))
    state = onboardingReducer(state, nextStep())
    expect(state.stepIndex).toBe(1)
    state = onboardingReducer(state, prevStep())
    expect(state.stepIndex).toBe(0)
    state = onboardingReducer(state, prevStep())
    expect(state.stepIndex).toBe(0)
  })

  test('setStepIndex sets the controlled index explicitly', () => {
    const state = onboardingReducer({ ...idle, run: true, tourId: 'main' }, setStepIndex(4))
    expect(state.stepIndex).toBe(4)
  })

  test('pauseTour stops without clearing the active tour; resumeTour restarts it', () => {
    const running = { ...idle, run: true, tourId: 'upload' as const, stepIndex: 2 }
    const paused = onboardingReducer(running, pauseTour())
    expect(paused.run).toBe(false)
    expect(paused.tourId).toBe('upload')
    expect(paused.stepIndex).toBe(2)
    const resumed = onboardingReducer(paused, resumeTour())
    expect(resumed.run).toBe(true)
  })

  test('completeTour marks the tour done, resets state, and persists it', () => {
    const running = { ...idle, run: true, tourId: 'main' as const, stepIndex: 5 }
    const next = onboardingReducer(running, completeTour())
    expect(next.run).toBe(false)
    expect(next.tourId).toBeNull()
    expect(next.stepIndex).toBe(0)
    expect(next.completed.main).toBe(true)
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toEqual({
      completed: { main: true },
    })
  })

  test('skipTour also marks the tour completed and persists it', () => {
    const running = { ...idle, run: true, tourId: 'library' as const, stepIndex: 1 }
    const next = onboardingReducer(running, skipTour())
    expect(next.completed.library).toBe(true)
    expect(next.run).toBe(false)
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!).completed.library).toBe(true)
  })

  test('resumeTour is a no-op when no tour is active', () => {
    const next = onboardingReducer(idle, resumeTour())
    expect(next.run).toBe(false)
  })
})
