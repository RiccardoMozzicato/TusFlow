import { createAsyncThunk, createSlice, type PayloadAction } from '@reduxjs/toolkit'
import type { RootState } from '@/app/store'
import {
  queryRag,
  streamRag,
  type RagQueryResponse,
  type RagSource,
  type RagStreamHandle,
} from './chatApi'
import type { ChatMessage, ChatSource } from './types'

type LoadStatus = 'idle' | 'loading'

interface ChatState {
  isOpen: boolean
  messages: ChatMessage[]
  status: LoadStatus
  error: string | null
  /** Monotonic counter for stable, serializable message ids. */
  seq: number
  /**
   * Client-minted conversation id sent on every turn so the backend keeps chat memory.
   * Reset on `clearChat` (and naturally on reload) to start a fresh, ephemeral conversation.
   */
  conversationId: string
}

const initialState: ChatState = {
  isOpen: false,
  messages: [],
  status: 'idle',
  error: null,
  seq: 0,
  conversationId: crypto.randomUUID(),
}

const STREAM_ERROR_FALLBACK = "Sorry, I couldn't answer that. Please try again."

// Reads the optional fileId/filename keys out of a source's open metadata map.
function mapSource(source: RagSource): ChatSource {
  const metadata = source.metadata ?? {}
  return {
    fileId: typeof metadata.fileId === 'string' ? metadata.fileId : undefined,
    filename: typeof metadata.filename === 'string' ? metadata.filename : undefined,
    preview: source.contentPreview,
    similarity: source.similarity,
  }
}

// Finds the assistant turn for a stream, creating an empty placeholder if frames arrive before the
// POST response (or vice versa). Idempotent on streamId.
function ensureAssistant(state: ChatState, streamId: string): ChatMessage {
  const existing = state.messages.find((m) => m.streamId === streamId)
  if (existing) return existing
  state.messages.push({ id: `a${state.seq}`, role: 'assistant', text: '', streamId, isStreaming: true })
  state.seq += 1
  return state.messages[state.messages.length - 1]
}

// Non-streaming ask (kept as a fallback). The user turn is appended in `pending`, the assistant turn
// in `fulfilled`/`rejected`.
export const askRag = createAsyncThunk<
  RagQueryResponse,
  { query: string },
  { state: RootState }
>('chat/askRag', async ({ query }, { getState }) =>
  queryRag(query, getState().chat.conversationId),
)

// Streaming ask: POST returns a streamId; the answer then arrives as frames over the rag socket,
// dispatched into the stream* reducers below.
export const streamMessage = createAsyncThunk<
  RagStreamHandle,
  { query: string },
  { state: RootState }
>('chat/streamMessage', async ({ query }, { getState }) =>
  streamRag(query, getState().chat.conversationId),
)

const chatSlice = createSlice({
  name: 'chat',
  initialState,
  reducers: {
    openChat(state) {
      state.isOpen = true
    },
    closeChat(state) {
      state.isOpen = false
    },
    toggleChat(state) {
      state.isOpen = !state.isOpen
    },
    // Clearing the transcript also starts a new conversation (fresh memory thread + Langfuse
    // session). The new id is generated in `prepare` to keep the reducer pure.
    clearChat: {
      reducer(state, action: PayloadAction<string>) {
        state.messages = []
        state.error = null
        state.conversationId = action.payload
      },
      prepare: () => ({ payload: crypto.randomUUID() }),
    },
    // --- streaming frames (dispatched by the rag socket listener) ---
    streamSources(state, action: PayloadAction<{ streamId: string; sources: RagSource[] }>) {
      ensureAssistant(state, action.payload.streamId).sources = action.payload.sources.map(mapSource)
    },
    streamToken(state, action: PayloadAction<{ streamId: string; token: string }>) {
      ensureAssistant(state, action.payload.streamId).text += action.payload.token
    },
    streamDone(state, action: PayloadAction<{ streamId: string }>) {
      const message = state.messages.find((m) => m.streamId === action.payload.streamId)
      if (message) message.isStreaming = false
      state.status = 'idle'
    },
    streamError(state, action: PayloadAction<{ streamId: string; error?: string }>) {
      const message = ensureAssistant(state, action.payload.streamId)
      if (!message.text) message.text = STREAM_ERROR_FALLBACK
      message.isError = true
      message.isStreaming = false
      state.status = 'idle'
      state.error = action.payload.error ?? 'Stream failed'
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(askRag.pending, (state, action) => {
        state.status = 'loading'
        state.error = null
        state.messages.push({ id: `u${state.seq}`, role: 'user', text: action.meta.arg.query })
        state.seq += 1
      })
      .addCase(askRag.fulfilled, (state, action) => {
        state.status = 'idle'
        state.messages.push({
          id: `a${state.seq}`,
          role: 'assistant',
          text: action.payload.answer,
          sources: action.payload.sources.map(mapSource),
        })
        state.seq += 1
      })
      .addCase(askRag.rejected, (state, action) => {
        state.status = 'idle'
        state.error = action.error.message ?? 'Request failed'
        state.messages.push({
          id: `a${state.seq}`,
          role: 'assistant',
          text: STREAM_ERROR_FALLBACK,
          isError: true,
        })
        state.seq += 1
      })
      // Streaming: append the user turn now; the assistant turn fills in from socket frames.
      .addCase(streamMessage.pending, (state, action) => {
        state.status = 'loading'
        state.error = null
        state.messages.push({ id: `u${state.seq}`, role: 'user', text: action.meta.arg.query })
        state.seq += 1
      })
      .addCase(streamMessage.fulfilled, (state, action) => {
        // Bind an assistant placeholder to the streamId (frames may have arrived already).
        ensureAssistant(state, action.payload.streamId)
      })
      .addCase(streamMessage.rejected, (state, action) => {
        state.status = 'idle'
        state.error = action.error.message ?? 'Request failed'
        state.messages.push({
          id: `a${state.seq}`,
          role: 'assistant',
          text: STREAM_ERROR_FALLBACK,
          isError: true,
        })
        state.seq += 1
      })
  },
})

export default chatSlice.reducer
export const {
  openChat,
  closeChat,
  toggleChat,
  clearChat,
  streamSources,
  streamToken,
  streamDone,
  streamError,
} = chatSlice.actions

export const selectChatOpen = (state: RootState) => state.chat.isOpen
export const selectChatMessages = (state: RootState) => state.chat.messages
export const selectChatStatus = (state: RootState) => state.chat.status
export const selectChatStreaming = (state: RootState) =>
  state.chat.messages.some((m) => m.isStreaming)
