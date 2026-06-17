import Icon from '@/components/Icon'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import { selectChatOpen, toggleChat } from '../chatSlice'
import ChatPanel from './ChatPanel'

// Global RAG assistant: a floating button (bottom-right, on every authenticated
// page) that toggles a compact chat panel. Hidden when no user is signed in.
export default function ChatWidget() {
  const dispatch = useAppDispatch()
  const isOpen = useAppSelector(selectChatOpen)
  const user = useAppSelector(selectCurrentUser)

  if (!user) return null

  return (
    <>
      {isOpen ? <ChatPanel /> : null}
      <button
        type="button"
        data-tour="chat-launcher"
        onClick={() => dispatch(toggleChat())}
        aria-label={isOpen ? 'Close assistant' : 'Open assistant'}
        aria-expanded={isOpen}
        title={isOpen ? 'Close assistant' : 'Ask your documents'}
        className="fixed bottom-6 right-6 z-40 flex h-14 w-14 items-center justify-center rounded-ds-full bg-ds-primary text-ds-on-primary shadow-xl transition-all hover:bg-ds-primary-container active:scale-95"
      >
        <Icon name={isOpen ? 'close' : 'smart_toy'} className="text-[26px]" />
      </button>
    </>
  )
}
