import { render, screen, fireEvent, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SharedWithMePage from './SharedWithMePage'
import type { SharedDocument, SharePermission } from '@/features/shared/types'
import type { SharedFilters } from '@/features/shared/useSharedFiles'

const downloadMock = vi.fn()
const setFiltersMock = vi.fn()
const resetFiltersMock = vi.fn()

const DEFAULT_FILTERS: SharedFilters = { sort: 'createdAt,desc', permissions: ['edit', 'view'] }

let sharedState: {
  documents: SharedDocument[]
  status: 'loading' | 'succeeded' | 'failed'
  error: string | null
  filters: SharedFilters
  pageInfo: {
    page: number
    totalElements: number
    totalPages: number
    isFirst: boolean
    isLast: boolean
  }
}

vi.mock('@/features/shared/useSharedFiles', () => ({
  useSharedFiles: () => ({
    ...sharedState,
    setFilters: setFiltersMock,
    resetFilters: resetFiltersMock,
    goToPage: vi.fn(),
    reload: vi.fn(),
  }),
}))

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

function renderPage() {
  return render(
    <MemoryRouter>
      <SharedWithMePage />
    </MemoryRouter>,
  )
}

describe('SharedWithMePage', () => {
  beforeEach(() => {
    downloadMock.mockClear()
    setFiltersMock.mockClear()
    resetFiltersMock.mockClear()
    sharedState = {
      documents: DOCS,
      status: 'succeeded',
      error: null,
      filters: DEFAULT_FILTERS,
      pageInfo: { page: 0, totalElements: 3, totalPages: 1, isFirst: true, isLast: true },
    }
  })

  it('renders all shared documents by default', () => {
    renderPage()
    expect(screen.getByText('Q4 Projections')).toBeInTheDocument()
    expect(screen.getByText('Strategy')).toBeInTheDocument()
    expect(screen.getByText('Roadmap')).toBeInTheDocument()
    expect(screen.getByText('Showing 3 of 3 shared documents')).toBeInTheDocument()
  })

  it('narrows to editable shares when only Can edit is left checked', () => {
    renderPage()
    // Both boxes start checked (= any). Unchecking "Can view" leaves just edit.
    fireEvent.click(screen.getByRole('checkbox', { name: 'Can view' }))
    expect(setFiltersMock).toHaveBeenCalledWith({ permissions: ['edit'] })
  })

  it('allows unchecking both permission boxes (empty set)', () => {
    sharedState.filters = { sort: 'createdAt,desc', permissions: ['edit'] }
    renderPage()
    // Only "Can edit" is checked; unchecking it must leave an empty set, not snap back.
    fireEvent.click(screen.getByRole('checkbox', { name: 'Can edit' }))
    expect(setFiltersMock).toHaveBeenCalledWith({ permissions: [] })
  })

  it('filters by the search box', () => {
    renderPage()
    fireEvent.change(screen.getByPlaceholderText('Search shared documents…'), {
      target: { value: 'road' },
    })
    expect(screen.getByText('Roadmap')).toBeInTheDocument()
    expect(screen.queryByText('Q4 Projections')).not.toBeInTheDocument()
  })

  it('shows an empty state when nothing is shared', () => {
    sharedState = {
      documents: [],
      status: 'succeeded',
      error: null,
      filters: DEFAULT_FILTERS,
      pageInfo: { page: 0, totalElements: 0, totalPages: 0, isFirst: true, isLast: true },
    }
    renderPage()
    expect(
      screen.getByText('No documents have been shared with you yet.'),
    ).toBeInTheDocument()
  })

  it('triggers a download from the row action', () => {
    renderPage()
    const row = screen.getByText('Q4 Projections').closest('tr') as HTMLTableRowElement
    fireEvent.click(within(row).getByLabelText('Download'))
    expect(downloadMock).toHaveBeenCalledWith('1', 'Q4 Projections')
  })
})
