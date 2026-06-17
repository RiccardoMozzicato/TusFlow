import { useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'
import Icon from '@/components/Icon'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import { selectTourRun, startTour } from '../onboardingSlice'
import { contextualTourForPath } from '../tours'

// Help launcher (the hybrid model's restart affordance): a floating "?" button
// that replays the welcome tour and offers a contextual tour for the current
// page. Sits to the left of the chat launcher so the two never overlap.
export default function HelpMenu() {
  const dispatch = useAppDispatch()
  const location = useLocation()
  const user = useAppSelector(selectCurrentUser)
  const tourRunning = useAppSelector(selectTourRun)
  const [open, setOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onPointer = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) setOpen(false)
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    window.addEventListener('mousedown', onPointer)
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('mousedown', onPointer)
      window.removeEventListener('keydown', onKey)
    }
  }, [open])

  // Hidden when signed out, and while a tour is already running (avoids overlap
  // with the Joyride overlay).
  if (!user || tourRunning) return null

  const pageTour = contextualTourForPath(location.pathname)

  function launch(tour: 'main' | NonNullable<ReturnType<typeof contextualTourForPath>>) {
    setOpen(false)
    dispatch(startTour(tour))
  }

  return (
    <div ref={menuRef} data-tour="help-button" className="fixed bottom-6 right-24 z-40">
      {open ? (
        <div
          role="menu"
          className="absolute bottom-full right-0 mb-2 min-w-[220px] overflow-hidden rounded-ds-lg border border-ds-outline-variant bg-ds-surface-container-lowest py-1 shadow-xl"
        >
          <button
            type="button"
            role="menuitem"
            onClick={() => launch('main')}
            className="flex w-full items-center gap-2 px-4 py-2 text-left font-ds-label text-ds-label-md text-ds-on-surface transition-colors hover:bg-ds-surface-container"
          >
            <Icon name="restart_alt" filled={false} className="text-[18px]" />
            Replay welcome tour
          </button>
          {pageTour ? (
            <button
              type="button"
              role="menuitem"
              onClick={() => launch(pageTour)}
              className="flex w-full items-center gap-2 px-4 py-2 text-left font-ds-label text-ds-label-md text-ds-on-surface transition-colors hover:bg-ds-surface-container"
            >
              <Icon name="tips_and_updates" filled={false} className="text-[18px]" />
              Tips for this page
            </button>
          ) : null}
        </div>
      ) : null}

      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Help and tours"
        title="Help and tours"
        className="flex h-14 w-14 items-center justify-center rounded-ds-full border border-ds-outline-variant bg-ds-surface-container-lowest text-ds-primary shadow-xl transition-all hover:bg-ds-surface-container active:scale-95"
      >
        <Icon name="help" className="text-[26px]" />
      </button>
    </div>
  )
}
