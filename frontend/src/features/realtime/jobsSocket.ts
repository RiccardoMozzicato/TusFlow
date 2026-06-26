import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

// Single per-user STOMP-over-WebSocket connection to AIProcessingService (routed
// through the gateway at /api/v1/jobs/ws). AIProcessingService pushes a message per
// AI-job transition to the owning user's /user/queue/jobs destination — the two
// non-terminal progress pings (pending/processing) plus the terminal outcome — so a
// single subscription drives the live updates across Library, Dashboard and Failed
// Jobs — no per-file streams, no polling.
//
// SockJS is used because the servlet gateway does not proxy the raw WebSocket
// upgrade; it falls back to HTTP streaming transports, which the gateway proxies.

// Terminal job outcomes the backend persists and broadcasts (lower-cased jobStatus enum).
export type TerminalJobStatus = 'completed' | 'unsupported' | 'failed' | 'deadlettered'

// Non-terminal progress pings: the job was (re)queued, or analysis actively started. The
// persisted/aggregate status stays PENDING for both (PROCESSING is a live-only refinement),
// so the frontend counts 'processing' as a pending job — see JOB_STATUS_BY_UPDATE.
export type ProgressJobStatus = 'pending' | 'processing'

export type JobUpdateStatus = TerminalJobStatus | ProgressJobStatus

export interface JobUpdate {
  fileId: string
  version: number
  status: JobUpdateStatus
  /** Present for completed/unsupported: summary, tags, sensitivity, analysisStatus, … */
  metadata?: Record<string, unknown> | null
  /** Present for failed/deadlettered. */
  error?: string | null
}

// Maps the backend jobStatus enum name onto the frontend's lower-cased token.
const STATUS_BY_ENUM: Record<string, JobUpdateStatus> = {
  PENDING: 'pending',
  PROCESSING: 'processing',
  COMPLETED: 'completed',
  UNSUPPORTED: 'unsupported',
  FAILED: 'failed',
  DEAD_LETTERED: 'deadlettered',
}

// The statuses that end a job: they carry metadata/error and finish the timeline. Consumers
// that only act on the final outcome (upload queue, failed-jobs retry) gate on this so the new
// pending/processing pings don't trip their terminal handling.
const TERMINAL_STATUSES: ReadonlySet<JobUpdateStatus> = new Set<JobUpdateStatus>([
  'completed',
  'unsupported',
  'failed',
  'deadlettered',
])

export function isTerminalJobStatus(status: JobUpdateStatus): status is TerminalJobStatus {
  return TERMINAL_STATUSES.has(status)
}

type Listener = (update: JobUpdate) => void

const listeners = new Set<Listener>()
let client: Client | null = null

/**
 * Registers a listener for live job updates. Returns an unsubscribe function.
 * Listeners persist across reconnects; the underlying socket is shared.
 */
export function onJobUpdate(listener: Listener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

function parse(body: string): JobUpdate | null {
  try {
    const raw = JSON.parse(body) as {
      fileId?: string
      version?: number
      status?: string
      metadata?: Record<string, unknown> | null
      error?: string | null
    }
    if (!raw.fileId || raw.version == null || !raw.status) return null
    const status = STATUS_BY_ENUM[raw.status]
    if (!status) return null
    return {
      fileId: raw.fileId,
      version: raw.version,
      status,
      metadata: raw.metadata ?? null,
      error: raw.error ?? null,
    }
  } catch {
    // The backend always sends JSON, but never let a malformed frame throw.
    return null
  }
}

/**
 * Opens the shared connection if not already open. Idempotent: safe to call on
 * every authenticated mount. The browser sends the session cookie because the
 * SockJS transport is same-origin with the gateway.
 */
export function connectJobsSocket(): void {
  if (client) return

  const next = new Client({
    webSocketFactory: () => new SockJS('/api/v1/jobs/ws'),
    reconnectDelay: 5000,
    onConnect: () => {
      next.subscribe('/user/queue/jobs', (message: IMessage) => {
        const update = parse(message.body)
        if (update) listeners.forEach((listener) => listener(update))
      })
    },
  })
  client = next
  next.activate()
}

/** Tears the shared connection down. Listeners are left registered for reconnect. */
export function disconnectJobsSocket(): void {
  const current = client
  client = null
  void current?.deactivate()
}
