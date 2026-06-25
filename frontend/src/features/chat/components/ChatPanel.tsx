import { useEffect, useMemo, useRef, useState } from 'react'
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

// Picks the file-type glyph for a cited source chip.
function sourceIcon(label: string): { name: string; className: string } {
  if (label.toLowerCase().endsWith('.pdf')) {
    return { name: 'picture_as_pdf', className: 'text-ds-error text-[18px]' }
  }
  return { name: 'description', className: 'text-ds-primary text-[18px]' }
}

function SourceList({ sources }: { sources: ChatSource[] }) {
  const dispatch = useAppDispatch()
  if (sources.length === 0) return null
  return (
    <div className="mt-4 border-t border-ds-outline-variant pt-4">
      <h4 className="mb-3 font-ds-label text-ds-label-md uppercase text-ds-secondary">
        Sources ({sources.length})
      </h4>
      <div className="flex flex-wrap gap-3">
        {sources.map((source, index) => {
          const label = source.filename ?? source.fileId ?? `Source ${index + 1}`
          const icon = sourceIcon(label)
          const inner = (
            <>
              <Icon name={icon.name} filled={false} className={icon.className} />
              <span className="truncate font-ds-body text-ds-body-sm text-ds-primary group-hover:underline">
                {label}
              </span>
            </>
          )
          const chipClass =
            'group flex items-center gap-2 rounded-ds-lg border border-ds-outline-variant bg-ds-surface-container px-3 py-1.5 transition-colors hover:border-ds-primary'
          return (
            <div key={`${source.fileId ?? label}-${index}`} title={source.preview}>
              {source.fileId ? (
                <Link
                  to={`/documents/${source.fileId}`}
                  onClick={() => dispatch(closeChat())}
                  className={chipClass}
                >
                  {inner}
                </Link>
              ) : (
                <span className={chipClass}>{inner}</span>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

// Collapsible retrieval trace, derived from the chunks the assistant actually
// retrieved. Mirrors the mockup's "Agent Reasoning" panel without fabricating
// steps the backend never emitted.
function AgentReasoning({ sources }: { sources: ChatSource[] }) {
  const [expanded, setExpanded] = useState(false)
  if (sources.length === 0) return null

  const consulted = Array.from(
    new Set(sources.map((s) => s.filename ?? s.fileId).filter((v): v is string => Boolean(v))),
  )

  return (
    <div className="overflow-hidden rounded-ds-lg border border-ds-outline-variant bg-ds-surface-container-low">
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        className="flex w-full items-center justify-between bg-ds-surface-container-high/50 px-4 py-2 transition-colors hover:bg-ds-surface-container-high"
      >
        <span className="flex items-center gap-2 text-ds-secondary">
          <Icon name="psychology" filled={false} className="text-[16px]" />
          <span className="font-ds-label text-ds-label-md">View Agent Reasoning</span>
        </span>
        <Icon
          name="expand_more"
          filled={false}
          className={`transition-transform duration-300 ${expanded ? 'rotate-180' : ''}`}
        />
      </button>
      {expanded ? (
        <div className="space-y-2 border-t border-ds-outline-variant p-4 font-ds-body text-ds-body-sm text-ds-on-surface-variant">
          <div className="flex items-center gap-2">
            <Icon name="check_circle" filled={false} className="text-[16px] text-ds-primary" />
            <span>Searched the vector store across your accessible documents.</span>
          </div>
          <div className="flex items-center gap-2">
            <Icon name="check_circle" filled={false} className="text-[16px] text-ds-primary" />
            <span>Retrieved {sources.length} relevant document chunk{sources.length === 1 ? '' : 's'}.</span>
          </div>
          {consulted.map((name) => (
            <div key={name} className="flex items-center gap-2">
              <Icon name="search" filled={false} className="text-[16px] text-ds-primary" />
              <span className="truncate">Consulted &ldquo;{name}&rdquo;.</span>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  )
}

// The three bouncing dots used while the assistant is preparing / streaming its
// first tokens.
function TypingDots() {
  return (
    <div className="flex h-8 items-center gap-1">
      <span className="h-2 w-2 animate-bounce rounded-ds-full bg-ds-primary/40 [animation-delay:0s]" />
      <span className="h-2 w-2 animate-bounce rounded-ds-full bg-ds-primary/40 [animation-delay:0.15s]" />
      <span className="h-2 w-2 animate-bounce rounded-ds-full bg-ds-primary/40 [animation-delay:0.3s]" />
    </div>
  )
}

function UserBubble({ text }: { text: string }) {
  return (
    <div className="flex justify-end">
      <div className="max-w-[80%] rounded-ds-xl rounded-tr-none bg-ds-surface-container-high px-4 py-3 text-ds-on-surface-variant shadow-sm">
        <p className="whitespace-pre-wrap break-words font-ds-body text-ds-body-md">{text}</p>
      </div>
    </div>
  )
}

function AssistantTurn({ message }: { message: ChatMessageModel }) {
  const [copied, setCopied] = useState(false)
  const sources = message.sources ?? []
  const showDots = message.isStreaming && message.text.length === 0

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(message.text)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      // Clipboard may be unavailable (e.g. insecure context); silently ignore.
    }
  }

  return (
    <div className="flex items-start gap-3">
      <div className="mt-1 flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-ds-full bg-ds-primary">
        <Icon name="smart_toy" className="text-[18px] text-ds-on-primary" />
      </div>
      <div className="flex-1 space-y-3">
        <AgentReasoning sources={sources} />

        <div
          className={`max-w-[95%] space-y-3 rounded-ds-xl rounded-tl-none border p-4 shadow-sm ${
            message.isError
              ? 'border-ds-error/40 bg-ds-error-container'
              : 'border-ds-outline-variant bg-ds-surface-container-lowest'
          }`}
        >
          {showDots ? (
            <TypingDots />
          ) : (
            <p
              className={`whitespace-pre-wrap break-words font-ds-body text-ds-body-md leading-relaxed ${
                message.isError ? 'text-ds-on-error-container' : 'text-ds-on-surface'
              }`}
            >
              {message.text}
            </p>
          )}
          <SourceList sources={sources} />
        </div>

        {!message.isStreaming && message.text.length > 0 && !message.isError ? (
          <div className="flex items-center gap-1 pl-1">
            <button
              type="button"
              onClick={handleCopy}
              className="flex items-center gap-1 rounded-ds px-1.5 py-1 text-ds-secondary transition-colors hover:text-ds-primary"
            >
              <Icon name={copied ? 'check' : 'content_copy'} filled={false} className="text-[18px]" />
              <span className="font-ds-label text-ds-label-sm">{copied ? 'Copied' : 'Copy'}</span>
            </button>
          </div>
        ) : null}
      </div>
    </div>
  )
}

// The RAG assistant as a centered modal dialog. Each turn is an independent RAG
// query scoped to the current user's accessible files; answers stream in over
// the rag socket.
export default function ChatPanel() {
  const dispatch = useAppDispatch()
  const messages = useAppSelector(selectChatMessages)
  const status = useAppSelector(selectChatStatus)
  const isStreaming = useAppSelector(selectChatStreaming)
  const user = useAppSelector(selectCurrentUser)
  const [input, setInput] = useState('')
  const scrollRef = useRef<HTMLDivElement>(null)
  const turnTopRef = useRef<HTMLDivElement>(null)

  // Id of the latest assistant turn, so we can anchor the viewport to the start
  // of the answer (not chase the streaming tail to the bottom).
  const lastAssistantId = useMemo(() => {
    for (let i = messages.length - 1; i >= 0; i -= 1) {
      if (messages[i].role === 'assistant') return messages[i].id
    }
    return null
  }, [messages])

  const isLoading = status === 'loading'
  const canSend = input.trim().length > 0 && !isLoading && Boolean(user?.uuid)
  // "Thinking…" dots only cover the gap before any assistant turn exists.
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

  // Close on Escape, matching standard modal behaviour.
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') dispatch(closeChat())
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [dispatch])

  // When a new assistant turn begins, scroll so the START of its answer sits at
  // the top of the transcript, instead of following the streaming tail down.
  useEffect(() => {
    if (lastAssistantId) {
      turnTopRef.current?.scrollIntoView({ block: 'start', behavior: 'smooth' })
    } else {
      // No assistant turn yet (e.g. the "thinking" state): keep the latest user
      // message visible at the bottom.
      scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight })
    }
  }, [lastAssistantId, isThinking])

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    const query = input.trim()
    if (!query || isLoading || !user?.uuid) return
    void dispatch(streamMessage({ query }))
    setInput('')
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ds-primary/40 p-4 backdrop-blur-sm"
      onClick={() => dispatch(closeChat())}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="DocPortal AI Assistant"
        onClick={(event) => event.stopPropagation()}
        className="flex h-[min(85vh,870px)] w-full max-w-3xl flex-col overflow-hidden rounded-ds-xl border border-ds-outline-variant bg-ds-surface-container-lowest shadow-2xl"
      >
        {/* Header */}
        <header className="flex h-16 items-center justify-between border-b border-ds-outline-variant bg-ds-primary px-6 text-ds-on-primary">
          <div className="flex items-center gap-4">
            <div className="flex h-10 w-10 items-center justify-center rounded-ds-full bg-white/10">
              <Icon name="auto_awesome" className="text-[22px] text-ds-primary-fixed-dim" />
            </div>
            <div>
              <h1 className="font-ds-heading text-ds-headline-sm leading-tight">
                DocPortal AI Assistant
              </h1>
              <p className="font-ds-label text-ds-label-sm uppercase tracking-wider opacity-80">
                Agentic RAG Engine
              </p>
            </div>
          </div>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => dispatch(clearChat())}
              title="Clear conversation"
              aria-label="Clear conversation"
              className="flex h-10 w-10 items-center justify-center rounded-ds-full transition-colors hover:bg-white/10"
            >
              <Icon name="delete_sweep" filled={false} className="text-[20px]" />
            </button>
            <button
              type="button"
              onClick={() => dispatch(closeChat())}
              title="Close"
              aria-label="Close chat"
              className="flex h-10 w-10 items-center justify-center rounded-ds-full transition-colors hover:bg-white/10"
            >
              <Icon name="close" filled={false} className="text-[20px]" />
            </button>
          </div>
        </header>

        {/* Transcript */}
        <div ref={scrollRef} className="flex-1 space-y-6 overflow-y-auto bg-ds-surface-bright p-6">
          {messages.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center gap-3 text-center">
              <Icon name="forum" filled={false} className="text-[40px] text-ds-secondary" />
              <p className="max-w-sm font-ds-body text-ds-body-md text-ds-secondary">
                Ask a question about the documents you can access. I&rsquo;ll search them and cite my
                sources.
              </p>
            </div>
          ) : (
            messages.map((message) =>
              message.role === 'user' ? (
                <UserBubble key={message.id} text={message.text} />
              ) : (
                <div
                  key={message.id}
                  ref={message.id === lastAssistantId ? turnTopRef : undefined}
                  className="scroll-mt-6"
                >
                  <AssistantTurn message={message} />
                </div>
              ),
            )
          )}

          {isThinking ? (
            <div className="flex items-start gap-3">
              <div className="mt-1 flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-ds-full bg-ds-primary">
                <Icon name="smart_toy" className="text-[18px] text-ds-on-primary" />
              </div>
              <TypingDots />
            </div>
          ) : null}
        </div>

        {/* Input */}
        <footer className="border-t border-ds-outline-variant bg-ds-surface p-4">
          <form
            onSubmit={handleSubmit}
            className="flex items-end gap-2 rounded-ds-xl border border-ds-outline bg-ds-surface-container-lowest px-3 py-2 shadow-sm transition-all focus-within:ring-2 focus-within:ring-ds-primary"
          >
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
              placeholder="Ask a question about your documents…"
              className="max-h-32 flex-1 resize-none border-none bg-transparent px-1 py-1.5 font-ds-body text-ds-body-md text-ds-on-surface outline-none placeholder:text-ds-outline"
            />
            <button
              type="submit"
              disabled={!canSend}
              className="flex items-center gap-2 rounded-ds-lg bg-ds-primary px-4 py-2 font-ds-label text-ds-label-md text-ds-on-primary shadow-md transition-all hover:opacity-90 active:scale-95 disabled:cursor-not-allowed disabled:opacity-50"
            >
              SEND
              <Icon name="send" filled={false} className="text-[18px]" />
            </button>
          </form>
          <p className="mt-3 text-center font-ds-label text-ds-label-sm text-ds-secondary">
            DocPortal AI uses retrieval-augmented generation. Verify critical information.
          </p>
        </footer>
      </div>
    </div>
  )
}
