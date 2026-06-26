import { useCallback, useState } from 'react'
import { getDownloadBlob } from './detailsApi'
import { saveBlob } from '@/lib/download'

// Downloads individual file versions from the details page. Tracks which single
// version is in flight so each row can show its own spinner, and surfaces the
// last error for inline feedback.

export interface UseVersionDownload {
  /** Numeric version currently downloading, or null when idle. */
  downloadingVersion: number | null
  /** Last download failure message, or null. */
  error: string | null
  download: (version: number, filename: string) => void
}

export function useVersionDownload(fileId: string): UseVersionDownload {
  const [downloadingVersion, setDownloadingVersion] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const download = useCallback(
    (version: number, filename: string) => {
      // One download at a time keeps the per-row spinner unambiguous.
      if (downloadingVersion != null) return

      setDownloadingVersion(version)
      setError(null)

      getDownloadBlob(fileId, version)
        .then((blob) => saveBlob(blob, filename))
        .catch((err) => {
          setError(err instanceof Error ? err.message : 'Failed to download version')
        })
        .finally(() => {
          setDownloadingVersion(null)
        })
    },
    [fileId, downloadingVersion],
  )

  return { downloadingVersion, error, download }
}
