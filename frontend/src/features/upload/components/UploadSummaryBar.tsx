import Icon from '@/components/Icon'
import { useAppSelector } from '@/app/hooks'
import { selectSidebarCollapsed } from '@/features/ui/uiSlice'
import type { UploadSummary } from '../types'

interface UploadSummaryBarProps {
  summary: UploadSummary
  onPauseAll: () => void
  onResumeAll: () => void
  onClearCompleted: () => void
  /** Enable Pause All only while something is actively uploading. */
  canPause: boolean
  /** Enable Resume All only while something is paused. */
  canResume: boolean
  /** Enable Clear Completed only when completed rows exist. */
  canClear: boolean
}

const DISABLED = 'cursor-not-allowed opacity-40'

// Pinned to the bottom of the content column. Its left edge tracks the sidebar
// width (collapsed rail vs full), matching AppLayout's `ml-20`/`ml-72`, so the
// bar spans from the sidebar edge to the viewport's right. The main scroll area
// reserves space via its `pb-24`.
export default function UploadSummaryBar({
  summary,
  onPauseAll,
  onResumeAll,
  onClearCompleted,
  canPause,
  canResume,
  canClear,
}: UploadSummaryBarProps) {
  const collapsed = useAppSelector(selectSidebarCollapsed)
  const percent = Math.round(summary.progress * 100)

  return (
    <div
      className={`fixed bottom-0 right-0 z-30 border-t border-ds-outline-variant bg-ds-surface px-10 py-3 shadow-[0_-4px_10px_rgba(0,0,0,0.03)] transition-all duration-200 ${
        collapsed ? 'left-20' : 'left-72'
      }`}
    >
      <div className="flex items-center justify-between">
        <div className="flex flex-1 items-center gap-6">
          <div className="flex flex-col">
            <span className="font-ds-label text-ds-label-md text-ds-on-surface">
              Overall Progress
            </span>
            <span className="text-ds-body-sm text-ds-on-surface-variant">
              {summary.completed} of {summary.total} completed
            </span>
          </div>

          <div className="mx-4 max-w-md flex-1">
            <div className="mb-1 h-2 w-full overflow-hidden rounded-ds-full bg-ds-surface-variant">
              <div
                className="relative h-full overflow-hidden rounded-ds-full bg-ds-primary"
                style={{ width: `${percent}%` }}
              >
                <div className="absolute inset-0 w-full -translate-x-full bg-white/20 animate-[ds-shimmer_2s_infinite]" />
              </div>
            </div>
            <div className="flex justify-between font-ds-label text-ds-label-sm text-ds-on-surface-variant">
              <span>{percent}% Total</span>
              <span>{summary.speed}</span>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onPauseAll}
            disabled={!canPause}
            className={`flex items-center gap-2 rounded-ds-lg bg-ds-surface-variant px-4 py-2 font-ds-label text-ds-label-md text-ds-on-surface-variant transition-colors hover:bg-ds-surface-container-high hover:text-ds-on-surface ${
              canPause ? '' : DISABLED
            }`}
          >
            <Icon name="pause" filled={false} className="text-[18px]" />
            Pause All
          </button>
          <button
            type="button"
            onClick={onResumeAll}
            disabled={!canResume}
            className={`flex items-center gap-2 rounded-ds-lg bg-ds-primary px-4 py-2 font-ds-label text-ds-label-md text-ds-on-primary shadow-sm transition-colors hover:bg-ds-primary-container ${
              canResume ? '' : DISABLED
            }`}
          >
            <Icon name="play_arrow" filled={false} className="text-[18px]" />
            Resume All
          </button>
          <div className="mx-3 h-6 w-px bg-ds-outline-variant" />
          <button
            type="button"
            onClick={onClearCompleted}
            disabled={!canClear}
            className={`rounded-ds-lg border border-ds-outline-variant px-4 py-2 font-ds-label text-ds-label-md text-ds-on-surface transition-colors hover:bg-ds-surface-container-lowest ${
              canClear ? '' : DISABLED
            }`}
          >
            Clear Completed
          </button>
        </div>
      </div>
    </div>
  )
}
