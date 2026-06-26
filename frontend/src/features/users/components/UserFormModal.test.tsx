import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import UserFormModal from './UserFormModal'
import { BYTES_PER_GB, UNIT_BYTES } from '../userQuota'
import type { UserDTO } from '../usersApi'

const EDIT_USER: UserDTO = {
  id: 'u-1',
  fullName: 'Ada Lovelace',
  email: 'ada@example.com',
  organization: 'Acme',
  department: 'R&D',
  role: 'ROLE_staff',
  storageQuota: 5 * BYTES_PER_GB,
  storageUsed: 0,
}

function renderModal(props: Partial<React.ComponentProps<typeof UserFormModal>> = {}) {
  const onCreate = vi.fn()
  const onUpdate = vi.fn()
  const onClose = vi.fn()
  render(
    <UserFormModal
      defaultOrganization="Acme"
      isSaving={false}
      saveError={null}
      onClose={onClose}
      onCreate={onCreate}
      onUpdate={onUpdate}
      {...props}
    />,
  )
  return { onCreate, onUpdate, onClose }
}

describe('UserFormModal — create mode', () => {
  it('blocks submission and flags every empty/unset field', async () => {
    const { onCreate } = renderModal()

    await userEvent.click(screen.getByRole('button', { name: 'Create user' }))

    expect(onCreate).not.toHaveBeenCalled()
    expect(screen.getByText('Username is required.')).toBeInTheDocument()
    expect(screen.getByText('Email is required.')).toBeInTheDocument()
    // A blank quota is not allowed at creation.
    expect(screen.getByText('Storage quota is required.')).toBeInTheDocument()
  })

  it('rejects a username with spaces and a malformed email', async () => {
    const { onCreate } = renderModal()

    await userEvent.type(screen.getByLabelText('Username'), 'grace lovelace')
    await userEvent.type(screen.getByLabelText('Email'), 'not-an-email')
    await userEvent.type(screen.getByLabelText('Storage quota'), '50')
    await userEvent.click(screen.getByRole('button', { name: 'Create user' }))

    expect(onCreate).not.toHaveBeenCalled()
    expect(screen.getByText('Username cannot contain spaces.')).toBeInTheDocument()
    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument()
  })

  it('submits a payload with the quota converted from the chosen unit', async () => {
    const { onCreate } = renderModal()

    await userEvent.type(screen.getByLabelText('Username'), 'grace')
    await userEvent.type(screen.getByLabelText('Email'), 'grace@example.com')
    await userEvent.selectOptions(screen.getByLabelText('Role'), 'ROLE_manager')
    await userEvent.type(screen.getByLabelText('Storage quota'), '2')
    await userEvent.selectOptions(screen.getByLabelText('Storage quota unit'), 'TB')
    await userEvent.click(screen.getByRole('button', { name: 'Create user' }))

    expect(onCreate).toHaveBeenCalledWith({
      username: 'grace',
      email: 'grace@example.com',
      organization: 'Acme',
      role: 'ROLE_manager',
      department: null,
      storageQuota: 2 * UNIT_BYTES.TB,
    })
  })
})

describe('UserFormModal — edit mode', () => {
  it('updates only role and quota while preserving identity fields', async () => {
    const { onUpdate } = renderModal({ user: EDIT_USER })

    await userEvent.selectOptions(screen.getByLabelText('Role'), 'ROLE_admin')
    const quota = screen.getByLabelText('Storage quota')
    await userEvent.clear(quota)
    await userEvent.type(quota, '10')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(onUpdate).toHaveBeenCalledWith({
      ...EDIT_USER,
      role: 'ROLE_admin',
      storageQuota: 10 * BYTES_PER_GB,
    })
  })

  it('treats a blank quota as unlimited (null)', async () => {
    const { onUpdate } = renderModal({ user: EDIT_USER })

    await userEvent.clear(screen.getByLabelText('Storage quota'))
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(onUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ storageQuota: null }),
    )
  })
})
