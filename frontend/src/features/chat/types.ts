// Presentation types for the RAG chat widget.

export type ChatRole = 'user' | 'assistant'

/** A document chunk cited under an assistant answer. */
export interface ChatSource {
  /** Backing file id (from the source metadata), used to link to its details. */
  fileId?: string
  /** Human-readable file name when present in the metadata. */
  filename?: string
  /** Short preview of the cited chunk. */
  preview: string
  /** Vector-search similarity score (0..1). */
  similarity: number
}

/** A single line in the chat transcript. */
export interface ChatMessage {
  id: string
  role: ChatRole
  text: string
  /** Cited sources for an assistant answer; omitted for user turns. */
  sources?: ChatSource[]
  /** Marks an assistant turn that failed, so the UI can style it as an error. */
  isError?: boolean
  /** Backend streamId for an assistant turn being streamed; correlates incoming frames. */
  streamId?: string
  /** True while the assistant turn is still streaming tokens. */
  isStreaming?: boolean
}
