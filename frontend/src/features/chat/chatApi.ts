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

// Shape returned by POST /api/rag/query (mirrors the backend RagQueryResponse).
export interface RagQueryResponse {
  query: string
  answer: string
  sourcesCount: number
  sources: RagSource[]
  accessibleFilesCount: number
}

const DEFAULT_TOP_K = 5

/**
 * Asks the RAG endpoint a question, scoped to the files the given user can access.
 * The backend requires the caller's id in `filters.userId`; auth + CSRF are carried
 * by the shared axios client (session cookie + X-XSRF-TOKEN).
 */
export async function queryRag(
  query: string,
  userId: string,
  topK: number = DEFAULT_TOP_K,
): Promise<RagQueryResponse> {
  const { data } = await http.post<RagQueryResponse>(`${API.rag}/query`, {
    query,
    filters: { userId },
    topK,
  })
  return data
}
