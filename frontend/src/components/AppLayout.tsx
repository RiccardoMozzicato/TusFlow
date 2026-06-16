import { Outlet } from 'react-router-dom'
import { useAppSelector } from '@/app/hooks'
import { selectSidebarCollapsed } from '@/features/ui/uiSlice'
import ChatWidget from '@/features/chat/components/ChatWidget'
import Sidebar from './layout/Sidebar'

// Authenticated app shell: fixed (collapsible) sidebar, page content in <Outlet>.
export default function AppLayout() {
  const collapsed = useAppSelector(selectSidebarCollapsed)

  return (
    <div className="flex h-screen overflow-hidden bg-ds-background font-ds-body text-ds-body-md text-ds-on-surface antialiased">
      <Sidebar />
      <div
        className={`relative z-10 flex h-full min-w-0 flex-1 flex-col bg-ds-background transition-all duration-200 ${
          collapsed ? 'ml-20' : 'ml-72'
        }`}
      >
        <main className="min-w-0 flex-1 overflow-y-auto p-10 pb-6">
          <Outlet />
        </main>
      </div>
      <ChatWidget />
    </div>
  )
}
