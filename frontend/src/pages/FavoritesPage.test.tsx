import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import FavoritesPage from './FavoritesPage'
import type { FavoriteDocument } from '@/features/favorites/types'

const downloadMock = vi.fn()
const removeFavoriteMock = vi.fn().mockResolvedValue(undefined)

let favoritesState: {
  documents: FavoriteDocument[]
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

vi.mock('@/features/favorites/useFavorites', () => ({
  useFavorites: () => ({ ...favoritesState, goToPage: vi.fn(), reload: vi.fn() }),
}))

vi.mock('@/features/favorites/useFavoriteDownload', () => ({
  useFavoriteDownload: () => ({ downloadingId: null, error: null, download: downloadMock }),
}))

vi.mock('@/features/documents/detailsApi', () => ({
  removeFavorite: (...args: unknown[]) => removeFavoriteMock(...args),
}))

function doc(fileId: string, title: string, version: number): FavoriteDocument {
  return {
    id: `${fileId}-v${version}`,
    fileId,
    title,
    version,
    versionLabel: `v${version}`,
    fileType: 'pdf',
    typeLabel: 'PDF Document',
    size: '2.4 MB',
    owner: 'Sarah Jenkins',
    favoritedDate: 'Oct 12, 2023',
  }
}

const DOCS = [doc('1', 'Q4 Projections', 3), doc('1', 'Q4 Projections (old)', 1), doc('2', 'Roadmap', 2)]

function renderPage() {
  return render(
    <MemoryRouter>
      <FavoritesPage />
    </MemoryRouter>,
  )
}

describe('FavoritesPage', () => {
  beforeEach(() => {
    downloadMock.mockClear()
    removeFavoriteMock.mockClear()
    favoritesState = {
      documents: DOCS,
      status: 'succeeded',
      error: null,
      pageInfo: { page: 0, totalElements: 3, totalPages: 1, isFirst: true, isLast: true },
    }
  })

  it('renders all favorited versions, including two versions of the same file', () => {
    renderPage()
    expect(screen.getByText('Q4 Projections')).toBeInTheDocument()
    expect(screen.getByText('Q4 Projections (old)')).toBeInTheDocument()
    expect(screen.getByText('Roadmap')).toBeInTheDocument()
    expect(screen.getByText('Showing 3 of 3 favorites')).toBeInTheDocument()
  })

  it('filters by the search box', () => {
    renderPage()
    fireEvent.change(screen.getByPlaceholderText('Search favorites…'), {
      target: { value: 'road' },
    })
    expect(screen.getByText('Roadmap')).toBeInTheDocument()
    expect(screen.queryByText('Q4 Projections')).not.toBeInTheDocument()
  })

  it('shows an empty state when nothing is favorited', () => {
    favoritesState = {
      documents: [],
      status: 'succeeded',
      error: null,
      pageInfo: { page: 0, totalElements: 0, totalPages: 0, isFirst: true, isLast: true },
    }
    renderPage()
    expect(
      screen.getByText('No favorite versions yet — star one from a document’s version history.'),
    ).toBeInTheDocument()
  })

  it('shows the error state with a retry button when loading fails', () => {
    favoritesState = {
      documents: [],
      status: 'failed',
      error: 'Network down',
      pageInfo: { page: 0, totalElements: 0, totalPages: 0, isFirst: true, isLast: true },
    }
    renderPage()
    expect(screen.getByText('Network down')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('triggers a versioned download from the row action', () => {
    renderPage()
    const row = screen.getByText('Roadmap').closest('tr') as HTMLTableRowElement
    fireEvent.click(within(row).getByLabelText('Download v2'))
    expect(downloadMock).toHaveBeenCalledWith(expect.objectContaining({ fileId: '2', version: 2 }))
  })

  it('removes a favorite from the row action', async () => {
    renderPage()
    const row = screen.getByText('Roadmap').closest('tr') as HTMLTableRowElement
    fireEvent.click(within(row).getByLabelText('Unfavorite v2'))
    await waitFor(() => expect(removeFavoriteMock).toHaveBeenCalledWith('2', 2))
  })
})
