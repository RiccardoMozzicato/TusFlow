import { useEffect, useRef, useState } from 'react'
import { getFiles, SharingScope } from '@/features/documents/documentsApi'
import { onJobUpdate } from '@/features/realtime/jobsSocket'
import { getJobStats } from './dashboardApi'
import { buildStatCards, mapFileToDocumentRow, startOfTodayIso } from './mappers'
import type { DocumentRow, StatCard } from './types'

// Rows shown in the Recent Documents table.
const RECENT_PAGE_SIZE = 4

// Coalesce a burst of live job updates into a single refetch.
const REFRESH_DEBOUNCE_MS = 500

// The dashboard is an overview, so count files across every visibility scope.
const ALL_SCOPES = [
  SharingScope.PERSONAL,
  SharingScope.DEPARTMENT,
  SharingScope.ORGANIZATION,
]

export interface DashboardData {
  /** Null while loading or when the fetch failed. */
  stats: StatCard[] | null
  /** Null while loading or when the fetch failed. */
  recentDocuments: DocumentRow[] | null
  hasError: boolean
}

/**
 * Loads the dashboard's live data: one files page (recent rows + total count)
 * and the aggregate job counters, fetched in parallel.
 */
export function useDashboardData(): DashboardData {
  const [data, setData] = useState<DashboardData>({
    stats: null,
    recentDocuments: null,
    hasError: false,
  })
  // Bumped to re-run the fetch when a live job update lands (debounced).
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    let isActive = true

    Promise.all([
      getFiles({ size: RECENT_PAGE_SIZE, sharingScope: ALL_SCOPES }),
      getJobStats(startOfTodayIso()),
    ])
      .then(([filesPage, jobStats]) => {
        if (!isActive) return
        setData({
          stats: buildStatCards(filesPage.totalElements, jobStats),
          recentDocuments: filesPage.content.map(mapFileToDocumentRow),
          hasError: false,
        })
      })
      .catch(() => {
        if (!isActive) return
        setData({ stats: null, recentDocuments: null, hasError: true })
      })

    return () => {
      isActive = false
    }
  }, [reloadToken])

  // A terminal AI-job update changes both the counters and the recent rows, so
  // refetch (debounced) to keep the overview in sync without polling.
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => {
    const unsubscribe = onJobUpdate(() => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
      debounceRef.current = setTimeout(() => setReloadToken((token) => token + 1), REFRESH_DEBOUNCE_MS)
    })
    return () => {
      unsubscribe()
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [])

  return data
}
