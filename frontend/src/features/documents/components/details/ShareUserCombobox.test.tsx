import { render, screen, fireEvent } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ShareUserCombobox, { type ShareTarget } from './ShareUserCombobox'
import type { UserDTO } from '@/features/users/usersApi'

function user(id: string, fullName: string, email: string): UserDTO {
  return { id, fullName, email, organization: 'Org', role: 'staff' }
}

const USERS: UserDTO[] = [
  user('11111111-1111-1111-1111-111111111111', 'Alice Johnson', 'alice@acme.io'),
  user('22222222-2222-2222-2222-222222222222', 'Bob Martin', 'bob@acme.io'),
]

function renderCombobox(overrides: Partial<Parameters<typeof ShareUserCombobox>[0]> = {}) {
  const onResolvedChange = vi.fn<(t: ShareTarget | null) => void>()
  const onFocusLoad = vi.fn()
  render(
    <ShareUserCombobox
      users={USERS}
      isLoading={false}
      loadError={null}
      excludeIds={new Set()}
      onFocusLoad={onFocusLoad}
      onResolvedChange={onResolvedChange}
      {...overrides}
    />,
  )
  const input = screen.getByRole('combobox')
  return { input, onResolvedChange, onFocusLoad }
}

describe('ShareUserCombobox', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads users on focus and opens suggestions', () => {
    const { input, onFocusLoad } = renderCombobox()
    fireEvent.focus(input)
    expect(onFocusLoad).toHaveBeenCalled()
    expect(screen.getByText('Alice Johnson')).toBeInTheDocument()
  })

  it('filters by query and resolves the picked user', () => {
    const { input, onResolvedChange } = renderCombobox()
    fireEvent.focus(input)
    fireEvent.change(input, { target: { value: 'bob' } })

    expect(screen.queryByText('Alice Johnson')).not.toBeInTheDocument()
    fireEvent.click(screen.getByText('Bob Martin'))

    expect(onResolvedChange).toHaveBeenLastCalledWith({
      userId: '22222222-2222-2222-2222-222222222222',
      label: 'Bob Martin',
    })
  })

  it('resolves a pasted UUID as the target', () => {
    const { input, onResolvedChange } = renderCombobox()
    const uuid = '33333333-3333-3333-3333-333333333333'
    fireEvent.change(input, { target: { value: uuid } })
    expect(onResolvedChange).toHaveBeenLastCalledWith({ userId: uuid, label: uuid })
  })

  it('leaves the target unresolved for free text', () => {
    const { input, onResolvedChange } = renderCombobox()
    fireEvent.change(input, { target: { value: 'not-a-user' } })
    expect(onResolvedChange).toHaveBeenLastCalledWith(null)
  })

  it('hides excluded users from suggestions', () => {
    const { input } = renderCombobox({
      excludeIds: new Set(['22222222-2222-2222-2222-222222222222']),
    })
    fireEvent.focus(input)
    expect(screen.getByText('Alice Johnson')).toBeInTheDocument()
    expect(screen.queryByText('Bob Martin')).not.toBeInTheDocument()
  })
})
