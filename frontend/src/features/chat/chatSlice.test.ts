import { describe, expect, it } from 'vitest'
import chatReducer, {
  clearChat,
  closeChat,
  openChat,
  streamDone,
  streamError,
  streamMessage,
  streamSources,
  streamToken,
  toggleChat,
} from './chatSlice'

const initial = () => chatReducer(undefined, { type: '@@INIT' })

describe('chatSlice reducers', () => {
  it('opens, closes, and toggles the panel', () => {
    let state = chatReducer(initial(), openChat())
    expect(state.isOpen).toBe(true)
    state = chatReducer(state, closeChat())
    expect(state.isOpen).toBe(false)
    state = chatReducer(state, toggleChat())
    expect(state.isOpen).toBe(true)
  })

  it('clears the transcript and error', () => {
    let state = chatReducer(initial(), streamMessage.pending('', { query: 'hi' }))
    expect(state.messages).toHaveLength(1)
    state = chatReducer(state, clearChat())
    expect(state.messages).toHaveLength(0)
    expect(state.error).toBeNull()
  })
})

describe('streaming lifecycle', () => {
  const arg = { query: 'Summarize the report' }
  const streamId = 's-1'

  it('appends the user turn on stream pending', () => {
    const state = chatReducer(initial(), streamMessage.pending('', arg))
    expect(state.status).toBe('loading')
    expect(state.messages).toEqual([expect.objectContaining({ role: 'user', text: arg.query })])
  })

  it('accumulates tokens and sources into one assistant turn, then completes', () => {
    let state = chatReducer(initial(), streamMessage.pending('', arg))
    state = chatReducer(state, streamMessage.fulfilled({ streamId }, '', arg))
    state = chatReducer(state, streamSources({
      streamId,
      sources: [{ content: 'c', contentPreview: 'p', metadata: { fileId: 'f1', filename: 'r.pdf' }, similarity: 0.9 }],
    }))
    state = chatReducer(state, streamToken({ streamId, token: 'Hel' }))
    state = chatReducer(state, streamToken({ streamId, token: 'lo' }))
    state = chatReducer(state, streamDone({ streamId }))

    const assistant = state.messages[1]
    expect(assistant.role).toBe('assistant')
    expect(assistant.text).toBe('Hello')
    expect(assistant.sources).toEqual([
      { fileId: 'f1', filename: 'r.pdf', preview: 'p', similarity: 0.9 },
    ])
    expect(assistant.isStreaming).toBe(false)
    expect(state.status).toBe('idle')
  })

  it('tolerates frames that arrive before the POST resolves', () => {
    let state = chatReducer(initial(), streamMessage.pending('', arg))
    // token arrives before fulfilled
    state = chatReducer(state, streamToken({ streamId, token: 'Hi' }))
    state = chatReducer(state, streamMessage.fulfilled({ streamId }, '', arg))
    const assistants = state.messages.filter((m) => m.role === 'assistant')
    expect(assistants).toHaveLength(1)
    expect(assistants[0].text).toBe('Hi')
  })

  it('marks the assistant turn as an error on stream error', () => {
    let state = chatReducer(initial(), streamMessage.pending('', arg))
    state = chatReducer(state, streamError({ streamId, error: 'boom' }))
    const assistant = state.messages[1]
    expect(assistant.isError).toBe(true)
    expect(assistant.isStreaming).toBe(false)
    expect(state.error).toBe('boom')
  })
})
