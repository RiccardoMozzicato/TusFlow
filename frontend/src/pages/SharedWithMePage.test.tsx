import { render, screen, fireEvent, within, act } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SharedWithMePage from './SharedWithMePage'
import type { SharedDocument, SharePermission } from '@/features/shared/types'

const downloadMock = vi.fn()

let sharedState: {
  documents: SharedDocument[]
  status: 'loading' | 'succeeded' | 'failed'
  error: string | null
  pageInfo: {
    page: number
    totalElements: number
    totalPages: number
    isFirst: boolean
    isLast: boolean
  }
}

// The page owns filters/page via the URL and passes them to the hook, which only
// fetches. We assert the URL the page writes, so the hook is stubbed to ignore
// its inputs and return the canned state.
vi.mock('@/features/shared/useSharedFiles', async () => {
  const actual = await vi.importActual<typeof import('@/features/shared/useSharedFiles')>(
    '@/features/shared/useSharedFiles',
  )
  return {
    ...actual,
    useSharedFiles: () => ({ ...sharedState, reload: vi.fn() }),
  }
})

vi.mock('@/features/shared/useSharedDownload', () => ({
  useSharedDownload: () => ({ downloadingId: null, error: null, download: downloadMock }),
}))

// The owner filter lazily loads org users; stub it so the page renders offline.
vi.mock('@/features/users/useOrgUsers', () => ({
  useOrgUsers: () => ({ users: [], isLoading: false, error: null, load: vi.fn() }),
}))

function doc(id: string, title: string, permission: SharePermission): SharedDocument {
  return {
    id,
    title,
    fileType: 'pdf',
    typeLabel: 'PDF Document',
    size: '2.4 MB',
    owner: 'Sarah Jenkins',
    sharedDate: 'Oct 12, 2023',
    permission,
  }
}

const DOCS = [
  doc('1', 'Q4 Projections', 'edit'),
  doc('2', 'Strategy', 'view'),
  doc('3', 'Roadmap', 'edit'),
]

// Surfaces the router's current query string so tests can assert what the page
// writes into the URL (its single source of truth).
let currentSearch = ''
function LocationProbe() {
  currentSearch = useLocation().search
  return null
}

function renderPage(initialEntry = '/') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <SharedWithMePage />
      <LocationProbe />
    </MemoryRouter>,
  )
}

describe('SharedWithMePage', () => {
  beforeEach(() => {
    downloadMock.mockClear()
    currentSearch = ''
    sharedState = {
      documents: DOCS,
      status: 'succeeded',
      error: null,
      pageInfo: { page: 0, totalElements: 3, totalPages: 1, isFirst: true, isLast: true },
    }
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders all shared documents by default', () => {
    renderPage()
    expect(screen.getByText('Q4 Projections')).toBeInTheDocument()
    expect(screen.getByText('Strategy')).toBeInTheDocument()
    expect(screen.getByText('Roadmap')).toBeInTheDocument()
    expect(screen.getByText('Showing 3 of 3 shared documents')).toBeInTheDocument()
  })

  it('writes perm=edit to the URL when only Can edit is left checked', () => {
    renderPage()
    // Both boxes start checked (= any). Unchecking "Can view" leaves just edit.
    fireEvent.click(screen.getByRole('checkbox', { name: 'Can view' }))
    expect(currentSearch).toBe('?perm=edit')
  })

  it('writes an empty perm (show nothing) when both boxes are unchecked', () => {
    // Start with only "Can edit" checked via the URL.
    renderPage('/?perm=edit')
    fireEvent.click(screen.getByRole('checkbox', { name: 'Can edit' }))
    expect(currentSearch).toBe('?perm=')
  })

  it('commits the debounced search query into the URL', () => {
    vi.useFakeTimers()
    renderPage()
    fireEvent.change(screen.getByPlaceholderText('Search shared documents…'), {
      target: { value: 'road' },
    })
    // The query is only committed after the debounce window elapses.
    expect(currentSearch).toBe('')
    act(() => {
      vi.advanceTimersByTime(400)
    })
    expect(currentSearch).toBe('?search=road')
  })

  it('seeds the search box from the URL', () => {
    renderPage('/?search=strategy')
    expect(screen.getByPlaceholderText('Search shared documents…')).toHaveValue('strategy')
  })

  it('shows an empty state when nothing is shared', () => {
    sharedState = {
      documents: [],
      status: 'succeeded',
      error: null,
      pageInfo: { page: 0, totalElements: 0, totalPages: 0, isFirst: true, isLast: true },
    }
    renderPage()
    expect(screen.getByText('No documents have been shared with you yet.')).toBeInTheDocument()
  })

  it('shows a search-specific empty state when a query matches nothing', () => {
    sharedState = {
      documents: [],
      status: 'succeeded',
      error: null,
      pageInfo: { page: 0, totalElements: 0, totalPages: 0, isFirst: true, isLast: true },
    }
    renderPage('/?search=nope')
    expect(screen.getByText('No documents match your search.')).toBeInTheDocument()
  })

  it('triggers a download from the row action', () => {
    renderPage()
    const row = screen.getByText('Q4 Projections').closest('tr') as HTMLTableRowElement
    fireEvent.click(within(row).getByLabelText('Download'))
    expect(downloadMock).toHaveBeenCalledWith('1', 'Q4 Projections')
  })
})
