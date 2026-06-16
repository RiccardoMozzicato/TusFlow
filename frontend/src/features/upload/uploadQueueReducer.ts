import type { UploadItem, UploadStatus } from './types'

// Pure state for the upload queue. Kept separate from the hook so the
// transitions can be unit-tested without React or the tus/SSE side effects.

export type UploadAction =
  | { type: 'add'; items: UploadItem[] }
  | {
      type: 'patch'
      id: string
      changes: Partial<Omit<UploadItem, 'id' | 'fileId'>>
    }
  | { type: 'remove'; id: string }
  | { type: 'clearCompleted' }

// Statuses that "Clear Completed" should sweep away.
const CLEARABLE: ReadonlySet<UploadStatus> = new Set<UploadStatus>(['completed'])

export function uploadQueueReducer(
  state: UploadItem[],
  action: UploadAction,
): UploadItem[] {
  switch (action.type) {
    case 'add':
      return [...state, ...action.items]

    case 'patch':
      return state.map((item) =>
        item.id === action.id ? { ...item, ...action.changes } : item,
      )

    case 'remove':
      return state.filter((item) => item.id !== action.id)

    case 'clearCompleted':
      return state.filter((item) => !CLEARABLE.has(item.status))

    default:
      return state
  }
}

export interface QueueCounts {
  total: number
  completed: number
  /** Average per-item progress (0–1) across the whole queue. */
  progress: number
}

/** Derives the summary counters from the queue (pure; speed added by the hook). */
export function deriveCounts(items: UploadItem[]): QueueCounts {
  const total = items.length
  if (total === 0) return { total: 0, completed: 0, progress: 0 }

  const completed = items.filter((item) => item.status === 'completed').length
  const progressSum = items.reduce((sum, item) => sum + item.progress, 0)

  return { total, completed, progress: progressSum / total }
}
