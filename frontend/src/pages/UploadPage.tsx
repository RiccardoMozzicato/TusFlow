import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import DropZone from '@/features/upload/components/DropZone'
import ScopeSelector from '@/features/upload/components/ScopeSelector'
import StagedUploads from '@/features/upload/components/StagedUploads'
import StorageMeter from '@/features/upload/components/StorageMeter'
import TransferQueue from '@/features/upload/components/TransferQueue'
import UploadSummaryBar from '@/features/upload/components/UploadSummaryBar'
import type { RowActionKind } from '@/features/upload/components/TransferQueueRow'
import { useUploadQueue } from '@/features/upload/useUploadQueue'
import { allowedUploadScopes } from '@/features/upload/uploadScopes'
import { useStorageInfo } from '@/features/users/useStorageInfo'
import { useAppSelector } from '@/app/hooks'
import { selectCurrentUser } from '@/features/auth/authSlice'
import { SharingScope } from '@/features/documents/documentsApi'

export default function UploadPage() {
  const navigate = useNavigate()
  const user = useAppSelector(selectCurrentUser)
  // Staff cannot share organization-wide, so narrow the scope choices by role.
  const scopes = useMemo(() => allowedUploadScopes(user?.roles), [user?.roles])
  const [scope, setScope] = useState<SharingScope>(SharingScope.PERSONAL)
  const {
    items,
    staged,
    summary,
    addFiles,
    updateStagedMetadata,
    removeStaged,
    startStaged,
    startAllStaged,
    pause,
    resume,
    cancel,
    retry,
    pauseAll,
    resumeAll,
    clearCompleted,
  } = useUploadQueue()
  const storage = useStorageInfo()

  // Guard against a selected scope the user isn't allowed to use (e.g. role
  // changes): fall back to the first permitted scope.
  useEffect(() => {
    if (!scopes.some((option) => option.value === scope)) {
      setScope(scopes[0]?.value ?? SharingScope.PERSONAL)
    }
  }, [scopes, scope])

  // Completed uploads change storageUsed server-side; re-read it so the meter
  // always reflects the space left for the next upload.
  const { refresh: refreshStorage } = storage
  useEffect(() => {
    if (summary.completed > 0) refreshStorage()
  }, [summary.completed, refreshStorage])

  function handleRowAction(id: string, kind: RowActionKind) {
    switch (kind) {
      case 'pause':
        return pause(id)
      case 'resume':
        return resume(id)
      case 'retry':
        return retry(id)
      case 'cancel':
        return cancel(id)
      // The client-generated fileId is the backend file UUID, so it doubles as
      // the Document Details route param.
      case 'open':
        return navigate(`/documents/${id}`)
    }
  }

  const hasUploading = items.some((item) => item.status === 'uploading')
  const hasPaused = items.some((item) => item.status === 'paused')
  const hasCompleted = items.some((item) => item.status === 'completed')

  return (
    <>
      <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="mb-1 font-ds-heading text-ds-headline-md text-ds-on-surface">
            Upload Manager
          </h2>
          <p className="text-ds-body-sm text-ds-on-surface-variant">
            Drag and drop files or manage your current transfer queue.
          </p>
          <div className="mt-3">
            <ScopeSelector scope={scope} onScopeChange={setScope} scopes={scopes} />
          </div>
        </div>
        <StorageMeter summary={storage.summary} hasError={storage.hasError} />
      </header>

      <DropZone onFiles={(files) => addFiles(files, scope)} />

      {staged.length > 0 ? (
        <StagedUploads
          staged={staged}
          onMetadataChange={updateStagedMetadata}
          onRemove={removeStaged}
          onStart={startStaged}
          onStartAll={startAllStaged}
        />
      ) : null}

      <TransferQueue items={items} onAction={handleRowAction} />

      <UploadSummaryBar
        summary={summary}
        onPauseAll={pauseAll}
        onResumeAll={resumeAll}
        onClearCompleted={clearCompleted}
        canPause={hasUploading}
        canResume={hasPaused}
        canClear={hasCompleted}
      />
    </>
  )
}
