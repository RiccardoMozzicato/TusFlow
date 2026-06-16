import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import DocumentActionsMenu from './DocumentActionsMenu'

function renderMenu() {
  const onShare = vi.fn()
  const onNewVersion = vi.fn()
  const onDelete = vi.fn()
  render(
    <DocumentActionsMenu onShare={onShare} onNewVersion={onNewVersion} onDelete={onDelete} />,
  )
  return { onShare, onNewVersion, onDelete }
}

describe('DocumentActionsMenu', () => {
  it('is closed initially and opens on trigger click', async () => {
    renderMenu()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /more actions/i }))
    expect(screen.getByRole('menu')).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /share/i })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /new version/i })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: /delete/i })).toBeInTheDocument()
  })

  it('fires the matching callback and closes when an item is chosen', async () => {
    const { onShare, onNewVersion, onDelete } = renderMenu()
    await userEvent.click(screen.getByRole('button', { name: /more actions/i }))
    await userEvent.click(screen.getByRole('menuitem', { name: /new version/i }))
    expect(onNewVersion).toHaveBeenCalledOnce()
    expect(onShare).not.toHaveBeenCalled()
    expect(onDelete).not.toHaveBeenCalled()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('closes when clicking outside', async () => {
    renderMenu()
    await userEvent.click(screen.getByRole('button', { name: /more actions/i }))
    expect(screen.getByRole('menu')).toBeInTheDocument()
    await userEvent.click(document.body)
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('closes on Escape', async () => {
    renderMenu()
    await userEvent.click(screen.getByRole('button', { name: /more actions/i }))
    expect(screen.getByRole('menu')).toBeInTheDocument()
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
})
