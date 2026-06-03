import { Outlet } from 'react-router-dom'
import Sidebar from './layout/Sidebar'
import Topbar from './layout/Topbar'

// Authenticated app shell: fixed sidebar + sticky topbar, page content in <Outlet>.
export default function AppLayout() {
  return (
    <div className="flex h-screen overflow-hidden bg-ds-background font-ds-body text-ds-body-md text-ds-on-surface antialiased">
      <Sidebar />
      <div className="relative z-10 ml-72 flex h-full flex-1 flex-col bg-ds-background">
        <Topbar />
        <main className="flex-1 overflow-y-auto p-10 pb-24">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
