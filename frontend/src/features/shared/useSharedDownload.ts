import { useCallback, useState } from 'react'
import { getDownloadBlob } from '@/features/documents/detailsApi'
import { saveBlob } from '@/lib/download'

export interface UseSharedDownload {
  /** Id of the file currently downloading, or null when idle. */
  downloadingId: string | null
  /** Last download error message, or null. */
  error: string | null
  download: (id: string, filename: string) => void
}

// Downloads the current version of a shared file, tracking which row is in
// flight so its action button can show a spinner. One download at a time.
export function useSharedDownload(): UseSharedDownload {
  const [downloadingId, setDownloadingId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const download = useCallback(
    (id: string, filename: string) => {
      if (downloadingId != null) return
      setDownloadingId(id)
      setError(null)
      getDownloadBlob(id)
        .then((blob) => saveBlob(blob, filename))
        .catch((err) => {
          setError(err instanceof Error ? err.message : 'Failed to download document')
        })
        .finally(() => setDownloadingId(null))
    },
    [downloadingId],
  )

  return { downloadingId, error, download }
}
