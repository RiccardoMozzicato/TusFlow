import { http } from '@/api/axios'
import { API } from '@/config'

// AI-job lifecycle status, mirroring the backend `jobStatus` enum. Const object
// (not a TS enum) so it erases cleanly under `verbatimModuleSyntax`.
export const JobStatus = {
  PENDING: 'PENDING',
  PROCESSING: 'PROCESSING',
  COMPLETED: 'COMPLETED',
  FAILED: 'FAILED',
  UNSUPPORTED: 'UNSUPPORTED',
} as const

export type JobStatus = (typeof JobStatus)[keyof typeof JobStatus]

/** A (fileId, version) pair identifying the AI job to look up. */
export interface JobKey {
  fileId: string
  version: number
}

// Shape returned by POST /api/v1/jobs/status/batch (mirrors JobStatusResponseDTO).
export interface JobStatusResponse {
  jobId: string
  fileId: string
  version: number
  status: JobStatus
  payload?: Record<string, unknown> | null
}

/**
 * Fetches AI-job statuses for many files in one call. Pairs without a job are
 * omitted from the response. Returns [] for an empty request without a round-trip.
 */
export async function getJobStatuses(keys: JobKey[]): Promise<JobStatusResponse[]> {
  if (keys.length === 0) return []
  const { data } = await http.post<JobStatusResponse[]>(`${API.jobs}/status/batch`, keys)
  return data
}

// A FAILED job surfaced by GET /api/v1/jobs/failed (mirrors FailedJobDTO).
export interface FailedJob {
  jobId: string
  fileId: string
  version: number
  filename: string | null
  attempts: number
  maxRetries: number
  /** False once the user-retry cap is exhausted. */
  canRetry: boolean
  error: string | null
  /** ISO instant of the last status transition (the job's updated_at). */
  failedAt: string | null
}

// Spring Data Page envelope returned by GET /api/v1/jobs/failed.
interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  last: boolean
}

// Default page size; large enough to surface the dashboard's failed jobs in one
// request while keeping the backend query bounded (server caps at 200).
const FAILED_JOBS_PAGE_SIZE = 100

/** Lists currently FAILED AI jobs, newest first (paginated, server-capped). */
export async function getFailedJobs(): Promise<FailedJob[]> {
  const { data } = await http.get<Page<FailedJob>>(`${API.jobs}/failed`, {
    params: { page: 0, size: FAILED_JOBS_PAGE_SIZE },
  })
  return data.content
}

/**
 * Triggers a manual retry of a FAILED job. The backend resets it to PENDING and
 * reprocesses asynchronously (202 Accepted). Throws on the server's guard
 * responses: 409 (job no longer FAILED), 403 (retry cap reached), 404 (no job).
 */
export async function retryJob(key: JobKey): Promise<void> {
  await http.post(API.jobs, null, {
    params: { fileId: key.fileId, version: key.version },
  })
}
