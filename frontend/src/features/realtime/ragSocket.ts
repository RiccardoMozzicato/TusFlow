import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { RagSource } from '@/features/chat/chatApi'

// Per-user STOMP-over-WebSocket stream of RAG answer frames. AIProcessingService pushes one message
// per frame to the user's /user/queue/rag destination (the same gateway endpoint as job status), so
// a streamed answer arrives as: one SOURCES frame, then TOKEN frames, then a terminal DONE/ERROR —
// all correlated by streamId. SockJS is used because the servlet gateway can't proxy a raw WebSocket
// upgrade; it falls back to HTTP streaming transports the gateway can relay.

export type RagStreamType = 'SOURCES' | 'TOKEN' | 'DONE' | 'ERROR'

export interface RagStreamEvent {
  streamId: string
  type: RagStreamType
  token?: string | null
  sources?: RagSource[] | null
  error?: string | null
}

type Listener = (event: RagStreamEvent) => void

const listeners = new Set<Listener>()
let client: Client | null = null

/** Registers a listener for RAG stream frames. Returns an unsubscribe function. */
export function onRagEvent(listener: Listener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

function parse(body: string): RagStreamEvent | null {
  try {
    const raw = JSON.parse(body) as Partial<RagStreamEvent>
    if (!raw.streamId || !raw.type) return null
    return {
      streamId: raw.streamId,
      type: raw.type,
      token: raw.token ?? null,
      sources: raw.sources ?? null,
      error: raw.error ?? null,
    }
  } catch {
    // The backend always sends JSON, but never let a malformed frame throw.
    return null
  }
}

/** Opens the shared connection if not already open. Idempotent. */
export function connectRagSocket(): void {
  if (client) return

  const next = new Client({
    webSocketFactory: () => new SockJS('/api/v1/jobs/ws'),
    reconnectDelay: 5000,
    onConnect: () => {
      next.subscribe('/user/queue/rag', (message: IMessage) => {
        const event = parse(message.body)
        if (event) listeners.forEach((listener) => listener(event))
      })
    },
  })
  client = next
  next.activate()
}

/** Tears the shared connection down. Listeners are left registered for reconnect. */
export function disconnectRagSocket(): void {
  const current = client
  client = null
  void current?.deactivate()
}
