import { http } from '@/api/axios'
import { API } from '@/config'

// One retrieved document chunk backing a RAG answer (mirrors the backend
// DocumentSource). `metadata` is an open map; we read `fileId`/`filename` from it.
export interface RagSource {
  content: string
  contentPreview: string
  metadata: Record<string, unknown>
  similarity: number
}

const DEFAULT_TOP_K = 5

/** Returned by the streaming endpoint; frames then arrive over the rag socket keyed by this id. */
export interface RagStreamHandle {
  streamId: string
}

/**
 * Starts a streamed RAG answer, scoped to the files the caller can access. Returns a streamId
 * immediately; the answer then arrives as frames on the rag socket (`/user/queue/rag`).
 * `conversationId` groups turns into one chat-memory thread (and one Langfuse session). Auth + CSRF
 * are carried by the shared axios client (session cookie + X-XSRF-TOKEN); the backend takes the
 * user id from the JWT.
 */
export async function streamRag(
  query: string,
  conversationId: string,
  topK: number = DEFAULT_TOP_K,
): Promise<RagStreamHandle> {
  const { data } = await http.post<RagStreamHandle>(`${API.rag}/query/stream`, {
    query,
    conversationId,
    topK,
  })
  return data
}
