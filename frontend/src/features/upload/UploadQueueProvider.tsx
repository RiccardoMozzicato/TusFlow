import { createContext, useContext, type ReactNode } from 'react'
import { useUploadQueue, type UseUploadQueue } from './useUploadQueue'

// Hosts the upload queue at app level so uploads keep running (and stay visible)
// across route changes. Mounted in AppLayout, which persists while only the routed
// page swaps — so navigating away no longer unmounts the queue or aborts uploads.
const UploadQueueContext = createContext<UseUploadQueue | null>(null)

export function UploadQueueProvider({ children }: { children: ReactNode }) {
  const queue = useUploadQueue()
  return <UploadQueueContext.Provider value={queue}>{children}</UploadQueueContext.Provider>
}

export function useUploadQueueContext(): UseUploadQueue {
  const queue = useContext(UploadQueueContext)
  if (!queue) {
    throw new Error('useUploadQueueContext must be used within an UploadQueueProvider')
  }
  return queue
}
