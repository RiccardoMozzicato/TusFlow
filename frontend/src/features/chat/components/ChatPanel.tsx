import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Icon from '@/components/Icon'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import {
  clearChat,
  closeChat,
  selectChatMessages,
  selectChatStatus,
  selectChatStreaming,
  streamDone,
  streamError,
  streamMessage,
  streamSources,
  streamToken,
} from '../chatSlice'
import { connectRagSocket, disconnectRagSocket, onRagEvent } from '@/features/realtime/ragSocket'
import type { ChatMessage as ChatMessageModel, ChatSource } from '../types'

function SourceList({ sources }: { sources: ChatSource[] }) {
  if (sources.length === 0) return null
  return (
    <div className="mt-2 flex flex-col gap-1 border-t border-ds-outline-variant/50 pt-2">
      <span className="font-ds-label text-ds-label-sm text-ds-secondary">
        Sources ({sources.length})
      </span>
      {sources.map((source, index) => {
        const label = source.filename ?? source.fileId ?? `Source ${index + 1}`
        const content = (
          <>
            <Icon name="description" filled={false} className="text-[14px]" />
            <span className="truncate">{label}</span>
          </>
        )
        return (
          <div key={`${source.fileId ?? label}-${index}`} title={source.preview}>
            {source.fileId ? (
              <Link
                to={`/documents/${source.fileId}`}
                className="flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-primary hover:underline"
              >
                {content}
              </Link>
            ) : (
              <span className="flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-on-surface-variant">
                {content}
              </span>
            )}
          </div>
        )
      })}
    </div>
  )
}

function MessageBubble({ message }: { message: ChatMessageModel }) {
  const isUser = message.role === 'user'
  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div
        className={`max-w-[85%] rounded-ds-lg px-3 py-2 font-ds-body text-ds-body-sm ${
          isUser
            ? 'bg-ds-primary text-ds-on-primary'
            : message.isError
              ? 'bg-ds-error-container text-ds-on-error-container'
              : 'bg-ds-surface-container text-ds-on-surface'
        }`}
      >
        <p className="whitespace-pre-wrap break-words">{message.text}</p>
        {message.sources ? <SourceList sources={message.sources} /> : null}
      </div>
    </div>
  )
}

// The RAG chat panel: a compact widget anchored bottom-right. Each turn is an
// independent RAG query scoped to the current user's accessible files.
export default function ChatPanel() {
  const dispatch = useAppDispatch()
  const messages = useAppSelector(selectChatMessages)
  const status = useAppSelector(selectChatStatus)
  const isStreaming = useAppSelector(selectChatStreaming)
  const user = useAppSelector(selectCurrentUser)
  const [input, setInput] = useState('')
  const scrollRef = useRef<HTMLDivElement>(null)

  const isLoading = status === 'loading'
  const canSend = input.trim().length > 0 && !isLoading && Boolean(user?.uuid)
  // "Thinking…" only covers the gap before the first streamed frame arrives.
  const isThinking = isLoading && !isStreaming

  // Subscribe to streamed answer frames while the panel is open.
  useEffect(() => {
    connectRagSocket()
    const unsubscribe = onRagEvent((event) => {
      switch (event.type) {
        case 'SOURCES':
          dispatch(streamSources({ streamId: event.streamId, sources: event.sources ?? [] }))
          break
        case 'TOKEN':
          if (event.token) dispatch(streamToken({ streamId: event.streamId, token: event.token }))
          break
        case 'DONE':
          dispatch(streamDone({ streamId: event.streamId }))
          break
        case 'ERROR':
          dispatch(streamError({ streamId: event.streamId, error: event.error ?? undefined }))
          break
      }
    })
    return () => {
      unsubscribe()
      disconnectRagSocket()
    }
  }, [dispatch])

  // Keep the newest message in view as the transcript grows or while tokens stream in.
  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight })
  }, [messages, isLoading])

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    const query = input.trim()
    if (!query || isLoading || !user?.uuid) return
    void dispatch(streamMessage({ query }))
    setInput('')
  }

  return (
    <div className="fixed bottom-24 right-6 z-40 flex h-[32rem] max-h-[calc(100vh-7rem)] w-96 max-w-[calc(100vw-3rem)] flex-col overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-xl">
      <div className="flex items-center justify-between border-b border-ds-outline-variant bg-ds-surface px-4 py-3">
        <div className="flex items-center gap-2">
          <Icon name="smart_toy" className="text-[20px] text-ds-primary" />
          <h2 className="font-ds-heading text-ds-headline-sm text-ds-on-surface">Ask your documents</h2>
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => dispatch(clearChat())}
            title="Clear conversation"
            aria-label="Clear conversation"
            className="rounded-ds-full p-1.5 text-ds-secondary transition-colors hover:bg-ds-surface-container-high hover:text-ds-primary"
          >
            <Icon name="delete_sweep" filled={false} className="text-[18px]" />
          </button>
          <button
            type="button"
            onClick={() => dispatch(closeChat())}
            title="Close"
            aria-label="Close chat"
            className="rounded-ds-full p-1.5 text-ds-secondary transition-colors hover:bg-ds-surface-container-high hover:text-ds-primary"
          >
            <Icon name="close" filled={false} className="text-[18px]" />
          </button>
        </div>
      </div>

      <div ref={scrollRef} className="flex flex-1 flex-col gap-3 overflow-y-auto px-4 py-4">
        {messages.length === 0 ? (
          <div className="m-auto flex flex-col items-center gap-2 text-center">
            <Icon name="forum" filled={false} className="text-[36px] text-ds-secondary" />
            <p className="font-ds-body text-ds-body-sm text-ds-secondary">
              Ask a question about the documents you can access.
            </p>
          </div>
        ) : (
          messages.map((message) => <MessageBubble key={message.id} message={message} />)
        )}
        {isThinking ? (
          <div className="flex justify-start">
            <div className="flex items-center gap-2 rounded-ds-lg bg-ds-surface-container px-3 py-2 font-ds-body text-ds-body-sm text-ds-secondary">
              <Icon name="progress_activity" filled={false} className="animate-spin text-[16px]" />
              Thinking…
            </div>
          </div>
        ) : null}
      </div>

      <form onSubmit={handleSubmit} className="flex items-end gap-2 border-t border-ds-outline-variant bg-ds-surface px-3 py-3">
        <textarea
          value={input}
          onChange={(event) => setInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault()
              handleSubmit(event)
            }
          }}
          rows={1}
          placeholder="Ask a question…"
          className="max-h-28 min-h-[2.5rem] flex-1 resize-none rounded-ds-md border border-ds-outline-variant bg-ds-surface-container-lowest px-3 py-2 font-ds-body text-ds-body-sm text-ds-on-surface outline-none focus:border-ds-primary focus:ring-1 focus:ring-ds-primary"
        />
        <button
          type="submit"
          disabled={!canSend}
          title="Send"
          aria-label="Send"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-ds-full bg-ds-primary text-ds-on-primary transition-colors hover:bg-ds-primary-container disabled:cursor-not-allowed disabled:opacity-50"
        >
          <Icon name="send" filled={false} className="text-[18px]" />
        </button>
      </form>
    </div>
  )
}
