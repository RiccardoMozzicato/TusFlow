import { describe, expect, it } from 'vitest'
import chatReducer, {
  askRag,
  clearChat,
  closeChat,
  openChat,
  toggleChat,
} from './chatSlice'
import type { RagQueryResponse } from './chatApi'

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
    let state = chatReducer(initial(), askRag.pending('', { query: 'hi', userId: 'u1' }))
    expect(state.messages).toHaveLength(1)
    state = chatReducer(state, clearChat())
    expect(state.messages).toHaveLength(0)
    expect(state.error).toBeNull()
  })
})

describe('askRag lifecycle', () => {
  const arg = { query: 'What is in the report?', userId: 'u1' }

  it('appends the user turn and sets loading on pending', () => {
    const state = chatReducer(initial(), askRag.pending('', arg))
    expect(state.status).toBe('loading')
    expect(state.messages).toEqual([
      expect.objectContaining({ role: 'user', text: arg.query }),
    ])
  })

  it('appends the assistant answer with mapped sources on fulfilled', () => {
    const response: RagQueryResponse = {
      query: arg.query,
      answer: 'The report covers Q3 results.',
      sourcesCount: 1,
      sources: [
        {
          content: 'full chunk',
          contentPreview: 'preview…',
          metadata: { fileId: 'f1', filename: 'report.pdf' },
          similarity: 0.91,
        },
      ],
      accessibleFilesCount: 4,
    }
    let state = chatReducer(initial(), askRag.pending('', arg))
    state = chatReducer(state, askRag.fulfilled(response, '', arg))

    expect(state.status).toBe('idle')
    expect(state.messages).toHaveLength(2)
    const assistant = state.messages[1]
    expect(assistant.role).toBe('assistant')
    expect(assistant.text).toBe(response.answer)
    expect(assistant.sources).toEqual([
      { fileId: 'f1', filename: 'report.pdf', preview: 'preview…', similarity: 0.91 },
    ])
  })

  it('appends an error turn on rejected', () => {
    let state = chatReducer(initial(), askRag.pending('', arg))
    state = chatReducer(state, askRag.rejected(new Error('boom'), '', arg))
    expect(state.status).toBe('idle')
    expect(state.error).toBe('boom')
    expect(state.messages[1]).toEqual(
      expect.objectContaining({ role: 'assistant', isError: true }),
    )
  })

  it('gives every message a unique id', () => {
    let state = chatReducer(initial(), askRag.pending('', arg))
    state = chatReducer(
      state,
      askRag.fulfilled(
        { query: arg.query, answer: 'a', sourcesCount: 0, sources: [], accessibleFilesCount: 0 },
        '',
        arg,
      ),
    )
    const ids = state.messages.map((m) => m.id)
    expect(new Set(ids).size).toBe(ids.length)
  })
})
