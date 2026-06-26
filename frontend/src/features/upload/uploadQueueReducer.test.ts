import { describe, expect, it } from 'vitest'
import {
  deriveCounts,
  uploadQueueReducer,
  type UploadAction,
} from './uploadQueueReducer'
import type { UploadItem } from './types'

function makeItem(overrides: Partial<UploadItem> = {}): UploadItem {
  return {
    id: 'a',
    fileId: 'a',
    name: 'file.pdf',
    icon: 'picture_as_pdf',
    iconClassName: 'text-ds-error',
    status: 'uploading',
    progress: 0,
    confirmedProgress: 0,
    detailPrimary: '',
    detailSecondary: '',
    ...overrides,
  }
}

describe('uploadQueueReducer', () => {
  it('appends added items without mutating the previous array', () => {
    const initial: UploadItem[] = []
    const next = uploadQueueReducer(initial, {
      type: 'add',
      items: [makeItem({ id: 'a', fileId: 'a' })],
    })
    expect(next).toHaveLength(1)
    expect(initial).toHaveLength(0)
  })

  it('patches only the targeted item and returns a new object', () => {
    const state = [makeItem({ id: 'a' }), makeItem({ id: 'b', fileId: 'b' })]
    const next = uploadQueueReducer(state, {
      type: 'patch',
      id: 'b',
      changes: { status: 'completed', progress: 1 },
    })
    expect(next[0]).toBe(state[0]) // untouched item keeps identity
    expect(next[1]).not.toBe(state[1])
    expect(next[1].status).toBe('completed')
    expect(next[1].progress).toBe(1)
  })

  it('removes an item by id', () => {
    const state = [makeItem({ id: 'a' }), makeItem({ id: 'b', fileId: 'b' })]
    const next = uploadQueueReducer(state, { type: 'remove', id: 'a' })
    expect(next.map((i) => i.id)).toEqual(['b'])
  })

  it('clears only completed items', () => {
    const state = [
      makeItem({ id: 'a', status: 'completed' }),
      makeItem({ id: 'b', fileId: 'b', status: 'uploading' }),
      makeItem({ id: 'c', fileId: 'c', status: 'failed' }),
    ]
    const next = uploadQueueReducer(state, { type: 'clearCompleted' })
    expect(next.map((i) => i.id)).toEqual(['b', 'c'])
  })

  it('returns the same state for an unknown action', () => {
    const state = [makeItem()]
    const next = uploadQueueReducer(state, { type: 'noop' } as unknown as UploadAction)
    expect(next).toBe(state)
  })
})

describe('deriveCounts', () => {
  it('returns zeros for an empty queue', () => {
    expect(deriveCounts([])).toEqual({ total: 0, completed: 0, progress: 0 })
  })

  it('averages per-item progress and counts completed', () => {
    const counts = deriveCounts([
      makeItem({ id: 'a', status: 'completed', progress: 1 }),
      makeItem({ id: 'b', fileId: 'b', status: 'uploading', progress: 0.5 }),
    ])
    expect(counts.total).toBe(2)
    expect(counts.completed).toBe(1)
    expect(counts.progress).toBeCloseTo(0.75)
  })
})
