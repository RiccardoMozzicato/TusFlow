import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import type { RootState } from '@/app/store'
import { queryRag, type RagQueryResponse, type RagSource } from './chatApi'
import type { ChatMessage, ChatSource } from './types'

type LoadStatus = 'idle' | 'loading'

interface ChatState {
  isOpen: boolean
  messages: ChatMessage[]
  status: LoadStatus
  error: string | null
  /** Monotonic counter for stable, serializable message ids. */
  seq: number
}

const initialState: ChatState = {
  isOpen: false,
  messages: [],
  status: 'idle',
  error: null,
  seq: 0,
}

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

// Sends a question to the RAG endpoint. The user turn is appended in `pending`
// (from the thunk arg) and the assistant turn in `fulfilled`/`rejected`.
export const askRag = createAsyncThunk<
  RagQueryResponse,
  { query: string; userId: string }
>('chat/askRag', async ({ query, userId }) => queryRag(query, userId))

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
    clearChat(state) {
      state.messages = []
      state.error = null
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(askRag.pending, (state, action) => {
        state.status = 'loading'
        state.error = null
        state.messages.push({
          id: `u${state.seq}`,
          role: 'user',
          text: action.meta.arg.query,
        })
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
          text: "Sorry, I couldn't answer that. Please try again.",
          isError: true,
        })
        state.seq += 1
      })
  },
})

export default chatSlice.reducer
export const { openChat, closeChat, toggleChat, clearChat } = chatSlice.actions

export const selectChatOpen = (state: RootState) => state.chat.isOpen
export const selectChatMessages = (state: RootState) => state.chat.messages
export const selectChatStatus = (state: RootState) => state.chat.status
