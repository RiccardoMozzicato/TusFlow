import { useCallback, useState } from 'react'
import { getDownloadBlob } from '@/features/documents/detailsApi'
import { saveBlob } from '@/lib/download'
import type { FavoriteDocument } from './types'

export interface UseFavoriteDownload {
  /** Row key (fileId + version) currently downloading, or null when idle. */
  downloadingId: string | null
  /** Last download error message, or null. */
  error: string | null
  download: (doc: FavoriteDocument) => void
}

// Downloads the specific favorited version, tracking which row is in flight so
// its action button can show a spinner. One download at a time.
export function useFavoriteDownload(): UseFavoriteDownload {
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const download = useCallback(
    (doc: FavoriteDocument) => {
      if (downloadingId != null) return
      setDownloadingId(doc.id)
      setError(null)
      getDownloadBlob(doc.fileId, doc.version)
        .then((blob) => saveBlob(blob, doc.title))
        .catch((err) => {
          setError(err instanceof Error ? err.message : 'Failed to download document')
        })
        .finally(() => setDownloadingId(null))
    },
    [downloadingId],
  )

  return { downloadingId, error, download }
}
