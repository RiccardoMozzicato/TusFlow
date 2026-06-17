import { useEffect, useRef } from 'react'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import { selectCompleted, startTour } from './onboardingSlice'

// Auto-starts the welcome tour the first time an authenticated user lands in the
// app shell. Because it lives inside AppLayout (the authenticated shell), the
// tour can never run on the login page — the "skip login" requirement is met by
// construction.
export function useOnboarding(): void {
  const dispatch = useAppDispatch()
  const user = useAppSelector(selectCurrentUser)
  const completed = useAppSelector(selectCompleted)
  // Guard against re-dispatching across re-renders / StrictMode double-invoke.
  const startedRef = useRef(false)

  useEffect(() => {
    if (startedRef.current) return
    if (user?.uuid && !completed.main) {
      startedRef.current = true
      dispatch(startTour('main'))
    }
  }, [dispatch, user?.uuid, completed.main])
}
