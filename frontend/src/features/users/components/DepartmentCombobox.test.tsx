import { render, screen, fireEvent } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DepartmentCombobox from './DepartmentCombobox'
import type { DepartmentDTO } from '../usersApi'

const DEPARTMENTS: DepartmentDTO[] = [
  { name: 'Engineering', userCount: 5 },
  { name: 'Finance', userCount: 3 },
]

function renderCombobox(overrides: Partial<Parameters<typeof DepartmentCombobox>[0]> = {}) {
  const onChange = vi.fn()
  const onFocusLoad = vi.fn()
  const { rerender } = render(
    <DepartmentCombobox
      value=""
      onChange={onChange}
      departments={DEPARTMENTS}
      isLoading={false}
      loadError={null}
      onFocusLoad={onFocusLoad}
      inputClassName="input"
      {...overrides}
    />,
  )
  const input = screen.getByRole('combobox')
  return { input, onChange, onFocusLoad, rerender }
}

describe('DepartmentCombobox', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads departments on focus and opens the browse list', () => {
    const { input, onFocusLoad } = renderCombobox()
    fireEvent.focus(input)
    expect(onFocusLoad).toHaveBeenCalled()
    expect(screen.getByText('Engineering')).toBeInTheDocument()
    expect(screen.getByText('Finance')).toBeInTheDocument()
  })

  it('filters suggestions as the user types', () => {
    const { input, onChange } = renderCombobox({ value: 'fin' })
    fireEvent.focus(input)
    expect(screen.getByText('Finance')).toBeInTheDocument()
    expect(screen.queryByText('Engineering')).not.toBeInTheDocument()
    // Typing reports the raw text up to the parent (free-text value).
    fireEvent.change(input, { target: { value: 'finn' } })
    expect(onChange).toHaveBeenLastCalledWith('finn')
  })

  it('picking a suggestion sets the value to its name', () => {
    const { input, onChange } = renderCombobox()
    fireEvent.focus(input)
    fireEvent.click(screen.getByText('Engineering'))
    expect(onChange).toHaveBeenLastCalledWith('Engineering')
  })

  it('selects the highlighted suggestion with the keyboard', () => {
    const { input, onChange } = renderCombobox()
    fireEvent.focus(input)
    fireEvent.keyDown(input, { key: 'ArrowDown' }) // move to second option
    fireEvent.keyDown(input, { key: 'Enter' })
    expect(onChange).toHaveBeenLastCalledWith('Finance')
  })

  it('shows the member count next to each suggestion', () => {
    const { input } = renderCombobox()
    fireEvent.focus(input)
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  it('surfaces a load error in the dropdown', () => {
    const { input } = renderCombobox({ loadError: 'boom' })
    fireEvent.focus(input)
    expect(screen.getByText(/Couldn't load departments/)).toBeInTheDocument()
  })
})
