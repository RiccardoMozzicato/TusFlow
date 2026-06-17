import { useEffect, useMemo } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ACTIONS, EVENTS, Joyride, STATUS, type EventHandler, type Step } from 'react-joyride'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import { buildSteps } from '../tours'
import { joyrideLocale, joyrideOptions, joyrideStyles } from '../joyrideStyles'
import {
  completeTour,
  selectStepIndex,
  selectTourId,
  selectTourRun,
  setStepIndex,
  skipTour,
} from '../onboardingSlice'

// The single component that knows about react-joyride. It reads the controlled
// tour state from the store, maps the library-agnostic step registry onto Joyride
// steps, drives cross-page navigation, and translates Joyride events back into
// store actions. Swapping the tour library means rewriting only this file.
export default function OnboardingTour() {
  const dispatch = useAppDispatch()
  const run = useAppSelector(selectTourRun)
  const tourId = useAppSelector(selectTourId)
  const stepIndex = useAppSelector(selectStepIndex)
  const user = useAppSelector(selectCurrentUser)
  const navigate = useNavigate()
  const location = useLocation()

  // Steps resolved for this user (role-gated steps dropped) so the controlled
  // index stays aligned with what Joyride renders.
  const steps = useMemo(
    () => (tourId ? buildSteps(tourId, user?.roles) : []),
    [tourId, user?.roles],
  )

  const joyrideSteps: Step[] = useMemo(
    () =>
      steps.map((step) => ({
        target: step.target === 'center' ? 'body' : step.target,
        title: step.title,
        content: step.content,
        placement: step.target === 'center' ? 'center' : step.placement,
      })),
    [steps],
  )

  // When the active step lives on a different route, navigate there. Joyride then
  // waits (targetWaitTimeout) for the target to mount after the page loads.
  useEffect(() => {
    if (!tourId || !run) return
    const step = steps[stepIndex]
    if (step?.path && location.pathname !== step.path) {
      navigate(step.path)
    }
  }, [tourId, run, stepIndex, steps, location.pathname, navigate])

  const handleEvent: EventHandler = (data) => {
    const { action, index, size, status, type } = data

    // The user dismissed the whole tour (Skip button or skipped status).
    if (status === STATUS.SKIPPED || action === ACTIONS.SKIP) {
      dispatch(skipTour())
      return
    }
    // Joyride considers the tour finished (advanced past the last step).
    if (status === STATUS.FINISHED) {
      dispatch(completeTour())
      return
    }
    // After a step: advance/retreat the controlled index, or end on close/last.
    if (type === EVENTS.STEP_AFTER || type === EVENTS.TARGET_NOT_FOUND) {
      if (action === ACTIONS.CLOSE) {
        dispatch(completeTour())
        return
      }
      const nextIndex = index + (action === ACTIONS.PREV ? -1 : 1)
      if (nextIndex >= size) {
        dispatch(completeTour())
      } else {
        dispatch(setStepIndex(Math.max(0, nextIndex)))
      }
    }
  }

  if (!tourId || joyrideSteps.length === 0) return null

  return (
    <Joyride
      run={run}
      stepIndex={stepIndex}
      steps={joyrideSteps}
      continuous
      scrollToFirstStep
      options={joyrideOptions}
      styles={joyrideStyles}
      locale={joyrideLocale}
      onEvent={handleEvent}
    />
  )
}
