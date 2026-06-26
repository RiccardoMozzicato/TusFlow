import { useCallback, useEffect, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { getFailedJobs, retryJob, type FailedJob } from '@/features/documents/jobsApi'
import { isTerminalJobStatus, onJobUpdate, type TerminalJobStatus } from '@/features/realtime/jobsSocket'

// Per-job retry lifecycle, tracked separately from the list so a retry never
// mutates the loaded jobs in place.
//   retrying     – the retry POST is in flight
//   reprocessing – queued (202); waiting on the jobs socket for the outcome
//   succeeded    – the AI job finished cleanly (socket 'completed')
//   failed       – the AI job failed again (socket 'failed' / 'unsupported')
//   deadlettered – retries exhausted; the job was permanently given up (socket 'deadlettered')
//   error        – the retry POST itself was rejected (guard responses)
export type RetryStatus =
  | 'idle'
  | 'retrying'
  | 'reprocessing'
  | 'succeeded'
  | 'failed'
  | 'deadlettered'
  | 'error'

export interface RetryState {
  status: RetryStatus
  /** User-facing message when status is 'error', 'failed', or 'deadlettered'. */
  message?: string
}

// A dead-lettered row lingers a few seconds so the user can read the terminal
// "retries exhausted" message before it drops (it has left the FAILED list).
const DEADLETTER_DISMISS_MS = 4000

export interface FailedJobsData {
  /** Null while loading or when the initial fetch failed. */
  jobs: FailedJob[] | null
  hasError: boolean
  /** Retry state keyed by jobId; absent entries are 'idle'. */
  retryStates: Record<string, RetryState>
  retry: (job: FailedJob) => void
  reload: () => void
}

// Maps the backend's retry guard responses to friendly copy.
function retryErrorMessage(error: unknown): string {
  if (isAxiosError(error)) {
    switch (error.response?.status) {
      case 403:
        return 'Retry limit reached for this file.'
      case 409:
        return 'Job is no longer in a failed state.'
      case 404:
        return 'Job no longer exists.'
    }
  }
  return 'Could not start the retry. Please try again.'
}

// Translates a terminal job status into the row's RetryState.
function retryStateFromStatus(status: TerminalJobStatus): RetryState {
  switch (status) {
    case 'completed':
      return { status: 'succeeded' }
    case 'failed':
      return { status: 'failed', message: 'Processing failed again.' }
    case 'unsupported':
      return { status: 'failed', message: 'This file type is not supported.' }
    case 'deadlettered':
      return {
        status: 'deadlettered',
        message: "All retries exhausted — this file won't be retried again.",
      }
  }
}

/**
 * Loads the list of FAILED jobs and exposes a per-job manual retry that calls
 * the AIProcessingService retry endpoint. After a successful retry the row is
 * marked 'reprocessing' and a listener on the shared jobs socket waits for this
 * file's next terminal outcome to update the row to 'succeeded' or 'failed' as
 * soon as the job ends — no polling, no manual refresh.
 *
 * When that update resolves to 'succeeded' the row stays in place showing the
 * "Reprocessed successfully" confirmation (with the file link still clickable),
 * so the user can act on it; it clears on the next Refresh, since the job has
 * left the FAILED set. When it resolves to 'failed' again, the row's attempts
 * bump by one in place (mirroring the backend's per-retry increment), disabling
 * Retry once the cap is reached. If it resolves to 'deadlettered' (retries
 * exhausted), the row shows a terminal "given up" state and auto-drops after a
 * few seconds, since the job has left the FAILED set.
 */
export function useFailedJobs(): FailedJobsData {
  const [jobs, setJobs] = useState<FailedJob[] | null>(null)
  const [hasError, setHasError] = useState(false)
  const [retryStates, setRetryStates] = useState<Record<string, RetryState>>({})
  const [reloadToken, setReloadToken] = useState(0)

  // Jobs-socket listener unsubscribers keyed by jobId, the pending success-removal
  // timers, plus a mounted flag so async callbacks never touch state after unmount.
  const closersRef = useRef<Map<string, () => void>>(new Map())
  const removalTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map())
  const isMountedRef = useRef(true)

  const closeStream = useCallback((jobId: string) => {
    const close = closersRef.current.get(jobId)
    if (close) {
      close()
      closersRef.current.delete(jobId)
    }
  }, [])

  // Reflects the backend's per-retry attempt increment on the row in place. The
  // retry POST bumps `attempts` by exactly one (RetryJobService) before
  // reprocessing, so on a re-failed outcome we mirror +1 and recompute canRetry.
  const bumpAttempts = useCallback((jobId: string) => {
    setJobs((prev) =>
      prev?.map((job) => {
        if (job.jobId !== jobId) return job
        const attempts = job.attempts + 1
        return { ...job, attempts, canRetry: attempts < job.maxRetries }
      }) ?? prev,
    )
  }, [])

  // Drops a job from the list (and its retry state) after `delayMs`, keeping the
  // list in sync without a manual reload — used for a dead-lettered outcome,
  // which has left the FAILED set after its terminal message has been shown.
  const scheduleRemoval = useCallback((jobId: string, delayMs: number) => {
    const timer = setTimeout(() => {
      if (!isMountedRef.current) return
      removalTimersRef.current.delete(jobId)
      setJobs((prev) => prev?.filter((job) => job.jobId !== jobId) ?? prev)
      setRetryStates((prev) => {
        if (!(jobId in prev)) return prev
        const { [jobId]: _removed, ...rest } = prev
        return rest
      })
    }, delayMs)
    removalTimersRef.current.set(jobId, timer)
  }, [])

  useEffect(() => {
    isMountedRef.current = true
    const closers = closersRef.current
    const timers = removalTimersRef.current
    return () => {
      isMountedRef.current = false
      closers.forEach((close) => close())
      closers.clear()
      timers.forEach((timer) => clearTimeout(timer))
      timers.clear()
    }
  }, [])

  useEffect(() => {
    let isActive = true
    setJobs(null)
    setHasError(false)

    getFailedJobs()
      .then((data) => {
        if (!isActive) return
        setJobs(data)
        setRetryStates({})
      })
      .catch(() => {
        if (!isActive) return
        setJobs(null)
        setHasError(true)
      })

    return () => {
      isActive = false
    }
  }, [reloadToken])

  const reload = useCallback(() => {
    // Drop any open socket listeners and pending removals; the fresh list re-establishes truth.
    closersRef.current.forEach((close) => close())
    closersRef.current.clear()
    removalTimersRef.current.forEach((timer) => clearTimeout(timer))
    removalTimersRef.current.clear()
    setReloadToken((token) => token + 1)
  }, [])

  const retry = useCallback(
    (job: FailedJob) => {
      closeStream(job.jobId)
      setRetryStates((prev) => ({ ...prev, [job.jobId]: { status: 'retrying' } }))

      retryJob({ fileId: job.fileId, version: job.version })
        .then(() => {
          if (!isMountedRef.current) return
          setRetryStates((prev) => ({ ...prev, [job.jobId]: { status: 'reprocessing' } }))

          // Listen on the shared jobs socket for this file+version's next terminal
          // outcome. The retry already cleared the stale failure on the backend, so
          // the very next update for this key is the retry's result.
          const unsubscribe = onJobUpdate((update) => {
            if (!isMountedRef.current) return
            if (update.fileId !== job.fileId || update.version !== job.version) return
            // Ignore the non-terminal pending/processing pings: the row stays
            // 'reprocessing' until this retry reaches its terminal outcome.
            if (!isTerminalJobStatus(update.status)) return
            const next = retryStateFromStatus(update.status)
            closeStream(job.jobId)
            setRetryStates((prev) => ({ ...prev, [job.jobId]: next }))
            // A succeeded job has left the FAILED set, but the row stays so the
            // user can read the confirmation and follow the file link; it clears
            // on the next Refresh.
            // A re-failed retry consumed one attempt; bump the row live.
            if (next.status === 'failed') bumpAttempts(job.jobId)
            // Dead-lettered: the final attempt was spent and the job is gone
            // from the FAILED set — bump to the cap, then drop after a beat.
            if (next.status === 'deadlettered') {
              bumpAttempts(job.jobId)
              scheduleRemoval(job.jobId, DEADLETTER_DISMISS_MS)
            }
          })
          closersRef.current.set(job.jobId, unsubscribe)
        })
        .catch((error) => {
          if (!isMountedRef.current) return
          setRetryStates((prev) => ({
            ...prev,
            [job.jobId]: { status: 'error', message: retryErrorMessage(error) },
          }))
        })
    },
    [closeStream, scheduleRemoval, bumpAttempts],
  )

  return { jobs, hasError, retryStates, retry, reload }
}
