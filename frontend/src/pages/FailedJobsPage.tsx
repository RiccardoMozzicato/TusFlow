import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import { formatDateTime } from '@/features/documents/mappers'
import { useFailedJobs, type RetryState } from '@/features/dashboard/useFailedJobs'
import type { FailedJob } from '@/features/documents/jobsApi'

const HEADERS = ['File', 'Attempts', 'Last error', 'Failed at']

// The small status cell shown after the user kicks off a retry, updated live as
// the queued job is polled to its outcome.
function RetryFeedback({ state }: { state: RetryState }) {
  if (state.status === 'reprocessing') {
    return (
      <span className="inline-flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-secondary">
        <Icon name="progress_activity" className="animate-spin text-[14px]" />
        {state.message ?? 'Reprocessing…'}
      </span>
    )
  }
  if (state.status === 'succeeded') {
    return (
      <span className="inline-flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-tertiary">
        <Icon name="check_circle" className="text-[14px]" />
        Reprocessed successfully
      </span>
    )
  }
  if (state.status === 'deadlettered') {
    return (
      <span className="inline-flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-error">
        <Icon name="gpp_bad" className="text-[14px]" />
        {state.message}
      </span>
    )
  }
  if (state.status === 'failed' || state.status === 'error') {
    return (
      <span className="inline-flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-error">
        <Icon name="error" className="text-[14px]" />
        {state.message}
      </span>
    )
  }
  return null
}

function RetryButton({
  job,
  state,
  onRetry,
}: {
  job: FailedJob
  state: RetryState
  onRetry: (job: FailedJob) => void
}) {
  // While reprocessing, once it succeeded, or once it dead-lettered, the job is
  // no longer a retryable FAILED job — don't offer another retry (a repeat
  // failure short of the cap re-enables the button).
  if (
    state.status === 'reprocessing' ||
    state.status === 'succeeded' ||
    state.status === 'deadlettered'
  )
    return null

  const isRetrying = state.status === 'retrying'
  const isDisabled = isRetrying || !job.canRetry

  return (
    <button
      type="button"
      onClick={() => onRetry(job)}
      disabled={isDisabled}
      title={job.canRetry ? undefined : 'Retry limit reached for this file'}
      className="inline-flex items-center gap-1.5 rounded-ds border border-ds-outline-variant bg-ds-surface-container px-3 py-1.5 font-ds-label text-ds-label-sm text-ds-on-surface transition-colors hover:bg-ds-surface-container-high disabled:cursor-not-allowed disabled:opacity-50"
    >
      <Icon
        name={isRetrying ? 'progress_activity' : 'refresh'}
        className={`text-[16px] ${isRetrying ? 'animate-spin' : ''}`}
      />
      {isRetrying ? 'Retrying…' : 'Retry'}
    </button>
  )
}

function FailedJobRow({
  job,
  state,
  onRetry,
  last,
}: {
  job: FailedJob
  state: RetryState
  onRetry: (job: FailedJob) => void
  last: boolean
}) {
  const borderClass = last ? '' : 'border-b border-ds-outline-variant/30'
  // Green right-to-left wash the moment the retry lands as succeeded.
  const sweepClass = state.status === 'succeeded' ? 'ds-success-sweep' : ''

  return (
    <tr className={`${borderClass} ${sweepClass}`.trim()}>
      <td className="px-6 py-4">
        <Link
          to={`/documents/${job.fileId}`}
          className="font-medium text-ds-on-surface transition-colors hover:text-ds-primary"
        >
          {job.filename ?? 'Untitled file'}
        </Link>
        <span className="ml-2 text-ds-secondary">v{job.version}</span>
      </td>
      <td className="px-6 py-4 text-ds-secondary">
        {/* `attempts` counts manual retries already spent (0 for a never-retried
            failure); the automatic Kafka path never increments it. Show it as-is. */}
        {job.attempts} / {job.maxRetries}
      </td>
      <td className="max-w-sm px-6 py-4 text-ds-secondary">
        <span className="line-clamp-2" title={job.error ?? undefined}>
          {job.error ?? '—'}
        </span>
      </td>
      <td className="px-6 py-4 text-ds-secondary">{formatDateTime(job.failedAt ?? undefined)}</td>
      <td className="px-6 py-4 text-right">
        <div className="flex flex-col items-end gap-1">
          <RetryButton job={job} state={state} onRetry={onRetry} />
          <RetryFeedback state={state} />
        </div>
      </td>
    </tr>
  )
}

const IDLE_STATE: RetryState = { status: 'idle' }

// Failed Processing: lists AI jobs that ended in FAILED and lets the user
// manually re-queue each one (the only path that moves a stuck job, since the
// Kafka auto-retry no-ops on already-FAILED jobs).
export default function FailedJobsPage() {
  const { jobs, hasError, retryStates, retry, reload } = useFailedJobs()

  return (
    <div className="flex flex-col gap-6">
      <section className="flex items-end justify-between">
        <div>
          <Link
            to="/"
            className="mb-2 inline-flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-secondary transition-colors hover:text-ds-primary"
          >
            <Icon name="arrow_back" className="text-[16px]" />
            Back to overview
          </Link>
          <h2 className="font-ds-heading text-ds-headline-md text-ds-on-surface">
            Failed Processing
          </h2>
          <p className="mt-1 font-ds-body text-ds-body-sm text-ds-secondary">
            Jobs whose AI analysis failed. Retry re-queues the file for processing.
          </p>
        </div>
        <button
          type="button"
          onClick={reload}
          className="inline-flex items-center gap-1.5 rounded-ds border border-ds-outline-variant bg-ds-surface-container px-3 py-1.5 font-ds-label text-ds-label-sm text-ds-on-surface transition-colors hover:bg-ds-surface-container-high"
        >
          <Icon name="refresh" className="text-[16px]" />
          Refresh
        </button>
      </section>

      <div className="overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-sm">
        {/* min-height keeps the card a consistent size whether it holds one row
            or a full page, so it never collapses to a thin strip. */}
        <div className="min-h-[28rem] overflow-x-auto">
          <table className="w-full border-collapse text-left text-ds-body-sm">
            <thead>
              <tr className="border-b border-ds-outline-variant/50 bg-ds-surface-bright">
                {HEADERS.map((header) => (
                  <th
                    key={header}
                    className="px-6 py-4 font-ds-label text-ds-label-md font-medium text-ds-on-surface-variant"
                  >
                    {header}
                  </th>
                ))}
                <th className="px-6 py-4 text-right font-ds-label text-ds-label-md font-medium text-ds-on-surface-variant">
                  Action
                </th>
              </tr>
            </thead>
            <tbody className="text-ds-on-surface">
              {jobs === null || hasError || jobs.length === 0 ? (
                <tr>
                  <td
                    colSpan={HEADERS.length + 1}
                    className="px-6 py-12 text-center text-ds-secondary"
                  >
                    {hasError ? (
                      <span className="inline-flex items-center gap-2 text-ds-error">
                        <Icon name="error" className="text-[18px]" />
                        Could not load failed jobs.
                      </span>
                    ) : jobs === null ? (
                      'Loading failed jobs…'
                    ) : (
                      <span className="inline-flex flex-col items-center gap-2">
                        <Icon name="check_circle" className="text-[40px] text-ds-tertiary" />
                        No failed jobs. Everything processed cleanly.
                      </span>
                    )}
                  </td>
                </tr>
              ) : (
                jobs.map((job, index) => (
                  <FailedJobRow
                    key={job.jobId}
                    job={job}
                    state={retryStates[job.jobId] ?? IDLE_STATE}
                    onRetry={retry}
                    last={index === jobs.length - 1}
                  />
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
