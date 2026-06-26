import { configureStore } from '@reduxjs/toolkit'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import authReducer from '@/features/auth/authSlice'
import documentsReducer from '@/features/documents/documentsSlice'
import documentDetailsReducer from '@/features/documents/documentDetailsSlice'
import {
  getFiles,
  SharingScope,
  type FileDTO,
  type PageResponse,
} from '@/features/documents/documentsApi'
import DocumentLibraryPage from './DocumentLibraryPage'

vi.mock('@/features/documents/documentsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/documents/documentsApi')>()
  return { ...actual, getFiles: vi.fn() }
})
const mockedGetFiles = vi.mocked(getFiles)

// loadDocuments also fetches AI statuses; stub it so the page tests don't hit
// the network and stay focused on file rendering / filtering.
vi.mock('@/features/documents/jobsApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/documents/jobsApi')>()
  return { ...actual, getJobStatuses: vi.fn().mockResolvedValue([]) }
})

const ALL_SCOPES = [
  SharingScope.PERSONAL,
  SharingScope.DEPARTMENT,
  SharingScope.ORGANIZATION,
]

// Default getFiles params for a no-filter page request.
const defaultParams = (page: number) => ({
  page,
  size: 12,
  contentType: undefined,
  dateFrom: undefined,
  dateTo: undefined,
  sort: 'createdAt,desc',
  sharingScope: ALL_SCOPES,
})

function makePage(content: FileDTO[], overrides: Partial<PageResponse<FileDTO>> = {}) {
  return {
    content,
    totalElements: content.length,
    totalPages: 1,
    size: 12,
    number: 0,
    first: true,
    last: true,
    empty: content.length === 0,
    ...overrides,
  } satisfies PageResponse<FileDTO>
}

function renderPage(initialEntry = '/documents') {
  const store = configureStore({
    reducer: {
      auth: authReducer,
      documents: documentsReducer,
      documentDetails: documentDetailsReducer,
    },
  })
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <DocumentLibraryPage />
      </MemoryRouter>
    </Provider>,
  )
  return store
}

const fileDto: FileDTO = {
  id: 'f1',
  fileName: 'Annual Report.pdf',
  contentType: 'application/pdf',
  version: 1,
  createdAt: '2023-10-20T09:00:00Z',
  ownerName: 'Alex',
}

afterEach(() => {
  vi.clearAllMocks()
})

describe('DocumentLibraryPage', () => {
  it('fetches page 0 on mount and renders the returned files', async () => {
    mockedGetFiles.mockResolvedValue(makePage([fileDto], { totalElements: 1 }))
    renderPage()

    expect(mockedGetFiles).toHaveBeenCalledWith(defaultParams(0))
    expect(await screen.findByText('Annual Report.pdf')).toBeInTheDocument()
    expect(screen.getByText('Showing 1 document')).toBeInTheDocument()
  })

  it('shows the empty-state copy when no files come back', async () => {
    mockedGetFiles.mockResolvedValue(makePage([]))
    renderPage()
    expect(
      await screen.findByText('No documents match the current filters.'),
    ).toBeInTheDocument()
  })

  it('shows an error with a working Retry button', async () => {
    mockedGetFiles.mockRejectedValueOnce(new Error('network down'))
    mockedGetFiles.mockResolvedValueOnce(makePage([fileDto], { totalElements: 1 }))
    renderPage()

    const retry = await screen.findByRole('button', { name: /retry/i })
    await userEvent.click(retry)

    expect(await screen.findByText('Annual Report.pdf')).toBeInTheDocument()
  })

  it('loads the next page when Next is clicked', async () => {
    mockedGetFiles.mockResolvedValue(
      makePage([fileDto], { totalElements: 30, totalPages: 3, last: false }),
    )
    renderPage()

    const next = await screen.findByRole('button', { name: /next/i })
    await userEvent.click(next)

    await waitFor(() =>
      expect(mockedGetFiles).toHaveBeenLastCalledWith(defaultParams(1)),
    )
  })

  it('initializes filters and page from the URL query string', async () => {
    mockedGetFiles.mockResolvedValue(
      makePage([fileDto], { totalElements: 30, totalPages: 3, number: 1, first: false, last: false }),
    )
    renderPage('/documents?page=2&type=pdf&search=budget')

    await waitFor(() =>
      expect(mockedGetFiles).toHaveBeenLastCalledWith({
        ...defaultParams(1),
        contentType: 'application/pdf',
        search: 'budget',
      }),
    )
    expect(screen.getByPlaceholderText(/search within library/i)).toHaveValue('budget')
  })

  it('re-fetches page 0 with a contentType filter when a File Type is picked', async () => {
    mockedGetFiles.mockResolvedValue(makePage([fileDto], { totalElements: 1 }))
    renderPage()

    await screen.findByText('Annual Report.pdf')
    // Exact name: the card's favorite star ("Favorite Annual Report.pdf")
    // would also match a loose /pdf/i query.
    await userEvent.click(screen.getByRole('button', { name: 'PDF' }))

    await waitFor(() =>
      expect(mockedGetFiles).toHaveBeenLastCalledWith({
        ...defaultParams(0),
        contentType: 'application/pdf',
      }),
    )
  })

  it('drops the exclusion list when switching from "Other" back to a known type', async () => {
    mockedGetFiles.mockResolvedValue(makePage([fileDto], { totalElements: 1 }))
    renderPage()

    await screen.findByText('Annual Report.pdf')

    await userEvent.click(screen.getByRole('button', { name: 'Other' }))
    await waitFor(() =>
      expect(mockedGetFiles).toHaveBeenLastCalledWith({
        ...defaultParams(0),
        excludeContentTypes: ['application/pdf', 'word', 'text/plain'],
      }),
    )

    await userEvent.click(screen.getByRole('button', { name: 'PDF' }))
    // The request must carry contentType alone — not contentType *and* the stale
    // exclusion list, which would ask for PDFs while excluding them (empty result).
    await waitFor(() =>
      expect(mockedGetFiles).toHaveBeenLastCalledWith({
        ...defaultParams(0),
        contentType: 'application/pdf',
        excludeContentTypes: undefined,
      }),
    )
  })

  it('debounces the search box into a server-side query from page 0', async () => {
    mockedGetFiles.mockResolvedValue(makePage([fileDto], { totalElements: 1 }))
    renderPage()

    await screen.findByText('Annual Report.pdf')

    await userEvent.type(
      screen.getByPlaceholderText(/search within library/i),
      'budget',
    )

    // The query is debounced, so the eventual request carries the full term and
    // re-fetches from page 0 (not a request per keystroke is asserted in the hook test).
    await waitFor(() =>
      expect(mockedGetFiles).toHaveBeenLastCalledWith({
        ...defaultParams(0),
        search: 'budget',
      }),
    )
  })

  it('clearing all filters also empties the search box', async () => {
    mockedGetFiles.mockResolvedValue(makePage([fileDto], { totalElements: 1 }))
    renderPage()

    await screen.findByText('Annual Report.pdf')
    const searchBox = screen.getByPlaceholderText(/search within library/i)
    await userEvent.type(searchBox, 'budget')
    await waitFor(() => expect(searchBox).toHaveValue('budget'))

    await userEvent.click(screen.getByRole('button', { name: /clear/i }))

    expect(searchBox).toHaveValue('')
  })
})
