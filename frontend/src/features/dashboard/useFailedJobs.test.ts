import { AxiosError, AxiosHeaders } from 'axios'
import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useFailedJobs } from './useFailedJobs'
import * as jobsApi from '@/features/documents/jobsApi'
import * as jobsSocket from '@/features/realtime/jobsSocket'
import type { FailedJob } from '@/features/documents/jobsApi'
import type { JobUpdate, JobUpdateStatus } from '@/features/realtime/jobsSocket'

vi.mock('@/features/documents/jobsApi', () => ({
  getFailedJobs: vi.fn(),
  retryJob: vi.fn(),
}))

vi.mock('@/features/realtime/jobsSocket', () => ({
  onJobUpdate: vi.fn(),
}))

const getFailedJobsMock = vi.mocked(jobsApi.getFailedJobs)
const retryJobMock = vi.mocked(jobsApi.retryJob)
const onJobUpdateMock = vi.mocked(jobsSocket.onJobUpdate)

const JOB: FailedJob = {
  jobId: 'job-1',
  fileId: 'file-1',
  version: 2,
  filename: 'report.pdf',
  attempts: 1,
  maxRetries: 4,
  canRetry: true,
  error: 'boom',
  failedAt: '2026-06-05T10:00:00Z',
}

// Captures the listener the hook registers on the jobs socket so a test can drive a
// terminal update for JOB's file+version, and exposes the unsubscribe spy.
function captureListener() {
  const unsubscribe = vi.fn()
  let listener: ((update: JobUpdate) => void) | undefined
  onJobUpdateMock.mockImplementation((cb) => {
    listener = cb
    return unsubscribe
  })
  return {
    unsubscribe,
    emit: (status: JobUpdateStatus) =>
      act(() =>
        listener?.({ fileId: JOB.fileId, version: JOB.version, status, metadata: null, error: null }),
      ),
  }
}

// Builds an AxiosError carrying a given HTTP status, the shape retryJob rejects with.
function axiosErrorWithStatus(status: number): AxiosError {
  const error = new AxiosError('failed')
  error.response = {
    status,
    statusText: '',
    data: null,
    headers: {},
    config: { headers: new AxiosHeaders() },
  }
  return error
}

describe('useFailedJobs', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getFailedJobsMock.mockResolvedValue([JOB])
    retryJobMock.mockResolvedValue(undefined)
    onJobUpdateMock.mockReturnValue(vi.fn())
  })

  it('loads failed jobs on mount', async () => {
    const { result } = renderHook(() => useFailedJobs())
    await waitFor(() => expect(result.current.jobs).toEqual([JOB]))
    expect(result.current.hasError).toBe(false)
  })

  it('sets hasError when the load fails', async () => {
    getFailedJobsMock.mockRejectedValue(new Error('nope'))
    const { result } = renderHook(() => useFailedJobs())
    await waitFor(() => expect(result.current.hasError).toBe(true))
    expect(result.current.jobs).toBeNull()
  })

  it('marks a job reprocessing and listens on the jobs socket after a successful retry', async () => {
    const { result } = renderHook(() => useFailedJobs())
    await waitFor(() => expect(result.current.jobs).toEqual([JOB]))

    act(() => result.current.retry(JOB))

    await waitFor(() =>
      expect(result.current.retryStates['job-1']?.status).toBe('reprocessing'),
    )
    expect(retryJobMock).toHaveBeenCalledWith({ fileId: 'file-1', version: 2 })
    expect(onJobUpdateMock).toHaveBeenCalledWith(expect.any(Function))
  })

  it('ignores updates for a different file or version', async () => {
    let listener: ((update: JobUpdate) => void) | undefined
    const unsubscribe = vi.fn()
    onJobUpdateMock.mockImplementation((cb) => {
      listener = cb
      return unsubscribe
    })
    const { result } = renderHook(() => useFailedJobs())
    await waitFor(() => expect(result.current.jobs).toEqual([JOB]))

    act(() => result.current.retry(JOB))
    await waitFor(() =>
      expect(result.current.retryStates['job-1']?.status).toBe('reprocessing'),
    )

    act(() =>
      listener?.({ fileId: 'other', version: 99, status: 'completed', metadata: null, error: null }),
    )

    // Still reprocessing — the update was for a different file.
    expect(result.current.retryStates['job-1']?.status).toBe('reprocessing')
    expect(unsubscribe).not.toHaveBeenCalled()
  })

  it('transitions reprocessing → succeeded on a completed update', async () => {
    const socket = captureListener()
    const { result } = renderHook(() => useFailedJobs())
    await waitFor(() => expect(result.current.jobs).toEqual([JOB]))

    act(() => result.current.retry(JOB))
    await waitFor(() =>
      expect(result.current.retryStates['job-1']?.status).toBe('reprocessing'),
    )

    socket.emit('completed')

    expect(result.current.retryStates['job-1']?.status).toBe('succeeded')
    expect(socket.unsubscribe).toHaveBeenCalled()
  })

  it('keeps a succeeded job in the list so the user can still interact with it', async () => {
    // shouldAdvanceTime lets the mount fetch's microtasks resolve normally while
    // any timers (there should be none on success) are under manual control.
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      const socket = captureListener()
      const { result } = renderHook(() => useFailedJobs())
      await waitFor(() => expect(result.current.jobs).toEqual([JOB]))

      act(() => result.current.retry(JOB))
      await waitFor(() =>
        expect(result.current.retryStates['job-1']?.status).toBe('reprocessing'),
      )

      socket.emit('completed')
      expect(result.current.retryStates['job-1']?.status).toBe('succeeded')

      // No auto-removal: the row and its 'succeeded' state persist indefinitely
      // (the row clears only on the next Refresh / reload).
      act(() => vi.advanceTimersByTime(10000))

      expect(result.current.jobs).toEqual([JOB])
      expect(result.current.retryStates['job-1']?.status).toBe('succeeded')
    } finally {
      vi.useRealTimers()
    }
  })

  it('transitions reprocessing → failed on a failed update', async () => {
    const socket = captureListener()
    const { result } = renderHook(() => useFailedJobs())
    await waitFor(() => expect(result.current.jobs).toEqual([JOB]))

    act(() => result.current.retry(JOB))
    await waitFor(() =>
      expect(result.current.retryStates['job-1']?.status).toBe('reprocessing'),
    )

    socket.emit('failed')

    expect(result.current.retryStates['job-1']?.status).toBe('failed')
    expect(result.current.retryStates['job-1']?.message).toBe('Processing failed again.')
  })

  it("increments the row's attempts on a failed update", async () => {
    const socket = captureListener()
    const { result } = renderHook(() => useFailedJobs())
    await waitFor(() => expect(result.current.jobs).toEqual([JOB]))

    act(() => result.current.retry(JOB))
    await waitFor(() =>
      expect(result.current.retryStates['job-1']?.status).toBe('reprocessing'),
    )

    socket.emit('failed')

    expect(result.current.jobs?.[0].attempts).toBe(2)
    expect(result.current.jobs?.[0].canRetry).toBe(true)
  })

  it('disables further retries when the bumped attempt reaches the cap', async () => {
    // attempts 3 of maxRetries 4 — one more failure exhausts the budget.
    getFailedJobsMock.mockResolvedValue([{ ...JOB, attempts: 3 }])
    const socket = captureListener()
    const { result } = renderHook(() => useFailedJobs())
    await waitFor(() => expect(result.current.jobs?.[0].attempts).toBe(3))

    act(() => result.current.retry(JOB))
    await waitFor(() =>
      expect(result.current.retryStates['job-1']?.status).toBe('reprocessing'),
    )

    socket.emit('failed')

    expect(result.current.jobs?.[0].attempts).toBe(4)
    expect(result.current.jobs?.[0].canRetry).toBe(false)
  })

  it('marks a job deadlettered and auto-drops it after the dismiss delay', async () => {
    // A dead-letter only happens once the final allowed retry is spent, so start
    // at the cap-minus-one (attempts 3 of 4).
    const atCap = { ...JOB, attempts: 3 }
    getFailedJobsMock.mockResolvedValue([atCap])
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      const socket = captureListener()
      const { result } = renderHook(() => useFailedJobs())
      await waitFor(() => expect(result.current.jobs?.[0].attempts).toBe(3))

      act(() => result.current.retry(atCap))
      await waitFor(() =>
        expect(result.current.retryStates['job-1']?.status).toBe('reprocessing'),
      )

      socket.emit('deadlettered')

      // Terminal state, attempts bumped to the cap, row still visible briefly.
      expect(result.current.retryStates['job-1']?.status).toBe('deadlettered')
      expect(result.current.jobs?.[0].attempts).toBe(4)
      expect(result.current.jobs?.[0].canRetry).toBe(false)

      act(() => vi.advanceTimersByTime(4000))

      expect(result.current.jobs).toEqual([])
      expect(result.current.retryStates['job-1']).toBeUndefined()
    } finally {
      vi.useRealTimers()
    }
  })

  it('bumps attempts on an unsupported update too', async () => {
    const socket = captureListener()
    const { result } = renderHook(() => useFailedJobs())
    await waitFor(() => expect(result.current.jobs).toEqual([JOB]))

    act(() => result.current.retry(JOB))
    await waitFor(() =>
      expect(result.current.retryStates['job-1']?.status).toBe('reprocessing'),
    )

    socket.emit('unsupported')

    expect(result.current.retryStates['job-1']?.status).toBe('failed')
    expect(result.current.jobs?.[0].attempts).toBe(2)
  })

  it('unsubscribes from the jobs socket on unmount', async () => {
    const socket = captureListener()
    const { result, unmount } = renderHook(() => useFailedJobs())
    await waitFor(() => expect(result.current.jobs).toEqual([JOB]))

    act(() => result.current.retry(JOB))
    await waitFor(() =>
      expect(result.current.retryStates['job-1']?.status).toBe('reprocessing'),
    )

    unmount()

    expect(socket.unsubscribe).toHaveBeenCalled()
  })

  it('maps the 403 retry-cap response to a friendly message', async () => {
    retryJobMock.mockRejectedValue(axiosErrorWithStatus(403))
    const { result } = renderHook(() => useFailedJobs())
    await waitFor(() => expect(result.current.jobs).toEqual([JOB]))

    act(() => result.current.retry(JOB))

    await waitFor(() =>
      expect(result.current.retryStates['job-1']?.status).toBe('error'),
    )
    expect(result.current.retryStates['job-1']?.message).toBe(
      'Retry limit reached for this file.',
    )
  })

  it('maps the 409 not-failed response to a friendly message', async () => {
    retryJobMock.mockRejectedValue(axiosErrorWithStatus(409))
    const { result } = renderHook(() => useFailedJobs())
    await waitFor(() => expect(result.current.jobs).toEqual([JOB]))

    act(() => result.current.retry(JOB))

    await waitFor(() =>
      expect(result.current.retryStates['job-1']?.message).toBe(
        'Job is no longer in a failed state.',
      ),
    )
  })
})
