import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import { beforeAll, describe, expect, it, vi } from 'vitest'
import authReducer from '@/features/auth/authSlice'
import chatReducer, { streamDone, streamSources, streamToken } from '../chatSlice'
import type { RagSource } from '../chatApi'
import ChatPanel from './ChatPanel'

// The panel opens a websocket on mount; stub it out so tests stay offline.
vi.mock('@/features/realtime/ragSocket', () => ({
  connectRagSocket: vi.fn(),
  disconnectRagSocket: vi.fn(),
  onRagEvent: vi.fn(() => () => {}),
}))

// jsdom doesn't implement scrollTo; the panel auto-scrolls on new messages.
beforeAll(() => {
  Element.prototype.scrollTo = vi.fn()
})

const SOURCE: RagSource = {
  content: 'Default deny; only port 443 allowed for external web services.',
  contentPreview: 'Default deny; only port 443…',
  metadata: { fileId: 'doc-1', filename: 'lab04_Firewalls.pdf' },
  similarity: 0.91,
}

// Seeds one completed assistant turn (text + a cited source) and renders the panel.
function renderWithAnswer() {
  const store = configureStore({ reducer: { chat: chatReducer, auth: authReducer } })
  store.dispatch(streamSources({ streamId: 's1', sources: [SOURCE] }))
  store.dispatch(streamToken({ streamId: 's1', token: 'Here are the firewall requirements.' }))
  store.dispatch(streamDone({ streamId: 's1' }))
  render(
    <Provider store={store}>
      <MemoryRouter>
        <ChatPanel />
      </MemoryRouter>
    </Provider>,
  )
}

describe('ChatPanel — sources', () => {
  it('renders the cited sources under an assistant answer, linking to each document', () => {
    renderWithAnswer()

    expect(screen.getByText('Here are the firewall requirements.')).toBeInTheDocument()
    expect(screen.getByText('Sources (1)')).toBeInTheDocument()

    const link = screen.getByRole('link', { name: /lab04_Firewalls\.pdf/ })
    expect(link).toHaveAttribute('href', '/documents/doc-1')
  })

  it('lists the consulted document inside the collapsible agent reasoning', async () => {
    renderWithAnswer()

    await userEvent.click(screen.getByRole('button', { name: /View Agent Reasoning/ }))

    expect(screen.getByText(/Retrieved 1 relevant document chunk\./)).toBeInTheDocument()
    expect(screen.getByText(/Consulted .*lab04_Firewalls\.pdf/)).toBeInTheDocument()
  })
})
