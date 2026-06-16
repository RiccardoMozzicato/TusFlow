import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import LibraryPager from './LibraryPager'

const baseProps = {
  pageNumber: 1,
  totalPages: 3,
  isFirst: false,
  isLast: false,
  onPrev: () => {},
  onNext: () => {},
}

describe('LibraryPager', () => {
  it('renders nothing when there is one page or fewer', () => {
    const { container } = render(<LibraryPager {...baseProps} totalPages={1} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('shows the human-friendly (1-based) page position', () => {
    render(<LibraryPager {...baseProps} />)
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument()
  })

  it('disables Previous on the first page and Next on the last', () => {
    const { rerender } = render(<LibraryPager {...baseProps} isFirst />)
    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /next/i })).toBeEnabled()

    rerender(<LibraryPager {...baseProps} isLast />)
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled()
  })

  it('invokes the navigation callbacks on click', async () => {
    const onPrev = vi.fn()
    const onNext = vi.fn()
    render(<LibraryPager {...baseProps} onPrev={onPrev} onNext={onNext} />)

    await userEvent.click(screen.getByRole('button', { name: /previous/i }))
    await userEvent.click(screen.getByRole('button', { name: /next/i }))

    expect(onPrev).toHaveBeenCalledOnce()
    expect(onNext).toHaveBeenCalledOnce()
  })
})
