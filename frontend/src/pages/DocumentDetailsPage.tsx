import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import Icon from '@/components/Icon'
import ConfirmDialog from '@/components/ConfirmDialog'
import {
  documentPageUrl,
  getDownloadBlob,
  type Permission,
  type SensitivityLevel,
} from '@/features/documents/detailsApi'
import { saveBlob } from '@/lib/download'
import { printBlob } from '@/lib/print'
import {
  createShare,
  deleteDocument,
  loadDocumentDetail,
  loadShares,
  loadWritable,
  removeShare,
  resetDetail,
  saveGeneratedMetadata,
  saveUserMetadata,
  selectCanEditMetadata,
  selectCreateShareError,
  selectDeleteFileError,
  selectDetail,
  selectDetailError,
  selectDetailStatus,
  selectIsCreatingShare,
  selectIsDeletingFile,
  selectIsMutatingShare,
  selectIsSavingMetadata,
  selectIsSavingUserMetadata,
  selectSaveMetadataError,
  selectSaveUserMetadataError,
  selectShareMutationError,
  selectShares,
  selectSharesStatus,
  updateShare,
} from '@/features/documents/documentDetailsSlice'
import type { FileType, MetadataEntry } from '@/features/documents/types'
import DocumentDetailsHeader from '@/features/documents/components/details/DocumentDetailsHeader'
import DocumentPreview from '@/features/documents/components/details/DocumentPreview'
import PrimaryInfoCard from '@/features/documents/components/details/PrimaryInfoCard'
import AiMetadataPanel from '@/features/documents/components/details/AiMetadataPanel'
import UserMetadataPanel from '@/features/documents/components/details/UserMetadataPanel'
import ProcessingTimeline from '@/features/documents/components/details/ProcessingTimeline'
import VersionHistory from '@/features/documents/components/details/VersionHistory'
import SharingPanel from '@/features/documents/components/details/SharingPanel'

// File types the browser can render — and therefore print — inline. Other types
// (e.g. DOCX, video) are download-only; printing them is not offered.
const PRINTABLE_TYPES: ReadonlySet<FileType> = new Set(['pdf', 'txt', 'image'])

// Centered status panel reused for loading / error / not-found states.
function StatusPanel({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-24 text-center">
      {children}
    </div>
  )
}

// Document Details: a live document preview alongside a metadata panel (AI
// summary, properties, processing status, versions, sharing). Data is fetched
// from the gateway-routed file service, keyed by the `:id` route param.
export default function DocumentDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const dispatch = useAppDispatch()
  const navigate = useNavigate()

  const detail = useAppSelector(selectDetail)
  const status = useAppSelector(selectDetailStatus)
  const error = useAppSelector(selectDetailError)
  const shares = useAppSelector(selectShares)
  const sharesStatus = useAppSelector(selectSharesStatus)
  const isCreatingShare = useAppSelector(selectIsCreatingShare)
  const createShareError = useAppSelector(selectCreateShareError)
  const isMutatingShare = useAppSelector(selectIsMutatingShare)
  const shareMutationError = useAppSelector(selectShareMutationError)
  const isDeletingFile = useAppSelector(selectIsDeletingFile)
  const deleteFileError = useAppSelector(selectDeleteFileError)
  const canEditMetadata = useAppSelector(selectCanEditMetadata)
  const isSavingMetadata = useAppSelector(selectIsSavingMetadata)
  const saveMetadataError = useAppSelector(selectSaveMetadataError)
  const isSavingUserMetadata = useAppSelector(selectIsSavingUserMetadata)
  const saveUserMetadataError = useAppSelector(selectSaveUserMetadataError)

  const [isDownloading, setIsDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const [isPrinting, setIsPrinting] = useState(false)
  const [printError, setPrintError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)

  useEffect(() => {
    if (!id) return
    void dispatch(loadDocumentDetail(id))
    void dispatch(loadShares(id))
    void dispatch(loadWritable(id))
    return () => {
      dispatch(resetDetail())
    }
  }, [dispatch, id])

  function handleCreateShare(userId: string, permission: Permission) {
    if (!id) return
    void dispatch(createShare({ id, userId, permission }))
  }

  function handleRemoveShare(shareId: number) {
    if (!id) return
    void dispatch(removeShare({ id, shareId }))
  }

  function handleUpdatePermission(shareId: number, permission: Permission) {
    if (!id) return
    void dispatch(updateShare({ id, shareId, permission }))
  }

  // Deletes the file, then returns to the library on success. The confirm dialog
  // stays open (showing the error) if the delete fails.
  function handleConfirmDelete() {
    if (!id) return
    void dispatch(deleteDocument({ id }))
      .unwrap()
      .then(() => {
        setConfirmDelete(false)
        navigate('/documents')
      })
      .catch(() => {
        // Error is surfaced via deleteFileError in the dialog.
      })
  }

  // Persists edited AI metadata against the current version. Returns the thunk's
  // unwrapped promise so the editor can exit edit mode only on success.
  function handleSaveMetadata(changes: {
    summary: string
    sensitivity: SensitivityLevel
    tags: string[]
  }): Promise<void> {
    if (!detail) return Promise.reject(new Error('Document not loaded'))
    return dispatch(
      saveGeneratedMetadata({
        id: detail.id,
        version: detail.currentVersion,
        summary: changes.summary,
        sensitivity: changes.sensitivity,
        tags: changes.tags,
      }),
    )
      .unwrap()
      .then(() => undefined)
  }

  // Persists edited user metadata against the current version.
  function handleSaveUserMetadata(payload: {
    metadata: Record<string, string>
    entries: MetadataEntry[]
  }): Promise<void> {
    if (!detail) return Promise.reject(new Error('Document not loaded'))
    return dispatch(
      saveUserMetadata({
        id: detail.id,
        version: detail.currentVersion,
        metadata: payload.metadata,
        entries: payload.entries,
      }),
    )
      .unwrap()
      .then(() => undefined)
  }

  async function handleDownload() {
    if (!detail || isDownloading) return
    setIsDownloading(true)
    setDownloadError(null)
    try {
      const blob = await getDownloadBlob(detail.id)
      saveBlob(blob, detail.title)
    } catch (err) {
      setDownloadError(err instanceof Error ? err.message : 'Failed to download document')
    } finally {
      setIsDownloading(false)
    }
  }

  // Fetches the document bytes and opens the browser print dialog. Only inline-
  // renderable types are printable; others would be downloaded by the print
  // frame, so we surface a hint pointing at Download instead.
  async function handlePrint() {
    if (!detail || isPrinting) return
    if (!PRINTABLE_TYPES.has(detail.fileType)) {
      setPrintError("Printing isn't available for this file type. Download it to print instead.")
      return
    }
    setIsPrinting(true)
    setPrintError(null)
    try {
      const blob = await getDownloadBlob(detail.id)
      printBlob(blob)
    } catch (err) {
      setPrintError(err instanceof Error ? err.message : 'Failed to print document')
    } finally {
      setIsPrinting(false)
    }
  }

  if (status === 'loading' || status === 'idle') {
    return (
      <StatusPanel>
        <Icon name="hourglass_empty" filled={false} className="text-[40px] text-ds-secondary" />
        <p className="font-ds-body text-ds-body-sm text-ds-secondary">Loading document…</p>
      </StatusPanel>
    )
  }

  if (status === 'failed' || !detail) {
    return (
      <StatusPanel>
        <Icon name="search_off" filled={false} className="text-[48px] text-ds-secondary" />
        <h2 className="font-ds-heading text-ds-headline-sm text-ds-on-surface">
          Couldn't load document
        </h2>
        <p className="font-ds-body text-ds-body-sm text-ds-secondary">
          {error ?? "We couldn't find a document with that id."}
        </p>
        <div className="mt-2 flex items-center gap-2">
          {id ? (
            <button
              type="button"
              onClick={() => void dispatch(loadDocumentDetail(id))}
              className="rounded-ds border border-ds-outline-variant bg-ds-surface-container px-3 py-1.5 font-ds-label text-ds-label-sm text-ds-on-surface transition-colors hover:bg-ds-surface-container-high"
            >
              Retry
            </button>
          ) : null}
          <Link
            to="/documents"
            className="rounded-ds-lg bg-ds-primary px-4 py-1.5 font-ds-label text-ds-label-md text-ds-on-primary transition-colors hover:bg-ds-primary-container"
          >
            Back to Library
          </Link>
        </div>
      </StatusPanel>
    )
  }

  return (
    <div className="mx-auto flex w-full max-w-[1440px] flex-col gap-6 lg:h-full lg:min-h-0">
      <div className="flex flex-col gap-1">
        <DocumentDetailsHeader
          breadcrumbs={detail.breadcrumbs}
          onPrint={() => void handlePrint()}
          isPrinting={isPrinting}
          onDownload={() => void handleDownload()}
          isDownloading={isDownloading}
          canDelete={canEditMetadata}
          onDeleteFile={() => setConfirmDelete(true)}
        />
        {printError ? (
          <p className="text-right font-ds-body text-ds-body-sm text-ds-error">{printError}</p>
        ) : null}
      </div>

      {/* On lg+ the view fills the viewport height so the page itself never
          scrolls: the preview adapts to the available height and the right rail
          scrolls internally if its cards exceed it. */}
      <div className="grid flex-1 grid-cols-1 gap-6 lg:min-h-0 lg:grid-cols-12 lg:grid-rows-1">
        <div data-tour="details-preview" className="flex min-h-0 flex-col lg:col-span-7">
          <DocumentPreview
            fileId={detail.id}
            onDownload={() => void handleDownload()}
            title={detail.title}
            fileType={detail.fileType}
          />
        </div>

        {/* Right rail: full-width header + AI panel, then the shorter Status /
            Versions / Sharing / User-metadata cards packed two-up to cut height. */}
        <div className="grid grid-cols-1 gap-4 lg:col-span-5 lg:min-h-0 lg:auto-rows-min lg:grid-cols-2 lg:overflow-y-auto lg:pr-1">
          <div className="lg:col-span-2">
            <PrimaryInfoCard
              title={detail.title}
              fileType={detail.fileType}
              owner={detail.owner}
              date={detail.date}
            />
          </div>
          <div data-tour="details-ai" className="lg:col-span-2">
            <AiMetadataPanel
              analysisStatus={detail.aiAnalysisStatus}
              summary={detail.aiSummary}
              sensitivity={detail.sensitivity}
              tags={detail.tags}
              canEdit={canEditMetadata}
              isSaving={isSavingMetadata}
              saveError={saveMetadataError}
              onSave={handleSaveMetadata}
            />
          </div>
          <div className="lg:col-span-2">
            <UserMetadataPanel
              entries={detail.userMetadata}
              canEdit={canEditMetadata}
              isSaving={isSavingUserMetadata}
              saveError={saveUserMetadataError}
              onSave={handleSaveUserMetadata}
            />
          </div>
          <ProcessingTimeline steps={detail.processing} />
          <div data-tour="details-versions">
            <VersionHistory
              versions={detail.versions}
              fileId={detail.id}
              scope={detail.sharingScope}
              onUploaded={() => id && void dispatch(loadDocumentDetail(id))}
            />
          </div>
          <div data-tour="details-share" className="lg:col-span-2">
            <SharingPanel
              shares={shares}
              isLoadingShares={sharesStatus === 'loading'}
              isCreating={isCreatingShare}
              createError={createShareError}
              onCreateShare={handleCreateShare}
              canManage={canEditMetadata}
              isMutating={isMutatingShare}
              mutationError={shareMutationError}
              onRemoveShare={handleRemoveShare}
              onUpdatePermission={handleUpdatePermission}
              linkUrl={documentPageUrl(detail.id)}
            />
          </div>

          <div className="lg:col-span-2">
            <button
              type="button"
              onClick={() => void handleDownload()}
              disabled={isDownloading}
              className="flex w-full items-center justify-center gap-2 rounded-ds-lg border border-ds-tertiary-container bg-ds-tertiary px-6 py-3 font-ds-heading text-ds-body-md font-semibold text-ds-on-tertiary shadow-sm transition-all duration-200 hover:bg-ds-tertiary-container hover:shadow-md active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60"
            >
              <Icon
                name={isDownloading ? 'hourglass_empty' : 'download'}
                filled={false}
                className="text-[20px]"
              />
              {isDownloading ? 'Downloading…' : 'Download Document'}
            </button>
            {downloadError ? (
              <p className="mt-2 font-ds-body text-ds-body-sm text-ds-error">{downloadError}</p>
            ) : null}
          </div>
        </div>
      </div>

      {confirmDelete ? (
        <ConfirmDialog
          title="Delete file"
          message={
            deleteFileError ??
            `Permanently delete "${detail.title}" and all its versions? This cannot be undone.`
          }
          confirmLabel="Delete"
          destructive
          isBusy={isDeletingFile}
          onConfirm={handleConfirmDelete}
          onCancel={() => setConfirmDelete(false)}
        />
      ) : null}
    </div>
  )
}
