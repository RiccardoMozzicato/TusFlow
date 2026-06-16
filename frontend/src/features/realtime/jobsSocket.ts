import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

// Single per-user STOMP-over-WebSocket connection to AIProcessingService (routed
// through the gateway at /api/v1/jobs/ws). AIProcessingService pushes one message
// per terminal AI-job transition to the owning user's /user/queue/jobs destination,
// so a single subscription drives the live updates across Library, Dashboard and
// Failed Jobs — no per-file streams, no polling.
//
// SockJS is used because the servlet gateway does not proxy the raw WebSocket
// upgrade; it falls back to HTTP streaming transports, which the gateway proxies.

// Terminal job outcomes the backend can emit (lower-cased from the jobStatus enum).
export type JobUpdateStatus = 'completed' | 'unsupported' | 'failed' | 'deadlettered'

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
  COMPLETED: 'completed',
  UNSUPPORTED: 'unsupported',
  FAILED: 'failed',
  DEAD_LETTERED: 'deadlettered',
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
