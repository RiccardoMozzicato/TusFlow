import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { selectIsAuthenticated } from '@/features/auth/authSlice'
import { applyJobUpdate as applyLibraryJobUpdate } from '@/features/documents/documentsSlice'
import { applyJobUpdate as applyDetailJobUpdate } from '@/features/documents/documentDetailsSlice'
import { JobStatus } from '@/features/documents/jobsApi'
import { connectJobsSocket, disconnectJobsSocket, onJobUpdate, type JobUpdateStatus } from './jobsSocket'

// Maps a live terminal job status onto the JobStatus the Library indicator uses.
// 'deadlettered' has no library-facing distinction — it reads as a failed analysis.
const JOB_STATUS_BY_UPDATE: Record<JobUpdateStatus, JobStatus> = {
  completed: JobStatus.COMPLETED,
  unsupported: JobStatus.UNSUPPORTED,
  failed: JobStatus.FAILED,
  deadlettered: JobStatus.FAILED,
}

/**
 * App-level bridge: opens the shared jobs socket once the user is authenticated and
 * mirrors every live job update onto the documents slice, so the Library reflects AI
 * outcomes without a refetch. Page-level consumers (upload queue, failed jobs,
 * dashboard) register their own `onJobUpdate` listeners on top of the same socket.
 */
export function useJobsRealtime(): void {
  const dispatch = useAppDispatch()
  const isAuthenticated = useAppSelector(selectIsAuthenticated)

  useEffect(() => {
    if (!isAuthenticated) return

    connectJobsSocket()
    const unsubscribe = onJobUpdate((update) => {
      // Library card status indicator.
      dispatch(
        applyLibraryJobUpdate({
          fileId: update.fileId,
          aiStatus: JOB_STATUS_BY_UPDATE[update.status],
        }),
      )
      // Open details page: patch its AI metadata + timeline live (no-op otherwise).
      dispatch(
        applyDetailJobUpdate({
          fileId: update.fileId,
          status: update.status,
          metadata: update.metadata,
        }),
      )
    })

    return () => {
      unsubscribe()
      disconnectJobsSocket()
    }
  }, [isAuthenticated, dispatch])
}
