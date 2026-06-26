import { useRef, useState } from 'react'
import Icon from '@/components/Icon'
import type { SharingScope } from '../../documentsApi'
import type { DocumentVersion } from '../../types'
import { useVersionUpload } from '../../useVersionUpload'
import { useVersionDownload } from '../../useVersionDownload'
import { useVersionFavorites } from '../../useVersionFavorites'
import DetailSection from './DetailSection'

// How many versions to show before "View All" expands the rest.
const COLLAPSED_COUNT = 3

interface VersionHistoryProps {
  versions: DocumentVersion[]
  /** Document UUID the new version is attached to. */
  fileId: string
  /** The file's current sharing scope, reused for the upload authorization. */
  scope: SharingScope
  /** Called after a new version finishes uploading, to refresh the detail. */
  onUploaded: () => void
}

// Per-row download control. Shows a spinner while this version is downloading,
// and stays revealed on hover/focus so the row reads as actionable.
function DownloadButton({
  version,
  isDownloading,
  onDownload,
}: {
  version: DocumentVersion
  isDownloading: boolean
  onDownload: () => void
}) {
  return (
    <button
      type="button"
      onClick={onDownload}
      disabled={isDownloading}
      aria-label={`Download ${version.label}`}
      title={`Download ${version.label}`}
      className="flex h-7 w-7 items-center justify-center rounded-ds text-ds-secondary opacity-0 transition-all duration-200 hover:bg-ds-primary-fixed/40 hover:text-ds-primary focus-visible:opacity-100 group-hover:opacity-100 disabled:opacity-100"
    >
      <Icon
        name={isDownloading ? 'progress_activity' : 'download'}
        filled={false}
        className={`text-[16px] ${isDownloading ? 'animate-spin' : ''}`}
      />
    </button>
  )
}

// Per-row favorite toggle. A favorited star stays visible (pinned versions
// should be obvious at a glance); an unfavorited one is revealed on hover.
function FavoriteButton({
  version,
  isFavorited,
  onToggle,
}: {
  version: DocumentVersion
  isFavorited: boolean
  onToggle: () => void
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={isFavorited}
      aria-label={isFavorited ? `Unfavorite ${version.label}` : `Favorite ${version.label}`}
      title={isFavorited ? `Unfavorite ${version.label}` : `Favorite ${version.label}`}
      className={`flex h-7 w-7 items-center justify-center rounded-ds transition-all duration-200 hover:bg-ds-primary-fixed/40 focus-visible:opacity-100 group-hover:opacity-100 ${
        isFavorited ? 'text-ds-primary opacity-100' : 'text-ds-secondary opacity-0 hover:text-ds-primary'
      }`}
    >
      <Icon name="star" filled={isFavorited} className="text-[16px]" />
    </button>
  )
}

function CurrentVersionRow({
  version,
  isDownloading,
  onDownload,
  isFavorited,
  onToggleFavorite,
}: {
  version: DocumentVersion
  isDownloading: boolean
  onDownload: () => void
  isFavorited: boolean
  onToggleFavorite: () => void
}) {
  return (
    <div className="group flex items-center justify-between rounded-ds border border-ds-primary-fixed-dim bg-ds-surface-container p-2 transition-all duration-200 hover:border-ds-primary hover:shadow-sm">
      <div className="flex items-center gap-2">
        <span className="rounded-ds bg-white px-1.5 py-0.5 font-ds-label text-ds-label-sm font-bold text-ds-primary shadow-sm">
          {version.label}
        </span>
        <span className="font-ds-body text-ds-body-sm text-ds-on-surface">(Current)</span>
      </div>
      <div className="flex items-center gap-1">
        <span className="font-ds-label text-ds-label-sm text-ds-secondary transition-opacity duration-200 group-hover:opacity-0">
          {version.date}
        </span>
        <FavoriteButton version={version} isFavorited={isFavorited} onToggle={onToggleFavorite} />
        <DownloadButton version={version} isDownloading={isDownloading} onDownload={onDownload} />
      </div>
    </div>
  )
}

function OlderVersionRow({
  version,
  isDownloading,
  onDownload,
  isFavorited,
  onToggleFavorite,
}: {
  version: DocumentVersion
  isDownloading: boolean
  onDownload: () => void
  isFavorited: boolean
  onToggleFavorite: () => void
}) {
  return (
    <div className="group flex items-center justify-between rounded-ds p-2 transition-all duration-200 hover:translate-x-0.5 hover:bg-ds-surface">
      <div className="flex items-center gap-2">
        <span className="font-ds-label text-ds-label-sm font-medium text-ds-secondary transition-colors group-hover:text-ds-on-surface">
          {version.label}
        </span>
        <span className="font-ds-body text-ds-body-sm text-ds-secondary transition-colors group-hover:text-ds-on-surface">
          {version.author}
        </span>
      </div>
      <div className="flex items-center gap-1">
        <span className="font-ds-label text-ds-label-sm text-ds-outline transition-opacity duration-200 group-hover:opacity-0">
          {version.date}
        </span>
        <FavoriteButton version={version} isFavorited={isFavorited} onToggle={onToggleFavorite} />
        <DownloadButton version={version} isDownloading={isDownloading} onDownload={onDownload} />
      </div>
    </div>
  )
}

// The new-version control: an upload button that opens a file picker, plus the
// in-flight progress bar / error feedback once a file is chosen.
function UploadVersionControl({
  fileId,
  scope,
  onUploaded,
}: {
  fileId: string
  scope: SharingScope
  onUploaded: () => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const { status, progress, fileName, error, upload, reset } = useVersionUpload(
    fileId,
    scope,
    onUploaded,
  )
  const isUploading = status === 'uploading'

  function handlePick(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    // Reset the input so picking the same file again still fires onChange.
    event.target.value = ''
    if (file) upload(file)
  }

  return (
    <div className="mt-3 border-t border-ds-outline-variant/40 pt-3">
      <input
        ref={inputRef}
        type="file"
        className="hidden"
        onChange={handlePick}
        disabled={isUploading}
      />
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={isUploading}
        className="group flex w-full items-center justify-center gap-2 rounded-ds-lg border border-dashed border-ds-outline-variant bg-ds-surface-container px-4 py-2.5 font-ds-label text-ds-label-md text-ds-on-surface transition-all duration-200 hover:-translate-y-0.5 hover:border-ds-primary hover:bg-ds-primary-fixed/20 hover:text-ds-primary hover:shadow-md active:translate-y-0 active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0 disabled:hover:shadow-none"
      >
        <Icon
          name={isUploading ? 'progress_activity' : 'upload_file'}
          filled={false}
          className={`text-[18px] transition-transform duration-200 ${
            isUploading ? 'animate-spin' : 'group-hover:-translate-y-0.5'
          }`}
        />
        {isUploading ? 'Uploading…' : 'Upload new version'}
      </button>

      {isUploading ? (
        <div className="mt-2">
          <div className="h-1.5 w-full overflow-hidden rounded-ds-full bg-ds-surface-variant">
            <div
              className="h-full rounded-ds-full bg-ds-primary transition-[width] duration-200"
              style={{ width: `${Math.round(progress * 100)}%` }}
            />
          </div>
          <p className="mt-1 truncate font-ds-label text-ds-label-sm text-ds-secondary">
            {fileName} • {Math.round(progress * 100)}%
          </p>
        </div>
      ) : null}

      {status === 'success' ? (
        <p className="mt-2 flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-tertiary">
          <Icon name="check_circle" className="text-[14px]" />
          New version uploaded.
        </p>
      ) : null}

      {status === 'error' ? (
        <div className="mt-2 flex items-start justify-between gap-2 rounded-ds bg-ds-error-container px-3 py-2 font-ds-label text-ds-label-sm text-ds-on-error-container">
          <span className="flex items-start gap-1">
            <Icon name="error" className="mt-0.5 text-[14px]" />
            {error}
          </span>
          <button type="button" onClick={reset} className="shrink-0 font-medium hover:underline">
            Dismiss
          </button>
        </div>
      ) : null}
    </div>
  )
}

// Version list with per-version download and an inline new-version uploader. The
// list collapses to the most recent few; "View All" expands it in place.
export default function VersionHistory({
  versions,
  fileId,
  scope,
  onUploaded,
}: VersionHistoryProps) {
  const [isExpanded, setIsExpanded] = useState(false)
  const { downloadingVersion, error: downloadError, download } = useVersionDownload(fileId)
  const { favoritedVersions, error: favoriteError, toggle } = useVersionFavorites(fileId)

  const hasMore = versions.length > COLLAPSED_COUNT
  const visibleVersions = isExpanded ? versions : versions.slice(0, COLLAPSED_COUNT)

  return (
    <DetailSection
      icon="update"
      title="Versions"
      action={
        hasMore ? (
          <button
            type="button"
            onClick={() => setIsExpanded((prev) => !prev)}
            className="font-ds-label text-ds-label-sm text-ds-primary transition-colors hover:text-ds-primary-container hover:underline"
          >
            {isExpanded ? 'Show less' : `View All (${versions.length})`}
          </button>
        ) : null
      }
    >
      <div className="flex flex-col gap-2">
        {visibleVersions.map((version) => {
          const isDownloading = downloadingVersion === version.version
          const onDownload = () => download(version.version, version.filename)
          const isFavorited = favoritedVersions.has(version.version)
          const onToggleFavorite = () => toggle(version.version)
          return version.isCurrent ? (
            <CurrentVersionRow
              key={version.id}
              version={version}
              isDownloading={isDownloading}
              onDownload={onDownload}
              isFavorited={isFavorited}
              onToggleFavorite={onToggleFavorite}
            />
          ) : (
            <OlderVersionRow
              key={version.id}
              version={version}
              isDownloading={isDownloading}
              onDownload={onDownload}
              isFavorited={isFavorited}
              onToggleFavorite={onToggleFavorite}
            />
          )
        })}
      </div>

      {downloadError ? (
        <p className="mt-2 flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-error">
          <Icon name="error" className="text-[14px]" />
          {downloadError}
        </p>
      ) : null}

      {favoriteError ? (
        <p className="mt-2 flex items-center gap-1 font-ds-label text-ds-label-sm text-ds-error">
          <Icon name="error" className="text-[14px]" />
          {favoriteError}
        </p>
      ) : null}

      <UploadVersionControl fileId={fileId} scope={scope} onUploaded={onUploaded} />
    </DetailSection>
  )
}
