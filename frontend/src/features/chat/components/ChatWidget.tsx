import { useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import { selectChatOpen } from '../chatSlice'
import ChatPanel from './ChatPanel'

// Global RAG assistant panel. The open/close trigger lives in the left sidebar
// (see Sidebar.tsx "Assistant" entry); this just renders the modal when open.
// Hidden when no user is signed in.
export default function ChatWidget() {
  const isOpen = useAppSelector(selectChatOpen)
  const user = useAppSelector(selectCurrentUser)

  if (!user || !isOpen) return null

  return <ChatPanel />
}
